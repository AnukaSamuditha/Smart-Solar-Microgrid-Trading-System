// ReservationService.cs
// Purpose: Orchestrates energy slot reservation creation, search/pagination, rescheduling, and
// cancellation (project-specification.md section 3.4), plus a prosumer self-service request/
// review workflow (RequestAsync/ApproveAsync/RejectAsync) alongside the original staff-assisted
// path (CreateAsync). A battery slot holds at most one active (Confirmed, not-yet-elapsed)
// reservation at a time; the Reservations collection itself is the source of truth for that rule,
// not the embedded MicrogridNode.BatterySlots[].Status flag, since that flag can already be
// edited directly and independently via MicrogridNodeService.UpdateBatterySlotStatusAsync. This
// service still flips that flag as a best-effort display cache for the existing battery-slots UI
// (Reserved once a Confirmed reservation exists - immediately on staff CreateAsync, or only once
// ApproveAsync confirms a prosumer's Pending request - and back to Available on cancel), but
// never reads it to decide whether a slot can be booked. No Mongo transactions are used anywhere
// in this codebase, so CreateAsync uses an insert-then-verify sequence instead of claim-then-
// insert to keep the one piece of shared, always-visible state (the node's BatterySlots array)
// safe from a partial-write failure mode; ApproveAsync uses the equivalent check-then-confirm
// sequence for the same reason.

using System.Linq.Expressions;
using MongoDB.Driver;
using smart_solar_mgt_api.Data;
using smart_solar_mgt_api.Models.Entities;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Services.Reservations;

public class ReservationService : IReservationService
{
    private const int MaxPageSize = 100;
    private static readonly TimeSpan MinimumModificationNotice = TimeSpan.FromHours(12);

    private readonly MongoContext _mongoContext;

    public ReservationService(MongoContext mongoContext)
    {
        _mongoContext = mongoContext;
    }

    // create a Confirmed reservation for a prosumer against a specific node's battery slot;
    // validates the prosumer/node exist and are Active, the slot exists on the node, and the slot
    // has no other active reservation, then inserts and re-verifies before returning
    public async Task<(CreateReservationResult Result, EnrichedReservation? Reservation)> CreateAsync(
        string prosumerNic,
        string nodeId,
        string slotId,
        DateTime startTime,
        DateTime endTime,
        string performedByUserId,
        CancellationToken cancellationToken = default)
    {
        var normalizedNic = NormalizeNic(prosumerNic);

        var (validationResult, prosumer, node) = await ValidateBookingSubjectsAsync(normalizedNic, nodeId, slotId, cancellationToken);
        if (validationResult != CreateReservationResult.Succeeded)
        {
            return (validationResult, null);
        }

        if (await HasConflictingReservationAsync(nodeId, slotId, excludeId: null, cancellationToken))
        {
            return (CreateReservationResult.SlotNotAvailable, null);
        }

        var reservation = new Reservation
        {
            ProsumerNic = normalizedNic,
            NodeId = nodeId,
            SlotId = slotId,
            StartTime = startTime,
            EndTime = endTime,
            Status = ReservationStatus.Confirmed,
            CreatedAt = DateTime.UtcNow,
            CreatedBy = performedByUserId
        };
        await _mongoContext.Reservations.InsertOneAsync(reservation, cancellationToken: cancellationToken);

        // a concurrent request may have won the race between the pre-check above and this insert;
        // re-check excluding this reservation's own id and roll back if another one claimed the slot first
        if (await HasConflictingReservationAsync(nodeId, slotId, excludeId: reservation.Id, cancellationToken))
        {
            await _mongoContext.Reservations.DeleteOneAsync(r => r.Id == reservation.Id, cancellationToken);
            return (CreateReservationResult.SlotNotAvailable, null);
        }

        await SetNodeSlotStatusAsync(nodeId, slotId, BatterySlotStatus.Reserved, cancellationToken);

        return (CreateReservationResult.Succeeded, new EnrichedReservation(reservation, node!.Name, prosumer!.FullName));
    }

