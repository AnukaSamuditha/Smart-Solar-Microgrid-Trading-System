// ProsumerStatus.cs
// Purpose: The account lifecycle states for Prosumer profiles. Two creation paths converge on
// the same Invited -> Active shape:
//   - Staff-initiated (ProsumerService.CreateAsync): starts directly at Invited.
//   - Self-registered from the mobile app (ProsumerService.RegisterAsync): starts at
//     PendingApproval, then a Backoffice/Grid Operator reviewer moves it to either Invited
//     (ApproveAsync - same invitation-token machinery as the staff path from here on) or
//     Rejected (DenyAsync, terminal).
// Deactivated is reachable from Active only, and can return to Active via reactivation
// (Backoffice-only, see ProsumerEndpoints). Mirrors UserStatus's invitation-based lifecycle
// for the staff/Invited/Active/Deactivated portion.

namespace smart_solar_mgt_api.Models.Enums;

public enum ProsumerStatus
{
    Invited,
    Active,
    Deactivated,
    PendingApproval,
    Rejected
}
