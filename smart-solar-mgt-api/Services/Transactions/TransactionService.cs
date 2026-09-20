// TransactionService.cs
// Purpose: Orchestrates QR Energy Transfer Pass generation, scan-verification, and finalize/
// complete (project-specification.md sections 4.2/4.4) - the server-side replacement for the
// mobile app's original 100%-on-device HMAC-signed pass. A token is opaque, cryptographically
// random (Services.Auth.SecureTokenGenerator, the same generator behind refresh/invitation
// tokens) - never an HMAC signature - because verification is always an authoritative online
// round-trip to this service, not an offline signature check. Only the token's SHA-256 hash is
// ever persisted; GenerateAsync's response is the only place the raw value is ever returned,
// exactly like InvitationService.CreateInvitationAsync. Scan and Complete each atomically claim
// the transaction via FindOneAndUpdate filtered on its current status (single-use, the same
// pattern InvitationService.AcceptAsync uses for UsedAt) rather than a check-then-write pair, so
// two concurrent scans of the same pass can never both succeed.
//
// No Mongo transactions exist anywhere in this codebase, so GenerateAsync/ScanAsync/CompleteAsync
// each write at most one authoritative document (the Transaction) plus a best-effort node
// battery-slot cache update, mirroring ReservationService's own write-safety approach. There is
// no persisted Expired state and no background job to release a stuck Scanned transaction that's
// never completed - the same accepted, no-background-jobs limitation ReservationService's
// BatterySlots cache already carries.

using MongoDB.Driver;
using smart_solar_mgt_api.Data;
using smart_solar_mgt_api.Models.Entities;
using smart_solar_mgt_api.Models.Enums;
using smart_solar_mgt_api.Services.Auth;

namespace smart_solar_mgt_api.Services.Transactions;

public class TransactionService : ITransactionService
{
    // how much longer past a reservation's own EndTime an unscanned pass stays valid, to tolerate
    // a prosumer arriving slightly late; purely a validity window, not a stored/self-expiring flag
    private static readonly TimeSpan GenerateGracePeriod = TimeSpan.FromMinutes(15);

    private readonly MongoContext _mongoContext;

    public TransactionService(MongoContext mongoContext)
    {
        _mongoContext = mongoContext;
    }

    // prosumer self-service: mint a fresh QR pass for one of the caller's own Confirmed
    // reservations. Always mints a brand-new Transaction (no idempotent "return the existing
    // one" - the raw token can't be recovered from a stored hash, matching
    // InvitationService.CreateInvitationAsync's own always-fresh behavior); any previously-
    // generated, still-unscanned pass for the same reservation is simply superseded - it's still
    // technically scannable until it expires, but ScanAsync's ReservationNoLongerConfirmed guard
    // (via CompleteAsync flipping the reservation to Completed) is what actually prevents a
    // second pass from redeeming the same reservation twice, not this method
    public async Task<(GenerateTransactionResult Result, Transaction? Transaction, string? RawToken)> GenerateAsync(
        string reservationId, string prosumerNic, CancellationToken cancellationToken = default)
    {
        var reservation = await _mongoContext.Reservations.Find(r => r.Id == reservationId).FirstOrDefaultAsync(cancellationToken);
        if (reservation is null)
        {
            return (GenerateTransactionResult.ReservationNotFound, null, null);
        }

        if (reservation.ProsumerNic != NormalizeNic(prosumerNic))
        {
            return (GenerateTransactionResult.NotOwner, null, null);
        }

        if (reservation.Status != ReservationStatus.Confirmed)
        {
            return (GenerateTransactionResult.ReservationNotConfirmed, null, null);
        }

        if (reservation.EndTime <= DateTime.UtcNow)
        {
            return (GenerateTransactionResult.ReservationWindowElapsed, null, null);
        }

        var rawToken = SecureTokenGenerator.GenerateToken();
        var transaction = new Transaction
        {
            ReservationId = reservation.Id,
            ProsumerNic = reservation.ProsumerNic,
            NodeId = reservation.NodeId,
            SlotId = reservation.SlotId,
            TokenHash = SecureTokenGenerator.Hash(rawToken),
            Status = TransactionStatus.Generated,
            GeneratedAt = DateTime.UtcNow,
            ExpiresAt = reservation.EndTime.Add(GenerateGracePeriod)
        };
        await _mongoContext.Transactions.InsertOneAsync(transaction, cancellationToken: cancellationToken);

        return (GenerateTransactionResult.Succeeded, transaction, rawToken);
    }

