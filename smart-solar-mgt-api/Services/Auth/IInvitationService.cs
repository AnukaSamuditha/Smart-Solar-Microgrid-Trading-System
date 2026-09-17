// IInvitationService.cs
// Purpose: Abstraction over invitation-token issuance and single-use consumption.
// Implemented by InvitationService. Deliberately unaware of email templating — that lives
// in UserService, which orchestrates account creation, invitation, and emailing.

namespace smart_solar_mgt_api.Services.Auth;

public interface IInvitationService
{
    Task<string> CreateInvitationAsync(
        string userId,
        string createdByUserId,
        CancellationToken cancellationToken = default);

    Task<InvitationAcceptResult> AcceptAsync(
        string presentedToken,
        string newPassword,
        CancellationToken cancellationToken = default);
}

public record InvitationAcceptResult(bool Succeeded, string? UserId, InvitationFailureReason? FailureReason);

public enum InvitationFailureReason
{
    NotFound,
    Expired,
    AlreadyUsed
}
