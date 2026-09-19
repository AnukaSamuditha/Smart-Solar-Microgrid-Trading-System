// IMicrogridNodeService.cs
// Purpose: Abstraction over microgrid node (solar grid hub) creation, search/pagination,
// schedule updates, battery-slot status updates, and lifecycle management
// (deactivate/reactivate). Implemented by MicrogridNodeService.

using smart_solar_mgt_api.Models.Entities;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Services.Nodes;

public interface IMicrogridNodeService
{
    Task<MicrogridNode> CreateAsync(
        string name,
        double latitude,
        double longitude,
        double capacityKw,
        int batterySlotCount,
        TimeOnly operatingStartTime,
        TimeOnly operatingEndTime,
        string createdByUserId,
        CancellationToken cancellationToken = default);

    Task<MicrogridNode?> GetByIdAsync(string id, CancellationToken cancellationToken = default);

    Task<(IReadOnlyList<MicrogridNode> Items, long TotalCount)> ListAsync(
        string? search,
        MicrogridNodeStatus? status,
        int page,
        int pageSize,
        string? sortBy,
        string? sortDir,
        CancellationToken cancellationToken = default);

    Task<NodeActionResult> UpdateScheduleAsync(
        string id,
        IReadOnlyList<ScheduleEntry> schedule,
        string performedByUserId,
        CancellationToken cancellationToken = default);

    Task<NodeActionResult> UpdateBatterySlotStatusAsync(
        string id,
        string slotId,
        BatterySlotStatus status,
        string performedByUserId,
        CancellationToken cancellationToken = default);

    Task<NodeActionResult> DeactivateAsync(string id, string performedByUserId, CancellationToken cancellationToken = default);

    Task<NodeActionResult> ReactivateAsync(string id, string performedByUserId, CancellationToken cancellationToken = default);
}

public enum NodeActionResult
{
    Succeeded,
    NotFound,
    ActiveReservationsExist,
    SlotNotFound
}
