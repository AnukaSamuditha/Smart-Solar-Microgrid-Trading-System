// MicrogridNodeService.cs
// Purpose: Orchestrates microgrid node (solar grid hub) creation, search/pagination, schedule
// updates, battery-slot status updates, and lifecycle transitions (deactivate/reactivate).
// Deactivation is blocked while active energy reservations exist against the node — see
// IReservationLookupService for the (currently stubbed) dependency on Reservation Management.

using System.Linq.Expressions;
using System.Text.RegularExpressions;
using MongoDB.Bson;
using MongoDB.Driver;
using MongoDB.Driver.GeoJsonObjectModel;
using smart_solar_mgt_api.Data;
using smart_solar_mgt_api.Models.Entities;
using smart_solar_mgt_api.Models.Enums;
using smart_solar_mgt_api.Services.Reservations;

namespace smart_solar_mgt_api.Services.Nodes;

public class MicrogridNodeService : IMicrogridNodeService
{
    private const int MaxPageSize = 100;

    private readonly MongoContext _mongoContext;
    private readonly IReservationLookupService _reservationLookupService;

    public MicrogridNodeService(MongoContext mongoContext, IReservationLookupService reservationLookupService)
    {
        _mongoContext = mongoContext;
        _reservationLookupService = reservationLookupService;
    }

    // create a new Active node with the requested capacity, a sequentially-numbered all-Available
    // battery slot list, and an initial weekly schedule applying the same operating window to
    // every day (refinable per-day afterward via UpdateScheduleAsync)
    public async Task<MicrogridNode> CreateAsync(
        string name,
        double latitude,
        double longitude,
        double capacityKw,
        int batterySlotCount,
        TimeOnly operatingStartTime,
        TimeOnly operatingEndTime,
        string createdByUserId,
        CancellationToken cancellationToken = default)
    {
        var node = new MicrogridNode
        {
            Name = name.Trim(),
            Location = new GeoJsonPoint<GeoJson2DGeographicCoordinates>(new GeoJson2DGeographicCoordinates(longitude, latitude)),
            CapacityKw = capacityKw,
            BatterySlots = BuildBatterySlots(batterySlotCount),
            Schedule = BuildInitialSchedule(operatingStartTime, operatingEndTime),
            Status = MicrogridNodeStatus.Active,
            CreatedAt = DateTime.UtcNow,
            CreatedBy = createdByUserId
        };

        await _mongoContext.MicrogridNodes.InsertOneAsync(node, cancellationToken: cancellationToken);
        return node;
    }

    // look up a node by id, used by update/deactivate/reactivate and the get-by-id endpoint
    public async Task<MicrogridNode?> GetByIdAsync(string id, CancellationToken cancellationToken = default) =>
        await _mongoContext.MicrogridNodes.Find(n => n.Id == id).FirstOrDefaultAsync(cancellationToken);

    // search/filter/sort/paginate nodes for the admin listing endpoint
    public async Task<(IReadOnlyList<MicrogridNode> Items, long TotalCount)> ListAsync(
        string? search,
        MicrogridNodeStatus? status,
        int page,
        int pageSize,
        string? sortBy,
        string? sortDir,
        CancellationToken cancellationToken = default)
    {
        var normalizedPage = Math.Max(page, 1);
        var normalizedPageSize = Math.Clamp(pageSize <= 0 ? 20 : pageSize, 1, MaxPageSize);

        var filterBuilder = Builders<MicrogridNode>.Filter;
        var filters = new List<FilterDefinition<MicrogridNode>>();

        if (!string.IsNullOrWhiteSpace(search))
        {
            var pattern = new BsonRegularExpression(Regex.Escape(search.Trim()), "i");
            filters.Add(filterBuilder.Regex(n => n.Name, pattern));
        }

        if (status is not null)
        {
            filters.Add(filterBuilder.Eq(n => n.Status, status.Value));
        }

        var filter = filters.Count == 0 ? FilterDefinition<MicrogridNode>.Empty : filterBuilder.And(filters);

        var sort = BuildSort(sortBy, sortDir);

        var totalCount = await _mongoContext.MicrogridNodes.CountDocumentsAsync(filter, cancellationToken: cancellationToken);
        var items = await _mongoContext.MicrogridNodes.Find(filter)
            .Sort(sort)
            .Skip((normalizedPage - 1) * normalizedPageSize)
            .Limit(normalizedPageSize)
            .ToListAsync(cancellationToken);

        return (items, totalCount);
    }

    // replace a node's entire weekly schedule
    public async Task<NodeActionResult> UpdateScheduleAsync(
        string id,
        IReadOnlyList<ScheduleEntry> schedule,
        string performedByUserId,
        CancellationToken cancellationToken = default)
    {
        var node = await GetByIdAsync(id, cancellationToken);
        if (node is null)
        {
            return NodeActionResult.NotFound;
        }

        var filter = Builders<MicrogridNode>.Filter.Eq(n => n.Id, node.Id);
        var update = Builders<MicrogridNode>.Update
            .Set(n => n.Schedule, schedule.ToList())
            .Set(n => n.UpdatedAt, DateTime.UtcNow)
            .Set(n => n.UpdatedBy, performedByUserId);
        await _mongoContext.MicrogridNodes.UpdateOneAsync(filter, update, cancellationToken: cancellationToken);

        return NodeActionResult.Succeeded;
    }

