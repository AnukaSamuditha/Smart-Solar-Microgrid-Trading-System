// InvitationService.cs
// Purpose: Issues single-use, time-limited account-setup invitation tokens and atomically
// consumes them to activate the invited user's account. See
// docs/authentication-implementation-approach.md, section 4.

using Microsoft.Extensions.Options;
using MongoDB.Driver;
using smart_solar_mgt_api.Configuration;
using smart_solar_mgt_api.Data;
using smart_solar_mgt_api.Models.Entities;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Services.Auth;

public class InvitationService : IInvitationService
{
    private readonly MongoContext _mongoContext;
    private readonly IPasswordHasherService _passwordHasher;
    private readonly InvitationOptions _invitationOptions;

    public InvitationService(
        MongoContext mongoContext,
        IPasswordHasherService passwordHasher,
        IOptions<InvitationOptions> invitationOptions)
    {
        _mongoContext = mongoContext;
        _passwordHasher = passwordHasher;
        _invitationOptions = invitationOptions.Value;
    }

    // generate a single-use invitation token and persist only its hash; the raw token is returned once, to be emailed
    public async Task<string> CreateInvitationAsync(
        string userId,
        string createdByUserId,
        CancellationToken cancellationToken = default)
    {
        var rawToken = SecureTokenGenerator.GenerateToken();

        var invitation = new Invitation
        {
            UserId = userId,
            TokenHash = SecureTokenGenerator.Hash(rawToken),
            ExpiresAt = DateTime.UtcNow.AddHours(_invitationOptions.TokenLifetimeHours),
            CreatedAt = DateTime.UtcNow,
            CreatedBy = createdByUserId
        };

        await _mongoContext.Invitations.InsertOneAsync(invitation, cancellationToken: cancellationToken);

        return rawToken;
    }

    // atomically consume an invitation token (single-use, filtered on UsedAt == null) and activate the invited user
    public async Task<InvitationAcceptResult> AcceptAsync(
        string presentedToken,
        string newPassword,
        CancellationToken cancellationToken = default)
    {
        var tokenHash = SecureTokenGenerator.Hash(presentedToken);

        var filter = Builders<Invitation>.Filter.Eq(i => i.TokenHash, tokenHash) &
                     Builders<Invitation>.Filter.Eq(i => i.UsedAt, (DateTime?)null);
        var update = Builders<Invitation>.Update.Set(i => i.UsedAt, DateTime.UtcNow);

        var invitation = await _mongoContext.Invitations.FindOneAndUpdateAsync(filter, update, cancellationToken: cancellationToken);

        if (invitation is null)
        {
            var alreadyUsed = await _mongoContext.Invitations
                .Find(i => i.TokenHash == tokenHash)
                .AnyAsync(cancellationToken);
            var reason = alreadyUsed ? InvitationFailureReason.AlreadyUsed : InvitationFailureReason.NotFound;
            return new InvitationAcceptResult(false, null, reason);
        }

        if (invitation.ExpiresAt < DateTime.UtcNow)
        {
            return new InvitationAcceptResult(false, null, InvitationFailureReason.Expired);
        }

        var passwordHash = _passwordHasher.HashPassword(newPassword);
        var userFilter = Builders<User>.Filter.Eq(u => u.Id, invitation.UserId);
        var userUpdate = Builders<User>.Update
            .Set(u => u.PasswordHash, passwordHash)
            .Set(u => u.Status, UserStatus.Active)
            .Set(u => u.UpdatedAt, DateTime.UtcNow);
        await _mongoContext.Users.UpdateOneAsync(userFilter, userUpdate, cancellationToken: cancellationToken);

        return new InvitationAcceptResult(true, invitation.UserId, null);
    }
}
