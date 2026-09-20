// TransactionVerificationResponse.cs
// Purpose: Response for POST /api/v1/transactions/scan and PATCH /api/v1/transactions/{id}/complete
// - display-ready details (enriched with node name / prosumer full name / the linked
// reservation's window and energy amount, mirroring ReservationResponse) for the Grid Operator's
// confirm-then-finalize screen, so the operator can visually match the physical prosumer/session
// in front of them, not just an id. Never includes the raw QR token (see TransactionResponse) -
// the operator's device already has the scanned value.

using smart_solar_mgt_api.Services.Transactions;

namespace smart_solar_mgt_api.Models.Dtos;

public record TransactionVerificationResponse(
    string Id,
    string ReservationId,
    string ProsumerNic,
    string? ProsumerFullName,
    string NodeId,
    string? NodeName,
    string SlotId,
    DateTime? StartTime,
    DateTime? EndTime,
    double? EnergyAmount,
    string Status,
    DateTime GeneratedAt,
    DateTime ExpiresAt)
{
    // project an EnrichedTransaction (transaction plus resolved node name / prosumer full name /
    // reservation window+energy amount) onto the public response shape
    public static TransactionVerificationResponse FromEnriched(EnrichedTransaction enriched) =>
        new(
            enriched.Transaction.Id,
            enriched.Transaction.ReservationId,
            enriched.Transaction.ProsumerNic,
            enriched.ProsumerFullName,
            enriched.Transaction.NodeId,
            enriched.NodeName,
            enriched.Transaction.SlotId,
            enriched.ReservationStartTime,
            enriched.ReservationEndTime,
            enriched.EnergyAmount,
            enriched.Transaction.Status.ToString(),
            enriched.Transaction.GeneratedAt,
            enriched.Transaction.ExpiresAt);
}
