// UpdateReservationRequest.cs
// Purpose: Request body for rescheduling an existing reservation. Only the time window can
// change — the node and slot stay fixed (see ReservationService.UpdateAsync); changing node/slot
// requires cancelling and creating a new reservation.

namespace smart_solar_mgt_api.Models.Dtos;

public record UpdateReservationRequest(DateTime StartTime, DateTime EndTime);