    // prosumer self-service: create a Pending reservation request. Reuses the same subject
    // validation and pre-check as CreateAsync, but never marks the slot Reserved on the node's
    // cached BatterySlots - that only happens once ApproveAsync confirms the request, so a
    // request that ends up Rejected never wrongly showed the slot as taken in the meantime.
    // HasConflictingReservationAsync only ever counts Confirmed reservations, so multiple
    // prosumers may have concurrently-Pending requests for the same slot - ApproveAsync resolves
    // that by re-checking immediately before confirming.
    public async Task<(CreateReservationResult Result, EnrichedReservation? Reservation)> RequestAsync(
        string prosumerNic,
        string nodeId,
        string slotId,
        DateTime startTime,
        DateTime endTime,
        double? energyAmount,
        CancellationToken cancellationToken = default)
    {
        var normalizedNic = NormalizeNic(prosumerNic);

        var (validationResult, prosumer, node) = await ValidateBookingSubjectsAsync(normalizedNic, nodeId, slotId, cancellationToken);
        if (validationResult != CreateReservationResult.Succeeded)
        {
            return (validationResult, null);
        }

        if (await HasConflictingReservationAsync(nodeId, slotId, excludeId: null, cancellationToken))
        {
            return (CreateReservationResult.SlotNotAvailable, null);
        }

        var reservation = new Reservation
        {
            ProsumerNic = normalizedNic,
            NodeId = nodeId,
            SlotId = slotId,
            StartTime = startTime,
            EndTime = endTime,
            EnergyAmount = energyAmount,
            Status = ReservationStatus.Pending,
            CreatedAt = DateTime.UtcNow,
            CreatedBy = normalizedNic
        };
        await _mongoContext.Reservations.InsertOneAsync(reservation, cancellationToken: cancellationToken);

        return (CreateReservationResult.Succeeded, new EnrichedReservation(reservation, node!.Name, prosumer!.FullName));
    }

    // shared prosumer/node/slot validation for CreateAsync and RequestAsync: prosumer exists and
    // is Active, node exists and is Active, the slot exists on that node
    private async Task<(CreateReservationResult Result, Prosumer? Prosumer, MicrogridNode? Node)> ValidateBookingSubjectsAsync(
        string normalizedNic, string nodeId, string slotId, CancellationToken cancellationToken)
    {
        var prosumer = await _mongoContext.Prosumers.Find(p => p.Nic == normalizedNic).FirstOrDefaultAsync(cancellationToken);
        if (prosumer is null)
        {
            return (CreateReservationResult.ProsumerNotFound, null, null);
        }

        if (prosumer.Status != ProsumerStatus.Active)
        {
            return (CreateReservationResult.ProsumerDeactivated, null, null);
        }

        var node = await _mongoContext.MicrogridNodes.Find(n => n.Id == nodeId).FirstOrDefaultAsync(cancellationToken);
        if (node is null)
        {
            return (CreateReservationResult.NodeNotFound, null, null);
        }

        if (node.Status != MicrogridNodeStatus.Active)
        {
            return (CreateReservationResult.NodeDeactivated, null, null);
        }

        if (node.BatterySlots.All(s => s.SlotId != slotId))
        {
            return (CreateReservationResult.SlotNotFound, null, null);
        }

        return (CreateReservationResult.Succeeded, prosumer, node);
    }

    // look up a single reservation by id, enriched with its node name / prosumer full name
    public async Task<EnrichedReservation?> GetByIdAsync(string id, CancellationToken cancellationToken = default)
    {
        var reservation = await _mongoContext.Reservations.Find(r => r.Id == id).FirstOrDefaultAsync(cancellationToken);
        if (reservation is null)
        {
            return null;
        }

        var enriched = await EnrichAsync([reservation], cancellationToken);
        return enriched[0];
    }

