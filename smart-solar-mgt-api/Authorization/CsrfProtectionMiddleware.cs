// CsrfProtectionMiddleware.cs
// Purpose: Double-submit CSRF protection for the cookie-based web auth flow. Cookies are
// attached to requests automatically by the browser, so any mutating request that could ride
// on an authenticated user's cookies must also prove the caller can read the non-HttpOnly
// XSRF-TOKEN cookie (which a cross-site attacker cannot) by echoing it back as a header.
// Requests authenticated via a bearer token (the mobile app, or ad-hoc API clients) never
// carry the auth cookies in the first place, so they are exempt. See
// docs/authentication-implementation-approach.md, section 6, and
// Services/Auth/CookieAuthService.cs for where the cookies are issued.

using smart_solar_mgt_api.Services.Auth;

namespace smart_solar_mgt_api.Authorization;

public class CsrfProtectionMiddleware(RequestDelegate next)
{
    private static readonly HashSet<string> SafeMethods = new(StringComparer.OrdinalIgnoreCase)
    {
        HttpMethods.Get, HttpMethods.Head, HttpMethods.Options, HttpMethods.Trace
    };

    private const string CsrfHeaderName = "X-XSRF-TOKEN";

    // reject mutating, cookie-authenticated requests unless the CSRF header matches the CSRF cookie
    public async Task InvokeAsync(HttpContext context)
    {
        var isCookieAuthenticated =
            context.Request.Cookies.ContainsKey(CookieAuthService.AccessTokenCookieName) ||
            context.Request.Cookies.ContainsKey(CookieAuthService.RefreshTokenCookieName);
        var hasBearerHeader = !string.IsNullOrEmpty(context.Request.Headers.Authorization);

        if (!SafeMethods.Contains(context.Request.Method) && isCookieAuthenticated && !hasBearerHeader)
        {
            var cookieToken = context.Request.Cookies[CookieAuthService.CsrfCookieName];
            var headerToken = context.Request.Headers[CsrfHeaderName].FirstOrDefault();

            if (string.IsNullOrEmpty(cookieToken) || string.IsNullOrEmpty(headerToken) ||
                !string.Equals(cookieToken, headerToken, StringComparison.Ordinal))
            {
                context.Response.StatusCode = StatusCodes.Status403Forbidden;
                await context.Response.WriteAsJsonAsync(new { error = "CsrfValidationFailed" });
                return;
            }
        }

        await next(context);
    }
}
