// ProsumerEndpoints.cs
// Purpose: Maps prosumer-management endpoints. Create/list/update/deactivate are available to
// both Backoffice and Grid Operator staff (the "ProsumerManagement" policy); reactivating a
// deactivated profile is Backoffice-only, per project-specification.md section 3.2. See
// docs/prosumer-management-implementation-plan.md.

using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text.RegularExpressions;
using smart_solar_mgt_api.Authorization;
using smart_solar_mgt_api.Models.Dtos;
using smart_solar_mgt_api.Models.Enums;
using smart_solar_mgt_api.Services.Prosumers;

namespace smart_solar_mgt_api.Endpoints;

public static class ProsumerEndpoints
{
    private const string ProsumerManagementPolicy = "ProsumerManagement";

    // old Sri Lankan NIC: 9 digits + V or X; new Sri Lankan NIC: 12 digits
    private static readonly Regex NicPattern = new("^([0-9]{9}[vVxX]|[0-9]{12})$", RegexOptions.Compiled);

    // register the /api/v1/prosumers endpoint group
    public static IEndpointRouteBuilder MapProsumerEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/v1/prosumers").WithTags("Prosumers");

        group.MapPost("/", CreateProsumerAsync).RequireAuthorization(ProsumerManagementPolicy);
        group.MapPost("/register", RegisterProsumerAsync); // public - self-registration from the mobile app
        group.MapGet("/me", GetMyProsumerProfileAsync).RequireAuthorization(RoleNames.Prosumer);
        group.MapGet("/", ListProsumersAsync).RequireAuthorization(ProsumerManagementPolicy);
        group.MapPut("/{nic}", UpdateProsumerAsync).RequireAuthorization(ProsumerManagementPolicy);
        group.MapPatch("/{nic}/approve", ApproveProsumerAsync).RequireAuthorization(ProsumerManagementPolicy);
        group.MapPatch("/{nic}/deny", DenyProsumerAsync).RequireAuthorization(ProsumerManagementPolicy);
        group.MapPatch("/{nic}/deactivate", DeactivateProsumerAsync).RequireAuthorization(ProsumerManagementPolicy);
        group.MapPatch("/{nic}/reactivate", ReactivateProsumerAsync).RequireAuthorization(RoleNames.Backoffice);