    // prosumer self-service: fetch a reservation only if it belongs to the calling prosumer -
    // an ownership mismatch returns null, identical to a nonexistent id, so a prosumer can never
    // learn another prosumer's reservation exists
    public async Task<EnrichedReservation?> GetByIdForProsumerAsync(
        string id, string prosumerNic, CancellationToken cancellationToken = default)
    {
        var reservation = await _mongoContext.Reservations.Find(r => r.Id == id).FirstOrDefaultAsync(cancellationToken);
        if (reservation is null || reservation.ProsumerNic != NormalizeNic(prosumerNic))
        {
            return null;
        }

        var enriched = await EnrichAsync([reservation], cancellationToken);
        return enriched[0];
    }

    // search/filter/sort/paginate reservations for the monitoring list endpoint
    public async Task<(IReadOnlyList<EnrichedReservation> Items, long TotalCount)> ListAsync(
        string? prosumerNic,
        string? nodeId,
        ReservationStatus? status,
        DateTime? dateFrom,
        DateTime? dateTo,
        int page,
        int pageSize,
        string? sortBy,
        string? sortDir,
        CancellationToken cancellationToken = default)
    {
        var normalizedPage = Math.Max(page, 1);
        var normalizedPageSize = Math.Clamp(pageSize <= 0 ? 20 : pageSize, 1, MaxPageSize);

        var filterBuilder = Builders<Reservation>.Filter;
        var filters = new List<FilterDefinition<Reservation>>();

        if (!string.IsNullOrWhiteSpace(prosumerNic))
        {
            filters.Add(filterBuilder.Eq(r => r.ProsumerNic, NormalizeNic(prosumerNic)));
        }

        if (!string.IsNullOrWhiteSpace(nodeId))
        {
            filters.Add(filterBuilder.Eq(r => r.NodeId, nodeId));
        }

        if (status is not null)
        {
            filters.Add(filterBuilder.Eq(r => r.Status, status.Value));
        }

        if (dateFrom is not null)
        {
            filters.Add(filterBuilder.Gte(r => r.StartTime, dateFrom.Value));
        }

        if (dateTo is not null)
        {
            filters.Add(filterBuilder.Lte(r => r.StartTime, dateTo.Value));
        }

        var filter = filters.Count == 0 ? FilterDefinition<Reservation>.Empty : filterBuilder.And(filters);
        var sort = BuildSort(sortBy, sortDir);

        var totalCount = await _mongoContext.Reservations.CountDocumentsAsync(filter, cancellationToken: cancellationToken);
        var items = await _mongoContext.Reservations.Find(filter)
            .Sort(sort)
            .Skip((normalizedPage - 1) * normalizedPageSize)
            .Limit(normalizedPageSize)
            .ToListAsync(cancellationToken);

        var enriched = await EnrichAsync(items, cancellationToken);
        return (enriched, totalCount);
    }

    // reschedule a reservation's time window; the node/slot stay fixed, so no slot-conflict
    // re-check is needed (the slot is already exclusively held by this reservation)
    public async Task<ReservationActionResult> UpdateAsync(
        string id,
        DateTime startTime,
        DateTime endTime,
        string performedByUserId,
        CancellationToken cancellationToken = default)
    {
        var reservation = await _mongoContext.Reservations.Find(r => r.Id == id).FirstOrDefaultAsync(cancellationToken);
        if (reservation is null)
        {
            return ReservationActionResult.NotFound;
        }

        return await UpdateCoreAsync(reservation, startTime, endTime, performedByUserId, cancellationToken);
    }

    // cancel a reservation and release its slot's best-effort cached status back to Available
    public async Task<ReservationActionResult> CancelAsync(
        string id,
        string performedByUserId,
        CancellationToken cancellationToken = default)
    {
        var reservation = await _mongoContext.Reservations.Find(r => r.Id == id).FirstOrDefaultAsync(cancellationToken);
        if (reservation is null)
        {
            return ReservationActionResult.NotFound;
        }

        return await CancelCoreAsync(reservation, performedByUserId, cancellationToken);
    }

