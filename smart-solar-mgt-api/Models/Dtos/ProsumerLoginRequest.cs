// ProsumerLoginRequest.cs
// Purpose: Request body for POST /api/v1/auth/prosumer/login. NicOrEmail accepts either
// identifier since the mobile login screen has one identifier field for both roles - see
// AuthEndpoints.ProsumerLoginAsync.

namespace smart_solar_mgt_api.Models.Dtos;

public record ProsumerLoginRequest(string NicOrEmail, string Password);