    // update a single battery slot's status; refuses if the slot id doesn't exist on the node
    public async Task<NodeActionResult> UpdateBatterySlotStatusAsync(
        string id,
        string slotId,
        BatterySlotStatus status,
        string performedByUserId,
        CancellationToken cancellationToken = default)
    {
        var node = await GetByIdAsync(id, cancellationToken);
        if (node is null)
        {
            return NodeActionResult.NotFound;
        }

        if (node.BatterySlots.All(s => s.SlotId != slotId))
        {
            return NodeActionResult.SlotNotFound;
        }

        var filter = Builders<MicrogridNode>.Filter.And(
            Builders<MicrogridNode>.Filter.Eq(n => n.Id, node.Id),
            Builders<MicrogridNode>.Filter.ElemMatch(n => n.BatterySlots, s => s.SlotId == slotId));
        var update = Builders<MicrogridNode>.Update
            .Set("BatterySlots.$.Status", status)
            .Set(n => n.UpdatedAt, DateTime.UtcNow)
            .Set(n => n.UpdatedBy, performedByUserId);
        await _mongoContext.MicrogridNodes.UpdateOneAsync(filter, update, cancellationToken: cancellationToken);

        return NodeActionResult.Succeeded;
    }

    // deactivate a node, blocked while IReservationLookupService reports active reservations against it
    public async Task<NodeActionResult> DeactivateAsync(string id, string performedByUserId, CancellationToken cancellationToken = default)
    {
        var node = await GetByIdAsync(id, cancellationToken);
        if (node is null)
        {
            return NodeActionResult.NotFound;
        }

        if (await _reservationLookupService.HasActiveReservationsAsync(node.Id, cancellationToken))
        {
            return NodeActionResult.ActiveReservationsExist;
        }

        return await SetStatusAsync(node, MicrogridNodeStatus.Deactivated, performedByUserId, cancellationToken);
    }

    // reactivate a previously deactivated node (Backoffice-only, enforced at the endpoint)
    public async Task<NodeActionResult> ReactivateAsync(string id, string performedByUserId, CancellationToken cancellationToken = default)
    {
        var node = await GetByIdAsync(id, cancellationToken);
        if (node is null)
        {
            return NodeActionResult.NotFound;
        }

        return await SetStatusAsync(node, MicrogridNodeStatus.Active, performedByUserId, cancellationToken);
    }

    // shared lifecycle-transition logic used by DeactivateAsync and ReactivateAsync
    private async Task<NodeActionResult> SetStatusAsync(
        MicrogridNode node,
        MicrogridNodeStatus newStatus,
        string performedByUserId,
        CancellationToken cancellationToken)
    {
        var filter = Builders<MicrogridNode>.Filter.Eq(n => n.Id, node.Id);
        var update = Builders<MicrogridNode>.Update
            .Set(n => n.Status, newStatus)
            .Set(n => n.UpdatedAt, DateTime.UtcNow)
            .Set(n => n.UpdatedBy, performedByUserId);
        await _mongoContext.MicrogridNodes.UpdateOneAsync(filter, update, cancellationToken: cancellationToken);

        return NodeActionResult.Succeeded;
    }

    // generate SlotId "1".."count", all starting as Available
    private static List<BatterySlot> BuildBatterySlots(int count) =>
        Enumerable.Range(1, Math.Max(count, 0))
            .Select(i => new BatterySlot { SlotId = i.ToString(), Status = BatterySlotStatus.Available })
            .ToList();

    // seed every day of the week with the same operating window; ValidateCreateRequest already
    // guarantees startTime < endTime before this is called
    private static List<ScheduleEntry> BuildInitialSchedule(TimeOnly startTime, TimeOnly endTime) =>
        Enum.GetValues<DayOfWeek>()
            .Select(day => new ScheduleEntry
            {
                DayOfWeek = day,
                OpenTime = startTime,
                CloseTime = endTime,
                IsClosed = false
            })
            .ToList();

    // translate the requested sort field/direction into a Mongo sort definition, defaulting to newest-first
    private static SortDefinition<MicrogridNode> BuildSort(string? sortBy, string? sortDir)
    {
        var descending = !string.Equals(sortDir, "asc", StringComparison.OrdinalIgnoreCase);

        Expression<Func<MicrogridNode, object>> field = sortBy?.ToLowerInvariant() switch
        {
            "name" => n => n.Name,
            "capacitykw" => n => n.CapacityKw,
            _ => n => n.CreatedAt
        };

        return descending
            ? Builders<MicrogridNode>.Sort.Descending(field)
            : Builders<MicrogridNode>.Sort.Ascending(field);
    }
}