    // prosumer self-service: reschedule own reservation only - an ownership mismatch returns
    // NotFound, identical to a nonexistent id
    public async Task<ReservationActionResult> UpdateForProsumerAsync(
        string id,
        string prosumerNic,
        DateTime startTime,
        DateTime endTime,
        CancellationToken cancellationToken = default)
    {
        var reservation = await _mongoContext.Reservations.Find(r => r.Id == id).FirstOrDefaultAsync(cancellationToken);
        if (reservation is null || reservation.ProsumerNic != NormalizeNic(prosumerNic))
        {
            return ReservationActionResult.NotFound;
        }

        return await UpdateCoreAsync(reservation, startTime, endTime, reservation.ProsumerNic, cancellationToken);
    }

    // prosumer self-service: cancel own reservation only - an ownership mismatch returns
    // NotFound, identical to a nonexistent id
    public async Task<ReservationActionResult> CancelForProsumerAsync(
        string id,
        string prosumerNic,
        CancellationToken cancellationToken = default)
    {
        var reservation = await _mongoContext.Reservations.Find(r => r.Id == id).FirstOrDefaultAsync(cancellationToken);
        if (reservation is null || reservation.ProsumerNic != NormalizeNic(prosumerNic))
        {
            return ReservationActionResult.NotFound;
        }

        return await CancelCoreAsync(reservation, reservation.ProsumerNic, cancellationToken);
    }

    // approve a Pending request: re-checks the slot is still free (another reservation may have
    // been confirmed against it while this one sat Pending) immediately before confirming, then
    // marks the node's cached slot Reserved for the first time (RequestAsync deliberately skipped
    // this)
    public async Task<ReservationReviewResult> ApproveAsync(
        string id, string performedByUserId, CancellationToken cancellationToken = default)
    {
        var reservation = await _mongoContext.Reservations.Find(r => r.Id == id).FirstOrDefaultAsync(cancellationToken);
        if (reservation is null)
        {
            return ReservationReviewResult.NotFound;
        }

        if (reservation.Status != ReservationStatus.Pending)
        {
            return ReservationReviewResult.NotPending;
        }

        if (await HasConflictingReservationAsync(reservation.NodeId, reservation.SlotId, excludeId: reservation.Id, cancellationToken))
        {
            return ReservationReviewResult.SlotNoLongerAvailable;
        }

        var filter = Builders<Reservation>.Filter.Eq(r => r.Id, reservation.Id);
        var update = Builders<Reservation>.Update
            .Set(r => r.Status, ReservationStatus.Confirmed)
            .Set(r => r.ReviewedBy, performedByUserId)
            .Set(r => r.ReviewedAt, DateTime.UtcNow)
            .Set(r => r.UpdatedAt, DateTime.UtcNow)
            .Set(r => r.UpdatedBy, performedByUserId);
        await _mongoContext.Reservations.UpdateOneAsync(filter, update, cancellationToken: cancellationToken);

        await SetNodeSlotStatusAsync(reservation.NodeId, reservation.SlotId, BatterySlotStatus.Reserved, cancellationToken);

        return ReservationReviewResult.Succeeded;
    }

