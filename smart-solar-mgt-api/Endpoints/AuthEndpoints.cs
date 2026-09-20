// AuthEndpoints.cs
// Purpose: Maps the unauthenticated authentication endpoints — login, refresh, logout, and
// invitation acceptance. Login/refresh/logout additionally set/clear the HttpOnly web auth
// cookies via ICookieAuthService, alongside the JSON-body/bearer response the mobile app
// uses — see docs/authentication-implementation-approach.md, sections 6 and 12.

using smart_solar_mgt_api.Authorization;
using smart_solar_mgt_api.Models.Dtos;
using smart_solar_mgt_api.Models.Enums;
using smart_solar_mgt_api.Services.Auth;
using smart_solar_mgt_api.Services.Prosumers;
using smart_solar_mgt_api.Services.Users;

namespace smart_solar_mgt_api.Endpoints;

public static class AuthEndpoints
{
    // register the /api/v1/auth endpoint group
    public static IEndpointRouteBuilder MapAuthEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/v1/auth").WithTags("Auth");

        group.MapPost("/login", LoginAsync);
        group.MapPost("/prosumer/login", ProsumerLoginAsync);
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
        IProsumerService prosumerService,
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
        if (user is null)
        {
            // the web dashboard is Backoffice/Grid Operator only (project-specification.md
            // section 3); a Prosumer email gets a distinct "use the mobile app" message
            // regardless of whether the presented password is actually correct for that
            // profile — we never check it here, so this path leaks nothing about it
            var prosumer = await prosumerService.GetByEmailAsync(request.Email, cancellationToken);
            var errorCode = prosumer is not null ? "ProsumerMobileOnly" : "InvalidCredentials";
            return Results.Json(new { error = errorCode }, statusCode: StatusCodes.Status401Unauthorized);
        }

        // still Invited (no password set yet) — never reachable via VerifyPassword since there's no hash to check
        if (user.PasswordHash is null)
        {
            return Results.Json(new { error = "ProfileIncomplete" }, statusCode: StatusCodes.Status401Unauthorized);
        }

        if (user.Status == UserStatus.Deactivated)
        {
            return Results.Json(new { error = "AccountDeactivated" }, statusCode: StatusCodes.Status401Unauthorized);
        }

        if (!passwordHasher.VerifyPassword(user.PasswordHash, request.Password))
        {
            return Results.Json(new { error = "InvalidCredentials" }, statusCode: StatusCodes.Status401Unauthorized);
        }

        var (accessToken, accessTokenExpiresAtUtc) = jwtTokenService.GenerateAccessToken(user);
        var (refreshToken, refreshTokenExpiresAtUtc) = await refreshTokenService.IssueAsync(user.Id, cancellationToken: cancellationToken);

        cookieAuthService.AppendAuthCookies(httpContext, accessToken, accessTokenExpiresAtUtc, refreshToken, refreshTokenExpiresAtUtc);
        await userService.RecordLoginAsync(user.Id, cancellationToken);

