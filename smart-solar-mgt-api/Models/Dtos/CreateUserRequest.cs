// CreateUserRequest.cs
// Purpose: Request body for the Backoffice-only account creation endpoints
// (POST /api/v1/users/backoffice and POST /api/v1/users/grid-operators). The role is implied
// by which endpoint is called, not by a field on this request.

namespace smart_solar_mgt_api.Models.Dtos;

public record CreateUserRequest(string Email, string? Username);
