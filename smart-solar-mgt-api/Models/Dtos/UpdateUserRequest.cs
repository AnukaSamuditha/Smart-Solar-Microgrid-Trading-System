// UpdateUserRequest.cs
// Purpose: Request body for the Backoffice-only account update endpoint
// (PUT /api/v1/users/{id}). Role and Status are not editable here — role is fixed at creation
// and status changes go through the dedicated deactivate/reactivate endpoints.

namespace smart_solar_mgt_api.Models.Dtos;

public record UpdateUserRequest(string Email, string? Username);
