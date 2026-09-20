// RegisterProsumerRequest.cs
// Purpose: Request body for POST /api/v1/prosumers/register (public, unauthenticated - a
// prosumer submitting this themselves from the mobile app). No password field: the profile
// starts PendingApproval and the prosumer sets a password only after a Backoffice/Grid Operator
// reviewer approves it (see ProsumerService.RegisterAsync/ApproveAsync).

namespace smart_solar_mgt_api.Models.Dtos;

public record RegisterProsumerRequest(string Nic, string Email, string? FullName, string? Phone, string? Address);
