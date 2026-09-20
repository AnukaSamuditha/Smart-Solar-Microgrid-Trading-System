// RejectReservationRequest.cs
// Purpose: Request body for PATCH /api/v1/reservations/{id}/reject - an optional reason shown
// back to the prosumer (see ReservationService.RejectAsync). Mirrors DenyProsumerRequest.

namespace smart_solar_mgt_api.Models.Dtos;

public record RejectReservationRequest(string? Reason);