        return Results.Ok(new LoginResponse(accessToken, accessTokenExpiresAtUtc, refreshToken, refreshTokenExpiresAtUtc, user.Role.ToString()));
    }

    // authenticate a prosumer by NIC-or-email/password (mobile-only; no cookies - see LoginAsync
    // for why staff/prosumer login are separate endpoints) and issue an access + refresh token pair
    private static async Task<IResult> ProsumerLoginAsync(
        HttpContext httpContext,
        ProsumerLoginRequest request,
        IProsumerService prosumerService,
        IPasswordHasherService passwordHasher,
        IJwtTokenService jwtTokenService,
        IRefreshTokenService refreshTokenService,
        ICookieAuthService cookieAuthService,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.NicOrEmail) || string.IsNullOrWhiteSpace(request.Password))
        {
            return Results.BadRequest(new { error = "NicOrEmailAndPasswordRequired" });
        }

        var identifier = request.NicOrEmail.Trim();
        var prosumer = await prosumerService.GetByNicAsync(identifier, cancellationToken)
                       ?? await prosumerService.GetByEmailAsync(identifier, cancellationToken);
        if (prosumer is null)
        {
            return Results.Json(new { error = "InvalidCredentials" }, statusCode: StatusCodes.Status401Unauthorized);
        }

        switch (prosumer.Status)
        {
            case ProsumerStatus.PendingApproval:
                return Results.Json(new { error = "PendingApproval" }, statusCode: StatusCodes.Status401Unauthorized);
            case ProsumerStatus.Rejected:
                return Results.Json(new { error = "AccountCreationDenied" }, statusCode: StatusCodes.Status401Unauthorized);
            case ProsumerStatus.Deactivated:
                return Results.Json(new { error = "AccountDeactivated" }, statusCode: StatusCodes.Status401Unauthorized);
        }

        // Invited (staff-created awaiting web accept-invitation, or approved awaiting mobile
        // reset-password) - no hash to check yet, so this must be caught before VerifyPassword
        if (prosumer.PasswordHash is null)
        {
            return Results.Json(new { error = "PasswordNotSet" }, statusCode: StatusCodes.Status401Unauthorized);
        }

        if (!passwordHasher.VerifyPassword(prosumer.PasswordHash, request.Password))
        {
            return Results.Json(new { error = "InvalidCredentials" }, statusCode: StatusCodes.Status401Unauthorized);
        }

        var (accessToken, accessTokenExpiresAtUtc) = jwtTokenService.GenerateAccessToken(prosumer);
        var (refreshToken, refreshTokenExpiresAtUtc) = await refreshTokenService.IssueAsync(
            prosumer.Nic, InvitationAccountType.Prosumer, cancellationToken: cancellationToken);

        cookieAuthService.AppendAuthCookies(httpContext, accessToken, accessTokenExpiresAtUtc, refreshToken, refreshTokenExpiresAtUtc);

        return Results.Ok(new LoginResponse(accessToken, accessTokenExpiresAtUtc, refreshToken, refreshTokenExpiresAtUtc, RoleNames.Prosumer));
    }

    // rotate a refresh token and issue a new access token, rejecting expired, unknown, or reused tokens; the
    // presented token comes from the JSON body (mobile/bearer) or, if absent, the refresh_token cookie (web).
    // Shared by both account types (result.AccountType) since the token/rotation record itself doesn't
    // distinguish which client is calling - see RefreshToken.AccountType and IRefreshTokenService.
    private static async Task<IResult> RefreshAsync(
        HttpContext httpContext,
        RefreshRequest? request,
        IUserService userService,
        IProsumerService prosumerService,
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

        string accessToken;
        DateTime accessTokenExpiresAtUtc;
        string role;

        if (result.AccountType == InvitationAccountType.Prosumer)
        {
            var prosumer = await prosumerService.GetByNicAsync(result.UserId, cancellationToken);
            if (prosumer is null || prosumer.Status != ProsumerStatus.Active)
            {
                return Results.Unauthorized();
            }

            (accessToken, accessTokenExpiresAtUtc) = jwtTokenService.GenerateAccessToken(prosumer);
            role = RoleNames.Prosumer;
        }
        else
        {
            var user = await userService.GetByIdAsync(result.UserId, cancellationToken);
            if (user is null || user.Status != UserStatus.Active)
            {
                return Results.Unauthorized();
            }

            (accessToken, accessTokenExpiresAtUtc) = jwtTokenService.GenerateAccessToken(user);
            role = user.Role.ToString();
        }

        cookieAuthService.AppendAuthCookies(
            httpContext, accessToken, accessTokenExpiresAtUtc, result.NewRefreshToken, result.NewRefreshTokenExpiresAtUtc.Value);

        return Results.Ok(new LoginResponse(
            accessToken,
            accessTokenExpiresAtUtc,
            result.NewRefreshToken,
            result.NewRefreshTokenExpiresAtUtc.Value,
            role));
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

    // consume a single-use invitation token and activate the invited account (a User or a
    // Prosumer, per result.AccountType) with the presenter's chosen password
    private static async Task<IResult> AcceptInvitationAsync(
        AcceptInvitationRequest request,
        IInvitationService invitationService,
        IUserService userService,
        IProsumerService prosumerService,
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

        if (result.AccountType == InvitationAccountType.Prosumer)
        {
            var prosumer = await prosumerService.GetByNicAsync(result.AccountId!, cancellationToken);
            if (prosumer is not null)
            {
                await prosumerService.SendPasswordSetConfirmationEmailAsync(prosumer, cancellationToken);
            }
        }
        else
        {
            var user = await userService.GetByIdAsync(result.AccountId!, cancellationToken);
            if (user is not null)
            {
                await userService.SendPasswordSetConfirmationEmailAsync(user, cancellationToken);
            }
        }

        return Results.Ok(new AcceptInvitationResponse(result.AccountType!.Value.ToString()));
    }
}
