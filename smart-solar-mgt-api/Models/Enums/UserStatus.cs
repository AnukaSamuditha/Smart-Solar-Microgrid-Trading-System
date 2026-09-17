// UserStatus.cs
// Purpose: The account lifecycle states for Backoffice/Grid Operator users:
// Invited (created, no password yet) -> Active (invitation accepted) -> Deactivated,
// with Deactivated able to return to Active via reactivation.

namespace smart_solar_mgt_api.Models.Enums;

public enum UserStatus
{
    Invited,
    Active,
    Deactivated
}
