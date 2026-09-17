// LoginResponse.cs
// Purpose: Response body for a successful login or refresh — the JSON-body token delivery
// used by both the native mobile client and (until HttpOnly cookies are added later) the web
// client. See docs/authentication-implementation-approach.md, section 6.

namespace smart_solar_mgt_api.Models.Dtos;

public record LoginResponse(
    string AccessToken,
    DateTime AccessTokenExpiresAtUtc,
    string RefreshToken,
    DateTime RefreshTokenExpiresAtUtc,
    string Role);