    // Grid Operator scans a pass: atomically claims it (Generated -> Scanned) so a concurrent
    // second scan of the same token can never also succeed, then flips the node's cached slot to
    // Occupied - the first real use of BatterySlotStatus.Occupied, matching its own header
    // comment's stated purpose
    public async Task<(ScanTransactionResult Result, EnrichedTransaction? Transaction)> ScanAsync(
        string rawToken, string operatorUserId, CancellationToken cancellationToken = default)
    {
        var tokenHash = SecureTokenGenerator.Hash(rawToken);

        var filter = Builders<Transaction>.Filter.Eq(t => t.TokenHash, tokenHash) &
                     Builders<Transaction>.Filter.Eq(t => t.Status, TransactionStatus.Generated);
        var update = Builders<Transaction>.Update
            .Set(t => t.Status, TransactionStatus.Scanned)
            .Set(t => t.ScannedByUserId, operatorUserId)
            .Set(t => t.ScannedAt, DateTime.UtcNow);

        // FindOneAndUpdate returns the PRE-update document by default, so an already-expired
        // token is still consumed by this attempt - identical to how AcceptAsync consumes an
        // expired invitation on a failed accept
        var transaction = await _mongoContext.Transactions.FindOneAndUpdateAsync(filter, update, cancellationToken: cancellationToken);
        if (transaction is null)
        {
            var exists = await _mongoContext.Transactions.Find(t => t.TokenHash == tokenHash).AnyAsync(cancellationToken);
            return (exists ? ScanTransactionResult.AlreadyUsed : ScanTransactionResult.NotFound, null);
        }

        if (transaction.ExpiresAt < DateTime.UtcNow)
        {
            return (ScanTransactionResult.Expired, null);
        }

        // defense in depth: the underlying reservation may have been cancelled, or already
        // redeemed by a different pass generated earlier for the same reservation, since this
        // pass was issued - re-verify it's still Confirmed before letting the physical handshake proceed
        var reservation = await _mongoContext.Reservations.Find(r => r.Id == transaction.ReservationId).FirstOrDefaultAsync(cancellationToken);
        if (reservation is null || reservation.Status != ReservationStatus.Confirmed)
        {
            return (ScanTransactionResult.ReservationNoLongerConfirmed, null);
        }

        await SetNodeSlotStatusAsync(transaction.NodeId, transaction.SlotId, BatterySlotStatus.Occupied, cancellationToken);

        transaction.Status = TransactionStatus.Scanned;
        transaction.ScannedByUserId = operatorUserId;
        transaction.ScannedAt = DateTime.UtcNow;

        return (ScanTransactionResult.Succeeded, await EnrichAsync(transaction, cancellationToken));
    }

