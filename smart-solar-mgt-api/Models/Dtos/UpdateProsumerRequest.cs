// UpdateProsumerRequest.cs
// Purpose: Request body for PUT /api/v1/prosumers/{nic}. NIC is immutable (it is the primary
// key) and there is no password field here — no password-change endpoint exists in this phase.

namespace smart_solar_mgt_api.Models.Dtos;

public record UpdateProsumerRequest(string Email, string? FullName);
