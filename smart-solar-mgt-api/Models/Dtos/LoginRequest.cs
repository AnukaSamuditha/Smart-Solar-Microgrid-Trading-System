// LoginRequest.cs
// Purpose: Request body for POST /api/v1/auth/login.

namespace smart_solar_mgt_api.Models.Dtos;

public record LoginRequest(string Email, string Password);
