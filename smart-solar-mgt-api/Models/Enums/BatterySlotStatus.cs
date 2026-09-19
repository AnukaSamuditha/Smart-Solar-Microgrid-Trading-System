// BatterySlotStatus.cs
// Purpose: Lifecycle states for a single battery storage slot embedded on a MicrogridNode.
// Reserved/Occupied are set aside for future Energy Slot Reservation Management (spec section
// 3.4) and Grid Operator battery-slot updates (spec section 6); only Available is used today.

namespace smart_solar_mgt_api.Models.Enums;

public enum BatterySlotStatus
{
    Available,
    Reserved,
    Occupied
}
