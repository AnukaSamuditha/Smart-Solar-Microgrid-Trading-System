// ReservationStatus.cs
// Purpose: Lifecycle states for an energy slot reservation. Deliberately just Confirmed/Cancelled
// — this feature (spec section 3.4) covers staff-assisted web booking only; an "in progress"/
// "completed" state belongs to the not-yet-built mobile QR dispatch and Operator Mode finalize
// flow (spec sections 4.2/4.4), which is represented separately by BatterySlotStatus.Occupied.

namespace smart_solar_mgt_api.Models.Enums;

public enum ReservationStatus
{
    Confirmed,
    Cancelled
}
