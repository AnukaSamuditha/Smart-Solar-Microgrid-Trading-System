// ReservationStatus.cs
// Purpose: Lifecycle states for an energy slot reservation. A staff-created reservation (see
// ReservationService.CreateAsync) still goes straight to Confirmed. A prosumer self-service
// request (ReservationService.RequestAsync) starts at Pending and needs a Backoffice/Grid
// Operator reviewer to Approve (-> Confirmed) or Reject (-> Rejected), mirroring
// ProsumerStatus.PendingApproval. Completed is set only by the QR transaction finalize flow
// (Services/Transactions/TransactionService.CompleteAsync) once the physical energy transfer is
// done - it is not reachable from Confirmed any other way.

namespace smart_solar_mgt_api.Models.Enums;

public enum ReservationStatus
{
    Pending,
    Confirmed,
    Rejected,
    Cancelled,
    Completed
}
