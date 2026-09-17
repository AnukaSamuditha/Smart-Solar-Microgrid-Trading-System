// UserService.cs
// Purpose: Orchestrates account creation (invite + email), lookups used by login/authz,
// profile updates, and account lifecycle transitions (deactivate/reactivate/delete). Protects
// the seeded super-admin account from accidental deactivation/deletion (see User.IsSeededAdmin).

using Microsoft.Extensions.Options;
using MongoDB.Driver;
using smart_solar_mgt_api.Configuration;
using smart_solar_mgt_api.Data;
using smart_solar_mgt_api.Models.Entities;
using smart_solar_mgt_api.Models.Enums;
using smart_solar_mgt_api.Services.Auth;
using smart_solar_mgt_api.Services.Email;

namespace smart_solar_mgt_api.Services.Users;

public class UserService : IUserService
{
    private readonly MongoContext _mongoContext;
    private readonly IInvitationService _invitationService;
    private readonly IEmailSender _emailSender;
    private readonly InvitationOptions _invitationOptions;

    public UserService(
        MongoContext mongoContext,
        IInvitationService invitationService,
        IEmailSender emailSender,
        IOptions<InvitationOptions> invitationOptions)
    {
        _mongoContext = mongoContext;
        _invitationService = invitationService;
        _emailSender = emailSender;
        _invitationOptions = invitationOptions.Value;
    }

    // create a new user in Invited status, generate a setup invitation, and email it to them
    public async Task<User> CreateInvitedUserAsync(
        string email,
        string? username,
        UserRole role,
        string createdByUserId,
        CancellationToken cancellationToken = default)
    {
        var normalizedEmail = email.Trim().ToLowerInvariant();

        var user = new User
        {
            Email = normalizedEmail,
            Username = username,
            Role = role,
            Status = UserStatus.Invited,
            CreatedAt = DateTime.UtcNow,
            CreatedBy = createdByUserId
        };

        await _mongoContext.Users.InsertOneAsync(user, cancellationToken: cancellationToken);

        var rawToken = await _invitationService.CreateInvitationAsync(user.Id, createdByUserId, cancellationToken);
        await SendInvitationEmailAsync(user, rawToken, cancellationToken);

        return user;
    }

    // look up a user by normalized email, used during login
    public async Task<User?> GetByEmailAsync(string email, CancellationToken cancellationToken = default)
    {
        var normalizedEmail = email.Trim().ToLowerInvariant();
        return await _mongoContext.Users.Find(u => u.Email == normalizedEmail).FirstOrDefaultAsync(cancellationToken);
    }

    // look up a user by id, used to resolve the current authenticated user and during token refresh
    public async Task<User?> GetByIdAsync(string userId, CancellationToken cancellationToken = default) =>
        await _mongoContext.Users.Find(u => u.Id == userId).FirstOrDefaultAsync(cancellationToken);

    // return every Backoffice and Grid Operator account for the admin listing endpoint
    public async Task<IReadOnlyList<User>> ListAsync(CancellationToken cancellationToken = default) =>
        await _mongoContext.Users.Find(FilterDefinition<User>.Empty).ToListAsync(cancellationToken);

    // deactivate a user account, blocking further logins; refuses to deactivate the seeded super-admin
    public async Task<UserActionResult> DeactivateAsync(
        string userId,
        string performedByUserId,
        CancellationToken cancellationToken = default) =>
        await SetStatusAsync(userId, UserStatus.Deactivated, performedByUserId, cancellationToken);

    // reactivate a previously deactivated user account
    public async Task<UserActionResult> ReactivateAsync(
        string userId,
        string performedByUserId,
        CancellationToken cancellationToken = default) =>
        await SetStatusAsync(userId, UserStatus.Active, performedByUserId, cancellationToken);

