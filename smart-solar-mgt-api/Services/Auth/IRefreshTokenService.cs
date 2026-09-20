// IRefreshTokenService.cs
// Purpose: Abstraction over refresh-token issuance, rotation-on-use, reuse detection, and
// revocation. Implemented by RefreshTokenService. AccountType (mirrors InvitationService) lets
// the same tokens/rotation machinery serve both staff Users and Prosumers.

using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Services.Auth;

public interface IRefreshTokenService
{
    Task<(string Token, DateTime ExpiresAtUtc)> IssueAsync(
        string userId,
        InvitationAccountType accountType = InvitationAccountType.User,
        string? familyId = null,
        CancellationToken cancellationToken = default);

    Task<RefreshTokenValidationResult> ValidateAndRotateAsync(
        string presentedToken,
        CancellationToken cancellationToken = default);

    Task RevokeAsync(string presentedToken, CancellationToken cancellationToken = default);
}

public record RefreshTokenValidationResult(
    bool Succeeded,
    string? UserId,
    InvitationAccountType? AccountType,
    string? NewRefreshToken,
    DateTime? NewRefreshTokenExpiresAtUtc,
    RefreshTokenFailureReason? FailureReason);

public enum RefreshTokenFailureReason
{
    NotFound,
    Expired,
    ReusedAndRevoked
}
