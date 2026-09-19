// IReservationService.cs
// Purpose: Abstraction over energy slot reservation creation, search/pagination, rescheduling,
// and cancellation (project-specification.md section 3.4). Implemented by ReservationService.

using smart_solar_mgt_api.Models.Entities;

namespace smart_solar_mgt_api.Services.Reservations;

public interface IReservationService
{
    Task<(CreateReservationResult Result, EnrichedReservation? Reservation)> CreateAsync(
        string prosumerNic,
        string nodeId,
        string slotId,
        DateTime startTime,
        DateTime endTime,
        string performedByUserId,
        CancellationToken cancellationToken = default);

    Task<EnrichedReservation?> GetByIdAsync(string id, CancellationToken cancellationToken = default);

    Task<(IReadOnlyList<EnrichedReservation> Items, long TotalCount)> ListAsync(
        string? prosumerNic,
        string? nodeId,
        Models.Enums.ReservationStatus? status,
        DateTime? dateFrom,
        DateTime? dateTo,
        int page,
        int pageSize,
        string? sortBy,
        string? sortDir,
        CancellationToken cancellationToken = default);

    Task<ReservationActionResult> UpdateAsync(
        string id,
        DateTime startTime,
        DateTime endTime,
        string performedByUserId,
        CancellationToken cancellationToken = default);

    Task<ReservationActionResult> CancelAsync(
        string id,
        string performedByUserId,
        CancellationToken cancellationToken = default);
}

// a reservation paired with its resolved node name / prosumer full name (see
// ReservationResponse.FromEntity); the names are computed at read time only, never stored on the
// Reservation entity itself
public record EnrichedReservation(Reservation Reservation, string? NodeName, string? ProsumerFullName);

public enum CreateReservationResult
{
    Succeeded,
    ProsumerNotFound,
    ProsumerDeactivated,
    NodeNotFound,
    NodeDeactivated,
    SlotNotFound,
    SlotNotAvailable
}

public enum ReservationActionResult
{
    Succeeded,
    NotFound,
    AlreadyCancelled,
    AlreadyStarted,
    InsufficientNotice
}
