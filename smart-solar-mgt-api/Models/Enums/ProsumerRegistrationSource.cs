// ProsumerRegistrationSource.cs
// Purpose: Records which of the two prosumer-creation paths produced a given profile - purely
// informational/audit (e.g. for the review queue UI), never used for authorization decisions.

namespace smart_solar_mgt_api.Models.Enums;

public enum ProsumerRegistrationSource
{
    StaffInvited,
    SelfRegistered
}
