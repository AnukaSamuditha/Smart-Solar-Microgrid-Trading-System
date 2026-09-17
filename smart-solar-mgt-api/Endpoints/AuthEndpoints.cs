// AuthEndpoints.cs
// Purpose: Maps the unauthenticated authentication endpoints — login, refresh, logout, and
// invitation acceptance. Login/refresh/logout additionally set/clear the HttpOnly web auth
// cookies via ICookieAuthService, alongside the JSON-body/bearer response the mobile app
// uses — see docs/authentication-implementation-approach.md, sections 6 and 12.

using smart_solar_mgt_api.Models.Dtos;
using smart_solar_mgt_api.Models.Enums;
using smart_solar_mgt_api.Services.Auth;
using smart_solar_mgt_api.Services.Users;

namespace smart_solar_mgt_api.Endpoints;

public static class AuthEndpoints
{
    // register the /api/v1/auth endpoint group
    public static IEndpointRouteBuilder MapAuthEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/v1/auth").WithTags("Auth");

        group.MapPost("/login", LoginAsync);
        group.MapPost("/refresh", RefreshAsync);
        group.MapPost("/logout", LogoutAsync);
        group.MapPost("/accept-invitation", AcceptInvitationAsync);

        return app;
    }

    // authenticate a user by email/password, issue a new access + refresh token pair, and set the web cookies
    private static async Task<IResult> LoginAsync(
        HttpContext httpContext,
        LoginRequest request,
        IUserService userService,
        IPasswordHasherService passwordHasher,
        IJwtTokenService jwtTokenService,
        IRefreshTokenService refreshTokenService,
        ICookieAuthService cookieAuthService,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Email) || string.IsNullOrWhiteSpace(request.Password))
        {
            return Results.BadRequest(new { error = "EmailAndPasswordRequired" });
        }

        var user = await userService.GetByEmailAsync(request.Email, cancellationToken);
        if (user is null || user.PasswordHash is null || user.Status != UserStatus.Active ||
            !passwordHasher.VerifyPassword(user.PasswordHash, request.Password))
        {
            return Results.Unauthorized();
        }

        var (accessToken, accessTokenExpiresAtUtc) = jwtTokenService.GenerateAccessToken(user);
        var (refreshToken, refreshTokenExpiresAtUtc) = await refreshTokenService.IssueAsync(user.Id, cancellationToken: cancellationToken);

        cookieAuthService.AppendAuthCookies(httpContext, accessToken, accessTokenExpiresAtUtc, refreshToken, refreshTokenExpiresAtUtc);

        return Results.Ok(new LoginResponse(accessToken, accessTokenExpiresAtUtc, refreshToken, refreshTokenExpiresAtUtc, user.Role.ToString()));
    }

    // rotate a refresh token and issue a new access token, rejecting expired, unknown, or reused tokens; the
    // presented token comes from the JSON body (mobile/bearer) or, if absent, the refresh_token cookie (web)
    private static async Task<IResult> RefreshAsync(
        HttpContext httpContext,
        RefreshRequest? request,
        IUserService userService,
        IJwtTokenService jwtTokenService,
        IRefreshTokenService refreshTokenService,
        ICookieAuthService cookieAuthService,
        CancellationToken cancellationToken)
    {
        var presentedToken = request?.RefreshToken ?? httpContext.Request.Cookies[CookieAuthService.RefreshTokenCookieName];
        if (string.IsNullOrWhiteSpace(presentedToken))
        {
            return Results.Unauthorized();
        }

        var result = await refreshTokenService.ValidateAndRotateAsync(presentedToken, cancellationToken);
        if (!result.Succeeded || result.UserId is null || result.NewRefreshToken is null || result.NewRefreshTokenExpiresAtUtc is null)
        {
            return Results.Unauthorized();
        }

        var user = await userService.GetByIdAsync(result.UserId, cancellationToken);
        if (user is null || user.Status != UserStatus.Active)
        {
            return Results.Unauthorized();
        }

        var (accessToken, accessTokenExpiresAtUtc) = jwtTokenService.GenerateAccessToken(user);

        cookieAuthService.AppendAuthCookies(
            httpContext, accessToken, accessTokenExpiresAtUtc, result.NewRefreshToken, result.NewRefreshTokenExpiresAtUtc.Value);

        return Results.Ok(new LoginResponse(
            accessToken,
            accessTokenExpiresAtUtc,
            result.NewRefreshToken,
            result.NewRefreshTokenExpiresAtUtc.Value,
            user.Role.ToString()));
    }

    // revoke the presented refresh token on logout and clear the web auth cookies regardless of how it was presented
    private static async Task<IResult> LogoutAsync(
        HttpContext httpContext,
        RefreshRequest? request,
        IRefreshTokenService refreshTokenService,
        ICookieAuthService cookieAuthService,
        CancellationToken cancellationToken)
    {
        var presentedToken = request?.RefreshToken ?? httpContext.Request.Cookies[CookieAuthService.RefreshTokenCookieName];
        if (!string.IsNullOrWhiteSpace(presentedToken))
        {
            await refreshTokenService.RevokeAsync(presentedToken, cancellationToken);
        }

        cookieAuthService.ClearAuthCookies(httpContext);

        return Results.NoContent();
    }

    // consume a single-use invitation token and activate the invited user's account with their chosen password
    private static async Task<IResult> AcceptInvitationAsync(
        AcceptInvitationRequest request,
        IInvitationService invitationService,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Token) || string.IsNullOrWhiteSpace(request.NewPassword) || request.NewPassword.Length < 8)
        {
            return Results.BadRequest(new { error = "InvalidRequest" });
        }

        var result = await invitationService.AcceptAsync(request.Token, request.NewPassword, cancellationToken);
        if (!result.Succeeded)
        {
            return Results.BadRequest(new { error = result.FailureReason?.ToString() ?? "InvalidToken" });
        }

        return Results.NoContent();
    }
}
