// MicrogridNodeStatus.cs
// Purpose: Lifecycle states for a Microgrid Node (solar grid hub): Active -> Deactivated, with
// Deactivated able to return to Active via reactivation (Backoffice-only, see
// MicrogridNodeEndpoints). Deactivation is blocked while active energy reservations exist
// against the node — see IReservationLookupService.

namespace smart_solar_mgt_api.Models.Enums;

public enum MicrogridNodeStatus
{
    Active,
    Deactivated
}
