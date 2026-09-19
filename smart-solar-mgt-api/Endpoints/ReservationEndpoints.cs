// ReservationEndpoints.cs
// Purpose: Maps energy slot reservation endpoints (project-specification.md section 3.4).
// Every action — create, list, get, reschedule, cancel — is available to both Backoffice and
// Grid Operator staff (the "ReservationManagement" policy). There is no prosumer authentication
// in this codebase yet, so creation via this API is inherently a staff-assisted booking (e.g.
// over the phone), not prosumer self-service — that stays a not-yet-built mobile-app concern.
// See docs/energy-slot-reservation-management-implementation-plan.md.

using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text.RegularExpressions;
using smart_solar_mgt_api.Models.Dtos;
using smart_solar_mgt_api.Models.Enums;
using smart_solar_mgt_api.Services.Reservations;

namespace smart_solar_mgt_api.Endpoints;

public static class ReservationEndpoints
{
    private const string ReservationManagementPolicy = "ReservationManagement";
    private const int MaxSchedulingWindowDays = 7;

    // old Sri Lankan NIC: 9 digits + V or X; new Sri Lankan NIC: 12 digits (matches Prosumer Management)
    private static readonly Regex NicPattern = new("^([0-9]{9}[vVxX]|[0-9]{12})$", RegexOptions.Compiled);

    // register the /api/v1/reservations endpoint group
    public static IEndpointRouteBuilder MapReservationEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/v1/reservations").WithTags("Reservations");

        group.MapPost("/", CreateReservationAsync).RequireAuthorization(ReservationManagementPolicy);
        group.MapGet("/", ListReservationsAsync).RequireAuthorization(ReservationManagementPolicy);
        group.MapGet("/{id}", GetReservationAsync).RequireAuthorization(ReservationManagementPolicy);
        group.MapPatch("/{id}", UpdateReservationAsync).RequireAuthorization(ReservationManagementPolicy);
        group.MapPatch("/{id}/cancel", CancelReservationAsync).RequireAuthorization(ReservationManagementPolicy);