    // update an account's email/username; refuses if the new email is already taken by another user
    public async Task<UserActionResult> UpdateAsync(
        string userId,
        string email,
        string? username,
        string performedByUserId,
        CancellationToken cancellationToken = default)
    {
        var user = await GetByIdAsync(userId, cancellationToken);
        if (user is null)
        {
            return UserActionResult.NotFound;
        }

        var normalizedEmail = email.Trim().ToLowerInvariant();
        var filter = Builders<User>.Filter.Eq(u => u.Id, userId);
        var update = Builders<User>.Update
            .Set(u => u.Email, normalizedEmail)
            .Set(u => u.Username, username)
            .Set(u => u.UpdatedAt, DateTime.UtcNow)
            .Set(u => u.UpdatedBy, performedByUserId);

        try
        {
            await _mongoContext.Users.UpdateOneAsync(filter, update, cancellationToken: cancellationToken);
        }
        catch (MongoWriteException ex) when (ex.WriteError.Category == ServerErrorCategory.DuplicateKey)
        {
            return UserActionResult.EmailConflict;
        }

        return UserActionResult.Succeeded;
    }

    // permanently delete an account and its associated refresh tokens/invitations; refuses to delete the seeded super-admin
    public async Task<UserActionResult> DeleteAsync(
        string userId,
        string performedByUserId,
        CancellationToken cancellationToken = default)
    {
        var user = await GetByIdAsync(userId, cancellationToken);
        if (user is null)
        {
            return UserActionResult.NotFound;
        }

        if (user.IsSeededAdmin)
        {
            return UserActionResult.Protected;
        }

        await _mongoContext.Users.DeleteOneAsync(u => u.Id == userId, cancellationToken);
        await _mongoContext.RefreshTokens.DeleteManyAsync(t => t.UserId == userId, cancellationToken);
        await _mongoContext.Invitations.DeleteManyAsync(i => i.UserId == userId, cancellationToken);

        return UserActionResult.Succeeded;
    }

    // stamp the account's last-login time, called after a successful password authentication
    public async Task RecordLoginAsync(string userId, CancellationToken cancellationToken = default)
    {
        var filter = Builders<User>.Filter.Eq(u => u.Id, userId);
        var update = Builders<User>.Update.Set(u => u.LastLoginAt, DateTime.UtcNow);
        await _mongoContext.Users.UpdateOneAsync(filter, update, cancellationToken: cancellationToken);
    }

    // shared lifecycle-transition logic used by DeactivateAsync and ReactivateAsync
    private async Task<UserActionResult> SetStatusAsync(
        string userId,
        UserStatus newStatus,
        string performedByUserId,
        CancellationToken cancellationToken)
    {
        var user = await GetByIdAsync(userId, cancellationToken);
        if (user is null)
        {
            return UserActionResult.NotFound;
        }

        if (newStatus == UserStatus.Deactivated && user.IsSeededAdmin)
        {
            return UserActionResult.Protected;
        }

        var filter = Builders<User>.Filter.Eq(u => u.Id, userId);
        var update = Builders<User>.Update
            .Set(u => u.Status, newStatus)
            .Set(u => u.UpdatedAt, DateTime.UtcNow)
            .Set(u => u.UpdatedBy, performedByUserId);
        await _mongoContext.Users.UpdateOneAsync(filter, update, cancellationToken: cancellationToken);

        return UserActionResult.Succeeded;
    }

    // build and send the invitation email containing the one-time setup link
    private async Task SendInvitationEmailAsync(User user, string rawToken, CancellationToken cancellationToken)
    {
        var inviteLink = $"{_invitationOptions.FrontendBaseUrl}/reset-password?token={Uri.EscapeDataString(rawToken)}";
        var (subject, body) = InvitationEmailTemplate.Build(user, inviteLink, _invitationOptions.TokenLifetimeHours);

        await _emailSender.SendAsync(user.Email, subject, body, cancellationToken);
    }

    // notify a user that their password was just set/updated, after a successful invitation-accept
    public async Task SendPasswordSetConfirmationEmailAsync(User user, CancellationToken cancellationToken = default)
    {
        var signInLink = $"{_invitationOptions.FrontendBaseUrl}/login";
        var (subject, body) = PasswordSetConfirmationEmailTemplate.Build(user, signInLink);

        await _emailSender.SendAsync(user.Email, subject, body, cancellationToken);
    }
}
