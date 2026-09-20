// TransactionStatus.cs
// Purpose: Lifecycle states for a QR Energy Transfer Pass transaction (project-specification.md
// sections 4.2/4.4). Generated -> Scanned (a Grid Operator has verified the token online) ->
// Completed (the physical energy transfer is finalized, releasing the node's slot). No persisted
// Expired state - staleness is evaluated lazily against Transaction.ExpiresAt at scan time
// instead, matching ReservationStatus/BatterySlotStatus's existing no-background-jobs pattern in
// this codebase.

namespace smart_solar_mgt_api.Models.Enums;

public enum TransactionStatus
{
    Generated,
    Scanned,
    Completed
}