    // reject a Pending request; the slot was never marked Reserved by RequestAsync, so there's
    // nothing to release on the node's cached BatterySlots
    public async Task<ReservationReviewResult> RejectAsync(
        string id, string performedByUserId, string? reason, CancellationToken cancellationToken = default)
    {
        var reservation = await _mongoContext.Reservations.Find(r => r.Id == id).FirstOrDefaultAsync(cancellationToken);
        if (reservation is null)
        {
            return ReservationReviewResult.NotFound;
        }

        if (reservation.Status != ReservationStatus.Pending)
        {
            return ReservationReviewResult.NotPending;
        }

        var filter = Builders<Reservation>.Filter.Eq(r => r.Id, reservation.Id);
        var update = Builders<Reservation>.Update
            .Set(r => r.Status, ReservationStatus.Rejected)
            .Set(r => r.RejectionReason, string.IsNullOrWhiteSpace(reason) ? null : reason.Trim())
            .Set(r => r.ReviewedBy, performedByUserId)
            .Set(r => r.ReviewedAt, DateTime.UtcNow)
            .Set(r => r.UpdatedAt, DateTime.UtcNow)
            .Set(r => r.UpdatedBy, performedByUserId);
        await _mongoContext.Reservations.UpdateOneAsync(filter, update, cancellationToken: cancellationToken);

        return ReservationReviewResult.Succeeded;
    }

    // shared reschedule logic for UpdateAsync/UpdateForProsumerAsync, gated by CheckModifiable
    private async Task<ReservationActionResult> UpdateCoreAsync(
        Reservation reservation, DateTime startTime, DateTime endTime, string performedBy, CancellationToken cancellationToken)
    {
        var modifiableCheck = CheckModifiable(reservation);
        if (modifiableCheck != ReservationActionResult.Succeeded)
        {
            return modifiableCheck;
        }

        var filter = Builders<Reservation>.Filter.Eq(r => r.Id, reservation.Id);
        var update = Builders<Reservation>.Update
            .Set(r => r.StartTime, startTime)
            .Set(r => r.EndTime, endTime)
            .Set(r => r.UpdatedAt, DateTime.UtcNow)
            .Set(r => r.UpdatedBy, performedBy);
        await _mongoContext.Reservations.UpdateOneAsync(filter, update, cancellationToken: cancellationToken);

        return ReservationActionResult.Succeeded;
    }

    // shared cancel logic for CancelAsync/CancelForProsumerAsync, gated by CheckModifiable; only
    // releases the node's cached slot back to Available if this reservation had actually claimed
    // it (a still-Pending reservation never marked it Reserved in the first place)
    private async Task<ReservationActionResult> CancelCoreAsync(
        Reservation reservation, string performedBy, CancellationToken cancellationToken)
    {
        var modifiableCheck = CheckModifiable(reservation);
        if (modifiableCheck != ReservationActionResult.Succeeded)
        {
            return modifiableCheck;
        }

        var hadClaimedSlot = reservation.Status == ReservationStatus.Confirmed;

        var filter = Builders<Reservation>.Filter.Eq(r => r.Id, reservation.Id);
        var update = Builders<Reservation>.Update
            .Set(r => r.Status, ReservationStatus.Cancelled)
            .Set(r => r.UpdatedAt, DateTime.UtcNow)
            .Set(r => r.UpdatedBy, performedBy);
        await _mongoContext.Reservations.UpdateOneAsync(filter, update, cancellationToken: cancellationToken);

        if (hadClaimedSlot)
        {
            await SetNodeSlotStatusAsync(reservation.NodeId, reservation.SlotId, BatterySlotStatus.Available, cancellationToken);
        }

        return ReservationActionResult.Succeeded;
    }

    // shared update/cancel guard: refuses an already-cancelled or already-started reservation, or
    // one within the 12-hour notice window, evaluated against its current (pre-update) StartTime
    private static ReservationActionResult CheckModifiable(Reservation reservation)
    {
        // any terminal status (Cancelled/Rejected/Completed) can't be modified further; a still-
        // Pending reservation is always modifiable regardless of its StartTime distance below,
        // since it hasn't consumed a slot yet
        if (reservation.Status is ReservationStatus.Cancelled or ReservationStatus.Rejected or ReservationStatus.Completed)
        {
            return ReservationActionResult.AlreadyCancelled;
        }

        var utcNow = DateTime.UtcNow;
        if (reservation.StartTime <= utcNow)
        {
            return ReservationActionResult.AlreadyStarted;
        }

        if (reservation.StartTime - utcNow < MinimumModificationNotice)
        {
            return ReservationActionResult.InsufficientNotice;
        }

        return ReservationActionResult.Succeeded;
    }

