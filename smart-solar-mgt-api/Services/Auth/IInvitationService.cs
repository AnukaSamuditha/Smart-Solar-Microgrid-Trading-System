// IInvitationService.cs
// Purpose: Abstraction over invitation-token issuance and single-use consumption, shared by
// both web app Users and Prosumer profiles (see InvitationAccountType). Implemented by
// InvitationService. Deliberately unaware of email templating — that lives in UserService and
// ProsumerService, which orchestrate account creation, invitation, and emailing.

using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Services.Auth;

public interface IInvitationService
{
    Task<string> CreateInvitationAsync(
        string accountId,
        string createdByUserId,
        InvitationAccountType accountType,
        CancellationToken cancellationToken = default);

    Task<InvitationAcceptResult> AcceptAsync(
        string presentedToken,
        string newPassword,
        CancellationToken cancellationToken = default);
}

public record InvitationAcceptResult(
    bool Succeeded,
    string? AccountId,
    InvitationAccountType? AccountType,
    InvitationFailureReason? FailureReason);

public enum InvitationFailureReason
{
    NotFound,
    Expired,
    AlreadyUsed
}