        return app;
    }

    // create a reservation for a prosumer against a specific node's battery slot
    private static async Task<IResult> CreateReservationAsync(
        CreateReservationRequest request,
        ClaimsPrincipal principal,
        IReservationService reservationService,
        CancellationToken cancellationToken)
    {
        var validationError = ValidateCreateRequest(request);
        if (validationError is not null)
        {
            return Results.BadRequest(new { error = validationError });
        }

        var performedBy = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var (result, reservation) = await reservationService.CreateAsync(
            request.ProsumerNic, request.NodeId, request.SlotId, request.StartTime, request.EndTime,
            performedBy, cancellationToken);

        if (result != CreateReservationResult.Succeeded || reservation is null)
        {
            return MapCreateResult(result);
        }

        var response = ReservationResponse.FromEntity(reservation.Reservation, reservation.NodeName, reservation.ProsumerFullName);
        return Results.Created($"/api/v1/reservations/{reservation.Reservation.Id}", response);
    }

    // search/filter/sort/paginate reservations for the monitoring list
    private static async Task<IResult> ListReservationsAsync(
        IReservationService reservationService,
        CancellationToken cancellationToken,
        string? prosumerNic = null,
        string? nodeId = null,
        string? status = null,
        DateTime? dateFrom = null,
        DateTime? dateTo = null,
        int page = 1,
        int pageSize = 20,
        string? sortBy = null,
        string? sortDir = null)
    {
        ReservationStatus? parsedStatus = null;
        if (!string.IsNullOrWhiteSpace(status))
        {
            if (!Enum.TryParse<ReservationStatus>(status, ignoreCase: true, out var statusValue))
            {
                return Results.BadRequest(new { error = "InvalidStatusFilter" });
            }

            parsedStatus = statusValue;
        }

        var (items, totalCount) = await reservationService.ListAsync(
            prosumerNic, nodeId, parsedStatus, dateFrom, dateTo, page, pageSize, sortBy, sortDir, cancellationToken);

        var response = new PagedResult<ReservationResponse>(
            items.Select(r => ReservationResponse.FromEntity(r.Reservation, r.NodeName, r.ProsumerFullName)).ToList(),
            totalCount, Math.Max(page, 1), pageSize);
        return Results.Ok(response);
    }

    // fetch a single reservation by id
    private static async Task<IResult> GetReservationAsync(
        string id,
        IReservationService reservationService,
        CancellationToken cancellationToken)
    {
        var reservation = await reservationService.GetByIdAsync(id, cancellationToken);
        return reservation is null
            ? Results.NotFound()
            : Results.Ok(ReservationResponse.FromEntity(reservation.Reservation, reservation.NodeName, reservation.ProsumerFullName));
    }

    // reschedule a reservation's time window (same node/slot); requires at least 12 hours'
    // notice against the reservation's current start time
    private static async Task<IResult> UpdateReservationAsync(
        string id,
        UpdateReservationRequest request,
        ClaimsPrincipal principal,
        IReservationService reservationService,
        CancellationToken cancellationToken)
    {
        var validationError = ValidateReservationWindow(request.StartTime, request.EndTime);
        if (validationError is not null)
        {
            return Results.BadRequest(new { error = validationError });
        }

        var performedBy = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var result = await reservationService.UpdateAsync(id, request.StartTime, request.EndTime, performedBy, cancellationToken);
        if (result != ReservationActionResult.Succeeded)
        {
            return MapActionResult(result);
        }

        var reservation = await reservationService.GetByIdAsync(id, cancellationToken);
        return reservation is null
            ? Results.NotFound()
            : Results.Ok(ReservationResponse.FromEntity(reservation.Reservation, reservation.NodeName, reservation.ProsumerFullName));
    }

    // cancel a reservation; requires at least 12 hours' notice against its current start time
    private static async Task<IResult> CancelReservationAsync(
        string id,
        ClaimsPrincipal principal,
        IReservationService reservationService,
        CancellationToken cancellationToken)
    {
        var performedBy = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var result = await reservationService.CancelAsync(id, performedBy, cancellationToken);
        return MapActionResult(result);
    }

    // validate the create-reservation request body: NIC format, node/slot ids present, time window
    private static string? ValidateCreateRequest(CreateReservationRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.ProsumerNic) || !NicPattern.IsMatch(request.ProsumerNic.Trim()))
        {
            return "InvalidNic";
        }

        if (string.IsNullOrWhiteSpace(request.NodeId))
        {
            return "NodeIdRequired";
        }

        if (string.IsNullOrWhiteSpace(request.SlotId))
        {
            return "SlotIdRequired";
        }

        return ValidateReservationWindow(request.StartTime, request.EndTime);
    }

    // shared create/update validation: start before end, start in the future, start within 7 days
    // (spec section 3.4's "scheduled within 7 days" rule)
    private static string? ValidateReservationWindow(DateTime startTime, DateTime endTime)
    {
        if (startTime >= endTime)
        {
            return "StartTimeMustBeBeforeEndTime";
        }

        var utcNow = DateTime.UtcNow;
        if (startTime <= utcNow)
        {
            return "ReservationMustBeInFuture";
        }

        if (startTime > utcNow.AddDays(MaxSchedulingWindowDays))
        {
            return "ReservationMustBeWithinSevenDays";
        }

        return null;
    }

    // translate a CreateReservationResult into the matching HTTP response
    private static IResult MapCreateResult(CreateReservationResult result) => result switch
    {
        CreateReservationResult.ProsumerNotFound => Results.NotFound(new { error = "ProsumerNotFound" }),
        CreateReservationResult.ProsumerDeactivated => Results.Conflict(new { error = "ProsumerDeactivated" }),
        CreateReservationResult.NodeNotFound => Results.NotFound(new { error = "NodeNotFound" }),
        CreateReservationResult.NodeDeactivated => Results.Conflict(new { error = "NodeDeactivated" }),
        CreateReservationResult.SlotNotFound => Results.NotFound(new { error = "SlotNotFound" }),
        CreateReservationResult.SlotNotAvailable => Results.Conflict(new { error = "SlotNotAvailable" }),
        _ => Results.Problem()
    };

    // translate a ReservationActionResult into the matching HTTP response
    private static IResult MapActionResult(ReservationActionResult result) => result switch
    {
        ReservationActionResult.Succeeded => Results.NoContent(),
        ReservationActionResult.NotFound => Results.NotFound(),
        ReservationActionResult.AlreadyCancelled => Results.Conflict(new { error = "AlreadyCancelled" }),
        ReservationActionResult.AlreadyStarted => Results.Conflict(new { error = "AlreadyStarted" }),
        ReservationActionResult.InsufficientNotice => Results.Conflict(new { error = "InsufficientNotice" }),
        _ => Results.Problem()
    };
}
