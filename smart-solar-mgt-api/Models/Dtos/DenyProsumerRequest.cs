// DenyProsumerRequest.cs
// Purpose: Request body for PATCH /api/v1/prosumers/{nic}/deny - an optional reason shown back
// to the prosumer (see ProsumerService.DenyAsync).

namespace smart_solar_mgt_api.Models.Dtos;

public record DenyProsumerRequest(string? Reason);
