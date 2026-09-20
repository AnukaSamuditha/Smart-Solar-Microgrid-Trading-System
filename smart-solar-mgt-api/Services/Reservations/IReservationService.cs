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

    // prosumer self-service: create a Pending reservation request for the calling prosumer;
    // never touches the node's cached battery-slot status (that only happens on ApproveAsync)
    Task<(CreateReservationResult Result, EnrichedReservation? Reservation)> RequestAsync(
        string prosumerNic,
        string nodeId,
        string slotId,
        DateTime startTime,
        DateTime endTime,
        double? energyAmount,
        CancellationToken cancellationToken = default);

    // prosumer self-service: fetch a reservation only if it belongs to the given prosumer
    Task<EnrichedReservation?> GetByIdForProsumerAsync(
        string id,
        string prosumerNic,
        CancellationToken cancellationToken = default);

    // prosumer self-service: reschedule own reservation, same 12-hour notice rule as staff UpdateAsync
    Task<ReservationActionResult> UpdateForProsumerAsync(
        string id,
        string prosumerNic,
        DateTime startTime,
        DateTime endTime,
        CancellationToken cancellationToken = default);

    // prosumer self-service: cancel own reservation, same 12-hour notice rule as staff CancelAsync
    Task<ReservationActionResult> CancelForProsumerAsync(
        string id,
        string prosumerNic,
        CancellationToken cancellationToken = default);

    // staff review: approve a Pending request, re-checking the slot is still free before confirming
    Task<ReservationReviewResult> ApproveAsync(
        string id,
        string performedByUserId,
        CancellationToken cancellationToken = default);

    // staff review: reject a Pending request; the slot was never marked Reserved, so nothing to release
    Task<ReservationReviewResult> RejectAsync(
        string id,
        string performedByUserId,
        string? reason,
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

public enum ReservationReviewResult
{
    Succeeded,
    NotFound,
    NotPending,
    SlotNoLongerAvailable
}