        return app;
    }

    // self-registration from the mobile app: creates a PendingApproval profile with no password,
    // awaiting a Backoffice/Grid Operator reviewer (see ApproveProsumerAsync/DenyProsumerAsync)
    private static async Task<IResult> RegisterProsumerAsync(
        RegisterProsumerRequest request,
        IProsumerService prosumerService,
        CancellationToken cancellationToken)
    {
        var validationError = ValidateCreateRequest(new CreateProsumerRequest(request.Nic, request.Email, request.FullName));
        if (validationError is not null)
        {
            return Results.BadRequest(new { error = validationError });
        }

        var result = await prosumerService.RegisterAsync(
            request.Nic, request.Email, request.FullName, request.Phone, request.Address, cancellationToken);

        if (result != ProsumerCreateResult.Succeeded)
        {
            return result switch
            {
                ProsumerCreateResult.NicConflict => Results.Conflict(new { error = "NicAlreadyInUse" }),
                ProsumerCreateResult.EmailConflict => Results.Conflict(new { error = "EmailAlreadyInUse" }),
                _ => Results.Problem()
            };
        }

        var prosumer = await prosumerService.GetByNicAsync(request.Nic, cancellationToken);
        return Results.Created($"/api/v1/prosumers/{prosumer!.Nic}", ProsumerResponse.FromEntity(prosumer));
    }

    // a logged-in prosumer's own profile - the mobile app's only read of prosumer profile data
    // (name/email/phone/address), since it never has direct access to the full list endpoint
    // (that's Backoffice/Grid Operator only). NIC comes from the JWT's sub claim
    // (JwtTokenService.GenerateAccessToken(Prosumer)), never a route/query parameter, so a
    // prosumer can only ever fetch their own record.
    private static async Task<IResult> GetMyProsumerProfileAsync(
        ClaimsPrincipal principal,
        IProsumerService prosumerService,
        CancellationToken cancellationToken)
    {
        var nic = principal.FindFirstValue(JwtRegisteredClaimNames.Sub);
        if (string.IsNullOrWhiteSpace(nic))
        {
            return Results.Unauthorized();
        }

        var prosumer = await prosumerService.GetByNicAsync(nic, cancellationToken);
        return prosumer is null ? Results.NotFound() : Results.Ok(ProsumerResponse.FromEntity(prosumer));
    }

    // approve a PendingApproval self-registration request (Backoffice or Grid Operator)
    private static async Task<IResult> ApproveProsumerAsync(
        string nic,
        ClaimsPrincipal principal,
        IProsumerService prosumerService,
        CancellationToken cancellationToken)
    {
        var performedBy = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var result = await prosumerService.ApproveAsync(nic, performedBy, cancellationToken);
        return MapReviewResult(result);
    }

    // deny a PendingApproval self-registration request, optionally with a reason (Backoffice or Grid Operator)
    private static async Task<IResult> DenyProsumerAsync(
        string nic,
        DenyProsumerRequest? request,
        ClaimsPrincipal principal,
        IProsumerService prosumerService,
        CancellationToken cancellationToken)
    {
        var performedBy = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var result = await prosumerService.DenyAsync(nic, performedBy, request?.Reason, cancellationToken);
        return MapReviewResult(result);
    }

    // translate a ProsumerReviewResult into the matching HTTP response
    private static IResult MapReviewResult(ProsumerReviewResult result) => result switch
    {
        ProsumerReviewResult.Succeeded => Results.NoContent(),
        ProsumerReviewResult.NotFound => Results.NotFound(),
        ProsumerReviewResult.NotPendingApproval => Results.Conflict(new { error = "NotPendingApproval" }),
        _ => Results.Problem()
    };

    // create a new prosumer profile from a NIC, email, and optional full name; sends a setup
    // invitation email rather than accepting a password directly (see ProsumerService.CreateAsync)
    private static async Task<IResult> CreateProsumerAsync(
        CreateProsumerRequest request,
        ClaimsPrincipal principal,
        IProsumerService prosumerService,
        CancellationToken cancellationToken)
    {
        var validationError = ValidateCreateRequest(request);
        if (validationError is not null)
        {
            return Results.BadRequest(new { error = validationError });
        }

        var createdByUserId = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var result = await prosumerService.CreateAsync(
            request.Nic, request.Email, request.FullName, createdByUserId, cancellationToken);

        if (result != ProsumerCreateResult.Succeeded)
        {
            return result switch
            {
                ProsumerCreateResult.NicConflict => Results.Conflict(new { error = "NicAlreadyInUse" }),
                ProsumerCreateResult.EmailConflict => Results.Conflict(new { error = "EmailAlreadyInUse" }),
                _ => Results.Problem()
            };
        }

        var prosumer = await prosumerService.GetByNicAsync(request.Nic, cancellationToken);
        return Results.Created($"/api/v1/prosumers/{prosumer!.Nic}", ProsumerResponse.FromEntity(prosumer));
    }

    // search/filter/sort/paginate prosumer profiles
    private static async Task<IResult> ListProsumersAsync(
        IProsumerService prosumerService,
        CancellationToken cancellationToken,
        string? search = null,
        string? status = null,
        int page = 1,
        int pageSize = 20,
        string? sortBy = null,
        string? sortDir = null)
    {
        ProsumerStatus? parsedStatus = null;
        if (!string.IsNullOrWhiteSpace(status))
        {
            if (!Enum.TryParse<ProsumerStatus>(status, ignoreCase: true, out var statusValue))
            {
                return Results.BadRequest(new { error = "InvalidStatusFilter" });
            }

            parsedStatus = statusValue;
        }

        var (items, totalCount) = await prosumerService.ListAsync(
            search, parsedStatus, page, pageSize, sortBy, sortDir, cancellationToken);

        var response = new PagedResult<ProsumerResponse>(
            items.Select(ProsumerResponse.FromEntity).ToList(), totalCount, Math.Max(page, 1), pageSize);
        return Results.Ok(response);
    }

    // update a prosumer's email/full name; NIC is immutable and blocked if the new email is already in use
    private static async Task<IResult> UpdateProsumerAsync(
        string nic,
        UpdateProsumerRequest request,
        ClaimsPrincipal principal,
        IProsumerService prosumerService,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Email) || !request.Email.Contains('@'))
        {
            return Results.BadRequest(new { error = "ValidEmailRequired" });
        }

        var performedBy = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var result = await prosumerService.UpdateAsync(nic, request.Email, request.FullName, performedBy, cancellationToken);
        if (result != ProsumerActionResult.Succeeded)
        {
            return MapActionResult(result);
        }

        var prosumer = await prosumerService.GetByNicAsync(nic, cancellationToken);
        return prosumer is null ? Results.NotFound() : Results.Ok(ProsumerResponse.FromEntity(prosumer));
    }

    // deactivate a prosumer profile (Backoffice or Grid Operator)
    private static async Task<IResult> DeactivateProsumerAsync(
        string nic,
        ClaimsPrincipal principal,
        IProsumerService prosumerService,
        CancellationToken cancellationToken)
    {
        var performedBy = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var result = await prosumerService.DeactivateAsync(nic, performedBy, cancellationToken);
        return MapActionResult(result);
    }

    // reactivate a previously deactivated prosumer profile (Backoffice-only, enforced by the route's policy)
    private static async Task<IResult> ReactivateProsumerAsync(
        string nic,
        ClaimsPrincipal principal,
        IProsumerService prosumerService,
        CancellationToken cancellationToken)
    {
        var performedBy = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var result = await prosumerService.ReactivateAsync(nic, performedBy, cancellationToken);
        return MapActionResult(result);
    }

    // validate the create-prosumer request body: NIC format and email format
    private static string? ValidateCreateRequest(CreateProsumerRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Nic) || !NicPattern.IsMatch(request.Nic.Trim()))
        {
            return "InvalidNic";
        }

        if (string.IsNullOrWhiteSpace(request.Email) || !request.Email.Contains('@'))
        {
            return "ValidEmailRequired";
        }

        return null;
    }

    // translate a ProsumerActionResult into the matching HTTP response
    private static IResult MapActionResult(ProsumerActionResult result) => result switch
    {
        ProsumerActionResult.Succeeded => Results.NoContent(),
        ProsumerActionResult.NotFound => Results.NotFound(),
        ProsumerActionResult.EmailConflict => Results.Conflict(new { error = "EmailAlreadyInUse" }),
        _ => Results.Problem()
    };
}
