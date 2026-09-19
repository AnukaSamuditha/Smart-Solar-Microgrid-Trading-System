// MicrogridNodeEndpoints.cs
// Purpose: Maps microgrid node (solar grid hub) management endpoints. Registration, schedule
// updates, deactivation, and reactivation are Backoffice-only; listing/reading and battery-slot
// status updates are available to both Backoffice and Grid Operator staff (the
// "NodeBatterySlotManagement" policy), per project-specification.md sections 3.3 and 6. See
// docs/microgrid-node-management-implementation-plan.md.

using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using smart_solar_mgt_api.Authorization;
using smart_solar_mgt_api.Models.Dtos;
using smart_solar_mgt_api.Models.Entities;
using smart_solar_mgt_api.Models.Enums;
using smart_solar_mgt_api.Services.Nodes;

namespace smart_solar_mgt_api.Endpoints;

public static class MicrogridNodeEndpoints
{
    private const string NodeBatterySlotManagementPolicy = "NodeBatterySlotManagement";

    // register the /api/v1/nodes endpoint group
    public static IEndpointRouteBuilder MapMicrogridNodeEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/v1/nodes").WithTags("MicrogridNodes");

        group.MapPost("/", CreateNodeAsync).RequireAuthorization(RoleNames.Backoffice);
        group.MapGet("/", ListNodesAsync).RequireAuthorization(NodeBatterySlotManagementPolicy);
        group.MapGet("/{id}", GetNodeAsync).RequireAuthorization(NodeBatterySlotManagementPolicy);
        group.MapPatch("/{id}/schedule", UpdateScheduleAsync).RequireAuthorization(RoleNames.Backoffice);
        group.MapPatch("/{id}/battery-slots/{slotId}", UpdateBatterySlotStatusAsync).RequireAuthorization(NodeBatterySlotManagementPolicy);
        group.MapPatch("/{id}/deactivate", DeactivateNodeAsync).RequireAuthorization(RoleNames.Backoffice);
        group.MapPatch("/{id}/reactivate", ReactivateNodeAsync).RequireAuthorization(RoleNames.Backoffice);