    // Grid Operator finalizes a previously-scanned pass: atomically claims it (Scanned ->
    // Completed), marks the linked reservation Completed, and releases the node's slot back to
    // Available. Deliberately does not re-check ExpiresAt here - Scan already succeeded while the
    // pass was valid, and the physical energy transfer it authorized may reasonably take longer
    // than the grace window; refusing to complete an already-Scanned pass would leave the slot
    // stuck Occupied forever, since this codebase has no background job to release it
    public async Task<(CompleteTransactionResult Result, EnrichedTransaction? Transaction)> CompleteAsync(
        string transactionId, string operatorUserId, CancellationToken cancellationToken = default)
    {
        var filter = Builders<Transaction>.Filter.Eq(t => t.Id, transactionId) &
                     Builders<Transaction>.Filter.Eq(t => t.Status, TransactionStatus.Scanned);
        var update = Builders<Transaction>.Update
            .Set(t => t.Status, TransactionStatus.Completed)
            .Set(t => t.CompletedByUserId, operatorUserId)
            .Set(t => t.CompletedAt, DateTime.UtcNow);

        var transaction = await _mongoContext.Transactions.FindOneAndUpdateAsync(filter, update, cancellationToken: cancellationToken);
        if (transaction is null)
        {
            var exists = await _mongoContext.Transactions.Find(t => t.Id == transactionId).AnyAsync(cancellationToken);
            return (exists ? CompleteTransactionResult.NotScanned : CompleteTransactionResult.NotFound, null);
        }

        var reservationFilter = Builders<Reservation>.Filter.Eq(r => r.Id, transaction.ReservationId);
        var reservationUpdate = Builders<Reservation>.Update
            .Set(r => r.Status, ReservationStatus.Completed)
            .Set(r => r.UpdatedAt, DateTime.UtcNow)
            .Set(r => r.UpdatedBy, operatorUserId);
        await _mongoContext.Reservations.UpdateOneAsync(reservationFilter, reservationUpdate, cancellationToken: cancellationToken);

        await SetNodeSlotStatusAsync(transaction.NodeId, transaction.SlotId, BatterySlotStatus.Available, cancellationToken);

        transaction.Status = TransactionStatus.Completed;
        transaction.CompletedByUserId = operatorUserId;
        transaction.CompletedAt = DateTime.UtcNow;

        return (CompleteTransactionResult.Succeeded, await EnrichAsync(transaction, cancellationToken));
    }

    // best-effort update of a node's cached battery-slot status; not the source of truth for
    // anything (see file header), so a lost race here never breaks transaction correctness -
    // mirrors ReservationService.SetNodeSlotStatusAsync exactly
    private async Task SetNodeSlotStatusAsync(string nodeId, string slotId, BatterySlotStatus status, CancellationToken cancellationToken)
    {
        var filter = Builders<MicrogridNode>.Filter.And(
            Builders<MicrogridNode>.Filter.Eq(n => n.Id, nodeId),
            Builders<MicrogridNode>.Filter.ElemMatch(n => n.BatterySlots, s => s.SlotId == slotId));
        var update = Builders<MicrogridNode>.Update.Set("BatterySlots.$.Status", status);
        await _mongoContext.MicrogridNodes.UpdateOneAsync(filter, update, cancellationToken: cancellationToken);
    }

    // resolve a transaction's node name, prosumer full name, and linked reservation's window/
    // energy amount via three lookups rather than storing them on the entity - mirrors
    // ReservationService.EnrichAsync, single-item shaped since only Scan/Complete's
    // one-transaction responses need this, unlike Reservation's list views
    private async Task<EnrichedTransaction> EnrichAsync(Transaction transaction, CancellationToken cancellationToken)
    {
        var node = await _mongoContext.MicrogridNodes.Find(n => n.Id == transaction.NodeId).FirstOrDefaultAsync(cancellationToken);
        var prosumer = await _mongoContext.Prosumers.Find(p => p.Nic == transaction.ProsumerNic).FirstOrDefaultAsync(cancellationToken);
        var reservation = await _mongoContext.Reservations.Find(r => r.Id == transaction.ReservationId).FirstOrDefaultAsync(cancellationToken);
        return new EnrichedTransaction(
            transaction, node?.Name, prosumer?.FullName,
            reservation?.StartTime, reservation?.EndTime, reservation?.EnergyAmount);
    }

    // trim and uppercase a NIC so lookups match ProsumerService/ReservationService's normalization exactly
    private static string NormalizeNic(string nic) => nic.Trim().ToUpperInvariant();
}
