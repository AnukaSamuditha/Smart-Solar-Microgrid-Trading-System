// ICookieAuthService.cs
// Purpose: Abstraction over HttpOnly cookie delivery for the web browser auth flow, additive
// to the existing JSON-body/bearer path the mobile app uses. Implemented by
// CookieAuthService. See docs/authentication-implementation-approach.md, section 6.

namespace smart_solar_mgt_api.Services.Auth;

public interface ICookieAuthService
{
    void AppendAuthCookies(
        HttpContext httpContext,
        string accessToken,
        DateTime accessTokenExpiresAtUtc,
        string refreshToken,
        DateTime refreshTokenExpiresAtUtc);

    void ClearAuthCookies(HttpContext httpContext);
}