        return app;
    }

    // create a new microgrid node with GPS location, capacity, and a generated battery slot list
    private static async Task<IResult> CreateNodeAsync(
        CreateMicrogridNodeRequest request,
        ClaimsPrincipal principal,
        IMicrogridNodeService nodeService,
        CancellationToken cancellationToken)
    {
        var validationError = ValidateCreateRequest(request);
        if (validationError is not null)
        {
            return Results.BadRequest(new { error = validationError });
        }

        var createdByUserId = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var node = await nodeService.CreateAsync(
            request.Name, request.Latitude, request.Longitude, request.CapacityKw, request.BatterySlotCount,
            request.OperatingStartTime, request.OperatingEndTime, createdByUserId, cancellationToken);

        return Results.Created($"/api/v1/nodes/{node.Id}", MicrogridNodeResponse.FromEntity(node));
    }

    // search/filter/sort/paginate microgrid nodes
    private static async Task<IResult> ListNodesAsync(
        IMicrogridNodeService nodeService,
        CancellationToken cancellationToken,
        string? search = null,
        string? status = null,
        int page = 1,
        int pageSize = 20,
        string? sortBy = null,
        string? sortDir = null)
    {
        MicrogridNodeStatus? parsedStatus = null;
        if (!string.IsNullOrWhiteSpace(status))
        {
            if (!Enum.TryParse<MicrogridNodeStatus>(status, ignoreCase: true, out var statusValue))
            {
                return Results.BadRequest(new { error = "InvalidStatusFilter" });
            }

            parsedStatus = statusValue;
        }

        var (items, totalCount) = await nodeService.ListAsync(
            search, parsedStatus, page, pageSize, sortBy, sortDir, cancellationToken);

        var response = new PagedResult<MicrogridNodeResponse>(
            items.Select(MicrogridNodeResponse.FromEntity).ToList(), totalCount, Math.Max(page, 1), pageSize);
        return Results.Ok(response);
    }

    // fetch a single microgrid node by id
    private static async Task<IResult> GetNodeAsync(
        string id,
        IMicrogridNodeService nodeService,
        CancellationToken cancellationToken)
    {
        var node = await nodeService.GetByIdAsync(id, cancellationToken);
        return node is null ? Results.NotFound() : Results.Ok(MicrogridNodeResponse.FromEntity(node));
    }

    // replace a node's weekly operating-hours schedule
    private static async Task<IResult> UpdateScheduleAsync(
        string id,
        UpdateMicrogridNodeScheduleRequest request,
        ClaimsPrincipal principal,
        IMicrogridNodeService nodeService,
        CancellationToken cancellationToken)
    {
        var validationError = ValidateSchedule(request.Schedule);
        if (validationError is not null)
        {
            return Results.BadRequest(new { error = validationError });
        }

        var performedBy = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var schedule = request.Schedule
            .Select(e => new ScheduleEntry { DayOfWeek = e.DayOfWeek, OpenTime = e.OpenTime, CloseTime = e.CloseTime, IsClosed = e.IsClosed })
            .ToList();
        var result = await nodeService.UpdateScheduleAsync(id, schedule, performedBy, cancellationToken);
        if (result != NodeActionResult.Succeeded)
        {
            return MapActionResult(result);
        }

        var node = await nodeService.GetByIdAsync(id, cancellationToken);
        return node is null ? Results.NotFound() : Results.Ok(MicrogridNodeResponse.FromEntity(node));
    }

    // update a single battery slot's status (Backoffice or Grid Operator)
    private static async Task<IResult> UpdateBatterySlotStatusAsync(
        string id,
        string slotId,
        UpdateBatterySlotStatusRequest request,
        ClaimsPrincipal principal,
        IMicrogridNodeService nodeService,
        CancellationToken cancellationToken)
    {
        var performedBy = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var result = await nodeService.UpdateBatterySlotStatusAsync(id, slotId, request.Status, performedBy, cancellationToken);
        if (result != NodeActionResult.Succeeded)
        {
            return MapActionResult(result);
        }

        var node = await nodeService.GetByIdAsync(id, cancellationToken);
        return node is null ? Results.NotFound() : Results.Ok(MicrogridNodeResponse.FromEntity(node));
    }

    // deactivate a node; blocked (409) while active energy reservations exist against it
    private static async Task<IResult> DeactivateNodeAsync(
        string id,
        ClaimsPrincipal principal,
        IMicrogridNodeService nodeService,
        CancellationToken cancellationToken)
    {
        var performedBy = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var result = await nodeService.DeactivateAsync(id, performedBy, cancellationToken);
        return MapActionResult(result);
    }

    // reactivate a previously deactivated node (Backoffice-only, enforced by the route's policy)
    private static async Task<IResult> ReactivateNodeAsync(
        string id,
        ClaimsPrincipal principal,
        IMicrogridNodeService nodeService,
        CancellationToken cancellationToken)
    {
        var performedBy = principal.FindFirstValue(JwtRegisteredClaimNames.Sub) ?? "unknown";
        var result = await nodeService.ReactivateAsync(id, performedBy, cancellationToken);
        return MapActionResult(result);
    }

    // validate the create-node request body: name, GPS range, capacity, and battery slot count
    private static string? ValidateCreateRequest(CreateMicrogridNodeRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Name))
        {
            return "NameRequired";
        }

        if (request.Latitude is < -90 or > 90)
        {
            return "InvalidLatitude";
        }

        if (request.Longitude is < -180 or > 180)
        {
            return "InvalidLongitude";
        }

        if (request.CapacityKw <= 0)
        {
            return "CapacityMustBePositive";
        }

        if (request.BatterySlotCount < 0)
        {
            return "BatterySlotCountMustNotBeNegative";
        }

        if (request.OperatingStartTime >= request.OperatingEndTime)
        {
            return "OperatingStartTimeMustBeBeforeEndTime";
        }

        return null;
    }

    // validate a schedule update: every non-closed day needs an open time strictly before its close time
    private static string? ValidateSchedule(List<ScheduleEntryDto> schedule)
    {
        foreach (var entry in schedule)
        {
            if (entry.IsClosed)
            {
                continue;
            }

            if (entry.OpenTime is null || entry.CloseTime is null)
            {
                return "OpenAndCloseTimeRequiredWhenNotClosed";
            }

            if (entry.OpenTime >= entry.CloseTime)
            {
                return "OpenTimeMustBeBeforeCloseTime";
            }
        }

        return null;
    }

    // translate a NodeActionResult into the matching HTTP response
    private static IResult MapActionResult(NodeActionResult result) => result switch
    {
        NodeActionResult.Succeeded => Results.NoContent(),
        NodeActionResult.NotFound => Results.NotFound(),
        NodeActionResult.SlotNotFound => Results.NotFound(new { error = "SlotNotFound" }),
        NodeActionResult.ActiveReservationsExist => Results.Conflict(new { error = "ActiveReservationsExist" }),
        _ => Results.Problem()
    };
}
