// ITransactionService.cs
// Purpose: Abstraction over QR Energy Transfer Pass generation, scan-verification, and
// finalize/complete (project-specification.md sections 4.2/4.4). Implemented by
// TransactionService.

using smart_solar_mgt_api.Models.Entities;

namespace smart_solar_mgt_api.Services.Transactions;

public interface ITransactionService
{
    Task<(GenerateTransactionResult Result, Transaction? Transaction, string? RawToken)> GenerateAsync(
        string reservationId,
        string prosumerNic,
        CancellationToken cancellationToken = default);

    Task<(ScanTransactionResult Result, EnrichedTransaction? Transaction)> ScanAsync(
        string rawToken,
        string operatorUserId,
        CancellationToken cancellationToken = default);

    Task<(CompleteTransactionResult Result, EnrichedTransaction? Transaction)> CompleteAsync(
        string transactionId,
        string operatorUserId,
        CancellationToken cancellationToken = default);
}

// a transaction paired with its resolved node name / prosumer full name and its linked
// reservation's start/end time + energy amount (see TransactionVerificationResponse.FromEnriched)
// - the operator's scan-confirmation screen needs these to visually match the physical prosumer/
// session in front of them, not just an id. Computed at read time only, never stored on the
// Transaction entity itself, matching Reservation's EnrichedReservation.
public record EnrichedTransaction(
    Transaction Transaction,
    string? NodeName,
    string? ProsumerFullName,
    DateTime? ReservationStartTime,
    DateTime? ReservationEndTime,
    double? EnergyAmount);

public enum GenerateTransactionResult
{
    Succeeded,
    ReservationNotFound,
    NotOwner,
    ReservationNotConfirmed,
    ReservationWindowElapsed
}

public enum ScanTransactionResult
{
    Succeeded,
    NotFound,
    AlreadyUsed,
    Expired,
    ReservationNoLongerConfirmed
}

public enum CompleteTransactionResult
{
    Succeeded,
    NotFound,
    NotScanned
}
