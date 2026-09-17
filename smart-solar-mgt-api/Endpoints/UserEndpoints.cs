// UserEndpoints.cs
// Purpose: Maps user-management endpoints — self-profile for any authenticated user, and
// Backoffice-only account creation/listing/lifecycle administration. Grid Operators have no
// authorized route here beyond /me. See docs/authentication-implementation-approach.md,
// section 12.

using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using smart_solar_mgt_api.Authorization;
using smart_solar_mgt_api.Models.Dtos;
using smart_solar_mgt_api.Models.Enums;
using smart_solar_mgt_api.Services.Users;

namespace smart_solar_mgt_api.Endpoints;

public static class UserEndpoints
{
    // register the /api/v1/users endpoint group
    public static IEndpointRouteBuilder MapUserEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/v1/users").WithTags("Users");

        group.MapGet("/me", GetMeAsync).RequireAuthorization();

        group.MapPost("/backoffice", CreateBackofficeUserAsync).RequireAuthorization(RoleNames.Backoffice);
        group.MapPost("/grid-operators", CreateGridOperatorUserAsync).RequireAuthorization(RoleNames.Backoffice);
        group.MapGet("/", ListUsersAsync).RequireAuthorization(RoleNames.Backoffice);
        group.MapPatch("/{id}/deactivate", DeactivateUserAsync).RequireAuthorization(RoleNames.Backoffice);
        group.MapPatch("/{id}/reactivate", ReactivateUserAsync).RequireAuthorization(RoleNames.Backoffice);

        return app;
    }

    // return the profile of the currently authenticated user
    private static async Task<IResult> GetMeAsync(
        ClaimsPrincipal principal,
        IUserService userService,
        CancellationToken cancellationToken)
    {
        var userId = principal.FindFirstValue(JwtRegisteredClaimNames.Sub);
        if (userId is null)
        {
            return Results.Unauthorized();
        }

        var user = await userService.GetByIdAsync(userId, cancellationToken);
        return user is null ? Results.NotFound() : Results.Ok(UserResponse.FromEntity(user));
    }

    // create a new Backoffice account and send it a setup invitation
    private static async Task<IResult> CreateBackofficeUserAsync(
        CreateUserRequest request,
        ClaimsPrincipal principal,
        IUserService userService,
        CancellationToken cancellationToken) =>
        await CreateUserAsync(request, UserRole.Backoffice, principal, userService, cancellationToken);

    // create a new Grid Operator account and send it a setup invitation
    private static async Task<IResult> CreateGridOperatorUserAsync(
        CreateUserRequest request,
        ClaimsPrincipal principal,
        IUserService userService,
        CancellationToken cancellationToken) =>
        await CreateUserAsync(request, UserRole.GridOperator, principal, userService, cancellationToken);

    // shared account-creation logic used by both the Backoffice and Grid Operator creation endpoints
    private static async Task<IResult> CreateUserAsync(
        CreateUserRequest request,
        UserRole role,
        ClaimsPrincipal principal,
        IUserService userService,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Email) || !request.Email.Contains('@'))
        {
            return Results.BadRequest(new { error = "ValidEmailRequired" });
        }

        var createdByUserId = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var user = await userService.CreateInvitedUserAsync(request.Email, request.Username, role, createdByUserId, cancellationToken);
        return Results.Created($"/api/v1/users/{user.Id}", UserResponse.FromEntity(user));
    }

    // list every Backoffice and Grid Operator account
    private static async Task<IResult> ListUsersAsync(IUserService userService, CancellationToken cancellationToken)
    {
        var users = await userService.ListAsync(cancellationToken);
        return Results.Ok(users.Select(UserResponse.FromEntity));
    }

    // deactivate a user account; blocked for the seeded super-admin account
    private static async Task<IResult> DeactivateUserAsync(
        string id,
        ClaimsPrincipal principal,
        IUserService userService,
        CancellationToken cancellationToken)
    {
        var performedBy = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var result = await userService.DeactivateAsync(id, performedBy, cancellationToken);
        return MapActionResult(result);
    }

    // reactivate a previously deactivated user account
    private static async Task<IResult> ReactivateUserAsync(
        string id,
        ClaimsPrincipal principal,
        IUserService userService,
        CancellationToken cancellationToken)
    {
        var performedBy = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var result = await userService.ReactivateAsync(id, performedBy, cancellationToken);
        return MapActionResult(result);
    }

    // translate a UserActionResult into the matching HTTP response
    private static IResult MapActionResult(UserActionResult result) => result switch
    {
        UserActionResult.Succeeded => Results.NoContent(),
        UserActionResult.NotFound => Results.NotFound(),
        UserActionResult.Protected => Results.Conflict(new { error = "SeededAdminProtected" }),
        _ => Results.Problem()
    };
}
