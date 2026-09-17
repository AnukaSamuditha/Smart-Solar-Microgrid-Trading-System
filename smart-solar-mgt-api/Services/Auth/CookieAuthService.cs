// CookieAuthService.cs
// Purpose: Sets and clears the HttpOnly access/refresh token cookies and the readable
// double-submit CSRF cookie used by the web browser client. Cookie `Secure` is derived from
// the current request scheme (true whenever the request itself arrived over HTTPS) rather
// than hardcoded, so local HTTP development keeps working while HTTPS environments always
// get Secure cookies. See docs/authentication-implementation-approach.md, section 6, and
// Authorization/CsrfProtectionMiddleware.cs for how the CSRF cookie is validated.

namespace smart_solar_mgt_api.Services.Auth;

public class CookieAuthService : ICookieAuthService
{
    public const string AccessTokenCookieName = "access_token";
    public const string RefreshTokenCookieName = "refresh_token";
    public const string CsrfCookieName = "XSRF-TOKEN";

    private const string AuthPath = "/api/v1/auth";

    // set the HttpOnly access/refresh cookies plus a readable CSRF cookie after a successful login or refresh
    public void AppendAuthCookies(
        HttpContext httpContext,
        string accessToken,
        DateTime accessTokenExpiresAtUtc,
        string refreshToken,
        DateTime refreshTokenExpiresAtUtc)
    {
        var secure = httpContext.Request.IsHttps;

        httpContext.Response.Cookies.Append(AccessTokenCookieName, accessToken, new CookieOptions
        {
            HttpOnly = true,
            Secure = secure,
            SameSite = SameSiteMode.Strict,
            Path = "/",
            Expires = accessTokenExpiresAtUtc
        });

        // scoped to the auth path only — this cookie's sole purpose is being presented back to /refresh and /logout
        httpContext.Response.Cookies.Append(RefreshTokenCookieName, refreshToken, new CookieOptions
        {
            HttpOnly = true,
            Secure = secure,
            SameSite = SameSiteMode.Strict,
            Path = AuthPath,
            Expires = refreshTokenExpiresAtUtc
        });

        // not HttpOnly — the frontend reads this value and echoes it back as a request header (double-submit pattern)
        httpContext.Response.Cookies.Append(CsrfCookieName, SecureTokenGenerator.GenerateToken(), new CookieOptions
        {
            HttpOnly = false,
            Secure = secure,
            SameSite = SameSiteMode.Strict,
            Path = "/",
            Expires = refreshTokenExpiresAtUtc
        });
    }

    // clear all auth cookies on logout; options must match what was used to set each cookie for the browser to remove it
    public void ClearAuthCookies(HttpContext httpContext)
    {
        var secure = httpContext.Request.IsHttps;

        httpContext.Response.Cookies.Delete(AccessTokenCookieName, new CookieOptions
        {
            HttpOnly = true,
            Secure = secure,
            SameSite = SameSiteMode.Strict,
            Path = "/"
        });

        httpContext.Response.Cookies.Delete(RefreshTokenCookieName, new CookieOptions
        {
            HttpOnly = true,
            Secure = secure,
            SameSite = SameSiteMode.Strict,
            Path = AuthPath
        });

        httpContext.Response.Cookies.Delete(CsrfCookieName, new CookieOptions
        {
            HttpOnly = false,
            Secure = secure,
            SameSite = SameSiteMode.Strict,
            Path = "/"
        });
    }
}