    // true if the given node+slot has a Confirmed reservation whose window hasn't elapsed yet,
    // optionally excluding one reservation id (used by CreateAsync's post-insert re-check)
    private async Task<bool> HasConflictingReservationAsync(
        string nodeId, string slotId, string? excludeId, CancellationToken cancellationToken)
    {
        var filterBuilder = Builders<Reservation>.Filter;
        var filters = new List<FilterDefinition<Reservation>>
        {
            filterBuilder.Eq(r => r.NodeId, nodeId),
            filterBuilder.Eq(r => r.SlotId, slotId),
            filterBuilder.Eq(r => r.Status, ReservationStatus.Confirmed),
            filterBuilder.Gt(r => r.EndTime, DateTime.UtcNow)
        };

        if (excludeId is not null)
        {
            filters.Add(filterBuilder.Ne(r => r.Id, excludeId));
        }

        return await _mongoContext.Reservations.Find(filterBuilder.And(filters)).AnyAsync(cancellationToken);
    }

    // best-effort update of a node's cached battery-slot status; not the source of truth for
    // booking decisions (see file header), so a lost race here never breaks reservation correctness
    private async Task SetNodeSlotStatusAsync(string nodeId, string slotId, BatterySlotStatus status, CancellationToken cancellationToken)
    {
        var filter = Builders<MicrogridNode>.Filter.And(
            Builders<MicrogridNode>.Filter.Eq(n => n.Id, nodeId),
            Builders<MicrogridNode>.Filter.ElemMatch(n => n.BatterySlots, s => s.SlotId == slotId));
        var update = Builders<MicrogridNode>.Update.Set("BatterySlots.$.Status", status);
        await _mongoContext.MicrogridNodes.UpdateOneAsync(filter, update, cancellationToken: cancellationToken);
    }

    // batch-resolve each reservation's node name and prosumer full name via a couple of $in
    // lookups rather than storing them on the entity (see file header)
    private async Task<IReadOnlyList<EnrichedReservation>> EnrichAsync(
        IReadOnlyList<Reservation> reservations, CancellationToken cancellationToken)
    {
        var nodeIds = reservations.Select(r => r.NodeId).Distinct().ToList();
        var nics = reservations.Select(r => r.ProsumerNic).Distinct().ToList();

        var nodes = await _mongoContext.MicrogridNodes.Find(n => nodeIds.Contains(n.Id)).ToListAsync(cancellationToken);
        var prosumers = await _mongoContext.Prosumers.Find(p => nics.Contains(p.Nic)).ToListAsync(cancellationToken);

        var nodeNamesById = nodes.ToDictionary(n => n.Id, n => n.Name);
        var prosumerFullNamesByNic = prosumers.ToDictionary(p => p.Nic, p => p.FullName);

        return reservations
            .Select(r => new EnrichedReservation(
                r,
                nodeNamesById.GetValueOrDefault(r.NodeId),
                prosumerFullNamesByNic.GetValueOrDefault(r.ProsumerNic)))
            .ToList();
    }

    // translate the requested sort field/direction into a Mongo sort definition, defaulting to newest-first
    private static SortDefinition<Reservation> BuildSort(string? sortBy, string? sortDir)
    {
        var descending = !string.Equals(sortDir, "asc", StringComparison.OrdinalIgnoreCase);

        Expression<Func<Reservation, object>> field = sortBy?.ToLowerInvariant() switch
        {
            "starttime" => r => r.StartTime,
            _ => r => r.CreatedAt
        };

        return descending
            ? Builders<Reservation>.Sort.Descending(field)
            : Builders<Reservation>.Sort.Ascending(field);
    }

    // trim and uppercase a NIC so lookups match ProsumerService's normalization exactly
    private static string NormalizeNic(string nic) => nic.Trim().ToUpperInvariant();
}
