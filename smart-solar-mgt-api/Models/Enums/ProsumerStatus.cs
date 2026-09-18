// ProsumerStatus.cs
// Purpose: The account lifecycle states for Prosumer profiles: Invited (created, no password
// yet) -> Active (setup invitation accepted) -> Deactivated, with Deactivated able to return to
// Active via reactivation (Backoffice-only, see ProsumerEndpoints). Mirrors UserStatus's
// invitation-based lifecycle exactly.

namespace smart_solar_mgt_api.Models.Enums;

public enum ProsumerStatus
{
    Invited,
    Active,
    Deactivated
}
