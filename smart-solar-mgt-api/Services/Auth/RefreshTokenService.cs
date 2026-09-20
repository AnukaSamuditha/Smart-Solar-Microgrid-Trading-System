// RefreshTokenService.cs
// Purpose: Persists refresh tokens (hash only), rotates them on every use, and detects reuse
// of an already-rotated token by revoking its entire rotation family — the mitigation for
// stolen-token replay described in docs/authentication-implementation-approach.md, section 6.

using Microsoft.Extensions.Options;
using MongoDB.Driver;
using smart_solar_mgt_api.Configuration;
using smart_solar_mgt_api.Data;
using smart_solar_mgt_api.Models.Entities;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Services.Auth;

public class RefreshTokenService : IRefreshTokenService
{
    private readonly MongoContext _mongoContext;
    private readonly JwtOptions _jwtOptions;

    public RefreshTokenService(MongoContext mongoContext, IOptions<JwtOptions> jwtOptions)
    {
        _mongoContext = mongoContext;
        _jwtOptions = jwtOptions.Value;
    }

    // generate a new opaque refresh token, persist only its hash, and return the raw value to the caller
    public async Task<(string Token, DateTime ExpiresAtUtc)> IssueAsync(
        string userId,
        InvitationAccountType accountType = InvitationAccountType.User,
        string? familyId = null,
        CancellationToken cancellationToken = default)
    {
        var rawToken = SecureTokenGenerator.GenerateToken();
        var expiresAtUtc = DateTime.UtcNow.AddDays(_jwtOptions.RefreshTokenLifetimeDays);

        var refreshToken = new RefreshToken
        {
            UserId = userId,
            AccountType = accountType,
            TokenHash = SecureTokenGenerator.Hash(rawToken),
            FamilyId = familyId ?? Guid.NewGuid().ToString(),
            ExpiresAt = expiresAtUtc,
            CreatedAt = DateTime.UtcNow
        };

        await _mongoContext.RefreshTokens.InsertOneAsync(refreshToken, cancellationToken: cancellationToken);

        return (rawToken, expiresAtUtc);
    }

    // validate a presented refresh token, detect reuse of an already-rotated token, and rotate it if valid
    public async Task<RefreshTokenValidationResult> ValidateAndRotateAsync(
        string presentedToken,
        CancellationToken cancellationToken = default)
    {
        var tokenHash = SecureTokenGenerator.Hash(presentedToken);
        var existing = await _mongoContext.RefreshTokens
            .Find(t => t.TokenHash == tokenHash)
            .FirstOrDefaultAsync(cancellationToken);

        if (existing is null)
        {
            return new RefreshTokenValidationResult(false, null, null, null, null, RefreshTokenFailureReason.NotFound);
        }

        if (existing.RevokedAt is not null)
        {
            await RevokeFamilyAsync(existing.FamilyId, cancellationToken);
            return new RefreshTokenValidationResult(false, null, null, null, null, RefreshTokenFailureReason.ReusedAndRevoked);
        }

        if (existing.ExpiresAt < DateTime.UtcNow)
        {
            return new RefreshTokenValidationResult(false, null, null, null, null, RefreshTokenFailureReason.Expired);
        }

        var (newToken, newExpiresAtUtc) = await IssueAsync(
            existing.UserId, existing.AccountType, existing.FamilyId, cancellationToken);
        var newTokenHash = SecureTokenGenerator.Hash(newToken);

        var revokeFilter = Builders<RefreshToken>.Filter.Eq(t => t.Id, existing.Id);
        var revokeUpdate = Builders<RefreshToken>.Update
            .Set(t => t.RevokedAt, DateTime.UtcNow)
            .Set(t => t.ReplacedByTokenHash, newTokenHash);
        await _mongoContext.RefreshTokens.UpdateOneAsync(revokeFilter, revokeUpdate, cancellationToken: cancellationToken);

        return new RefreshTokenValidationResult(true, existing.UserId, existing.AccountType, newToken, newExpiresAtUtc, null);
    }

    // revoke a single refresh token, used on logout
    public async Task RevokeAsync(string presentedToken, CancellationToken cancellationToken = default)
    {
        var tokenHash = SecureTokenGenerator.Hash(presentedToken);
        var filter = Builders<RefreshToken>.Filter.Eq(t => t.TokenHash, tokenHash) &
                     Builders<RefreshToken>.Filter.Eq(t => t.RevokedAt, (DateTime?)null);
        var update = Builders<RefreshToken>.Update.Set(t => t.RevokedAt, DateTime.UtcNow);
        await _mongoContext.RefreshTokens.UpdateOneAsync(filter, update, cancellationToken: cancellationToken);
    }

    // revoke every still-active token in a rotation family, used when token reuse is detected
    private async Task RevokeFamilyAsync(string familyId, CancellationToken cancellationToken)
    {
        var filter = Builders<RefreshToken>.Filter.Eq(t => t.FamilyId, familyId) &
                     Builders<RefreshToken>.Filter.Eq(t => t.RevokedAt, (DateTime?)null);
        var update = Builders<RefreshToken>.Update.Set(t => t.RevokedAt, DateTime.UtcNow);
        await _mongoContext.RefreshTokens.UpdateManyAsync(filter, update, cancellationToken: cancellationToken);
    }
}
