// RequestReservationRequest.cs
// Purpose: Request body for POST /api/v1/reservations/mine - a prosumer's self-service
// reservation request. Deliberately has no ProsumerNic field: the caller's identity comes from
// the JWT `sub` claim (see ReservationEndpoints.RequestReservationAsync), never from the request
// body, so one prosumer can never request a reservation on another's behalf.

namespace smart_solar_mgt_api.Models.Dtos;

public record RequestReservationRequest(
    string NodeId,
    string SlotId,
    DateTime StartTime,
    DateTime EndTime,
    double? EnergyAmount);
