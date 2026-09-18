// CreateProsumerRequest.cs
// Purpose: Request body for POST /api/v1/prosumers (Backoffice or Grid Operator). No password
// field — the prosumer is created in Invited status and sets their own password by accepting
// an emailed setup invitation, exactly like web app User accounts (see UserService).

namespace smart_solar_mgt_api.Models.Dtos;

public record CreateProsumerRequest(string Nic, string Email, string? FullName);
