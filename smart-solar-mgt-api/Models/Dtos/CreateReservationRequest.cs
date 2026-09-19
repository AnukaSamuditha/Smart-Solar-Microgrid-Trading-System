// CreateReservationRequest.cs
// Purpose: Request body for creating an energy slot reservation. StartTime/EndTime are UTC
// instants (ISO 8601 on the wire); System.Text.Json parses them into DateTimeKind.Utc.

namespace smart_solar_mgt_api.Models.Dtos;

public record CreateReservationRequest(
    string ProsumerNic,
    string NodeId,
    string SlotId,
    DateTime StartTime,
    DateTime EndTime);
