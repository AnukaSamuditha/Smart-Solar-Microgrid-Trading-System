// InvitationAccountType.cs
// Purpose: Discriminates which collection an Invitation's AccountId refers to, so the shared
// invitation-token infrastructure (InvitationService) can activate either a web app User or a
// Prosumer profile from the same accept-invitation flow. Defaults to User on documents written
// before this field existed (enum's zero-value), which is correct since every pre-existing
// invitation was issued for a User.

namespace smart_solar_mgt_api.Models.Enums;

public enum InvitationAccountType
{
    User,
    Prosumer
}
