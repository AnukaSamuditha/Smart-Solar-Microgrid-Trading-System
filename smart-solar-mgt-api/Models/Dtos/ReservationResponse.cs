// ReservationResponse.cs
// Purpose: Public-facing projection of a Reservation document, enriched with the referenced
// node's name and prosumer's full name for display. Unlike ProsumerResponse/MicrogridNodeResponse,
// FromEntity takes the enriched values as extra parameters rather than projecting the entity
// alone, since those values come from separate lookups (see ReservationService.ListAsync/
// GetByIdAsync) and are deliberately never stored on the Reservation entity itself, so a later
// node rename or prosumer profile edit can't leave stale names on old reservations.

using smart_solar_mgt_api.Models.Entities;

namespace smart_solar_mgt_api.Models.Dtos;

public record ReservationResponse(
    string Id,
    string ProsumerNic,
    string? ProsumerFullName,
    string NodeId,
    string? NodeName,
    string SlotId,
    DateTime StartTime,
    DateTime EndTime,
    string Status,
    DateTime CreatedAt,
    DateTime? UpdatedAt)
{
    // project a Reservation entity plus its resolved node name / prosumer full name onto the public response shape
    public static ReservationResponse FromEntity(Reservation reservation, string? nodeName, string? prosumerFullName) =>
        new(
            reservation.Id,
            reservation.ProsumerNic,
            prosumerFullName,
            reservation.NodeId,
            nodeName,
            reservation.SlotId,
            reservation.StartTime,
            reservation.EndTime,
            reservation.Status.ToString(),
            reservation.CreatedAt,
            reservation.UpdatedAt);
}
