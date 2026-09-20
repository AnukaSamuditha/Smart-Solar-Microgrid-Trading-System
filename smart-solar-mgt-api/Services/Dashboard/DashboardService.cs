// DashboardService.cs
// Purpose: Aggregates cross-entity platform-status data for the backoffice web app's dashboard
// (see smart-solar-mgt-fe/docs/dashboard-insights-implementation-plan.md). The first service in
// this codebase to compute stats across multiple collections rather than per-list pagination
// totals. Deliberately uses simple Find/CountDocumentsAsync queries plus in-memory LINQ grouping
// (matching every other service in this codebase, e.g. ReservationService's EnrichAsync) rather
// than MongoDB aggregation pipelines: at this system's expected scale (tens of nodes, hundreds of
// reservations/prosumers) pulling a handful of small documents into memory is simpler to read and
// test than introducing this codebase's first Aggregate() pipeline for a marginal efficiency gain.
// Revisit with a real pipeline only if a collection here grows large enough for that to matter.

using MongoDB.Driver;
using smart_solar_mgt_api.Data;
using smart_solar_mgt_api.Models.Dtos;
using smart_solar_mgt_api.Models.Entities;
using smart_solar_mgt_api.Models.Enums;

namespace smart_solar_mgt_api.Services.Dashboard;

public class DashboardService : IDashboardService
{
    private const int MaxActivityLimit = 50;

    private readonly MongoContext _mongoContext;

    public DashboardService(MongoContext mongoContext)
    {
        _mongoContext = mongoContext;
    }

    // gather all counts/sums for the KPI strip and battery-slot distribution widget in parallel
    public async Task<DashboardSummaryResponse> GetSummaryAsync(CancellationToken cancellationToken = default)
    {
        var utcNow = DateTime.UtcNow;
        var todayStart = utcNow.Date;
        var todayEnd = todayStart.AddDays(1);

        var activeNodesTask = _mongoContext.MicrogridNodes
            .Find(n => n.Status == MicrogridNodeStatus.Active)
            .ToListAsync(cancellationToken);
        var totalNodeCountTask = _mongoContext.MicrogridNodes.CountDocumentsAsync(
            FilterDefinition<MicrogridNode>.Empty, cancellationToken: cancellationToken);
        var todaysReservationsTask = _mongoContext.Reservations
            .Find(r => r.Status == ReservationStatus.Confirmed && r.StartTime >= todayStart && r.StartTime < todayEnd)
            .ToListAsync(cancellationToken);
        var invitedProsumersTask = _mongoContext.Prosumers.CountDocumentsAsync(
            p => p.Status == ProsumerStatus.Invited, cancellationToken: cancellationToken);
        var activeProsumersTask = _mongoContext.Prosumers.CountDocumentsAsync(
            p => p.Status == ProsumerStatus.Active, cancellationToken: cancellationToken);
        var deactivatedProsumersTask = _mongoContext.Prosumers.CountDocumentsAsync(
            p => p.Status == ProsumerStatus.Deactivated, cancellationToken: cancellationToken);

        await Task.WhenAll(
            activeNodesTask, totalNodeCountTask, todaysReservationsTask,
            invitedProsumersTask, activeProsumersTask, deactivatedProsumersTask);

        var activeNodes = activeNodesTask.Result;
        var allSlots = activeNodes.SelectMany(n => n.BatterySlots).ToList();

        var nodeSummary = new NodeSummary(
            activeNodes.Count, (int)totalNodeCountTask.Result, activeNodes.Sum(n => n.CapacityKw));
        var batterySlotSummary = new BatterySlotSummary(
            allSlots.Count(s => s.Status == BatterySlotStatus.Available),
            allSlots.Count(s => s.Status == BatterySlotStatus.Reserved),
            allSlots.Count(s => s.Status == BatterySlotStatus.Occupied),
            allSlots.Count);

        var todaysReservations = todaysReservationsTask.Result;
        var activeNowCount = todaysReservations.Count(r => r.StartTime <= utcNow && r.EndTime > utcNow);
        var scheduledTodayCount = todaysReservations.Count(r => r.StartTime > utcNow);
        var peakHour = todaysReservations.Count == 0
            ? (int?)null
            : todaysReservations.GroupBy(r => r.StartTime.Hour).OrderByDescending(g => g.Count()).First().Key;
        var reservationSummary = new ReservationSummary(
            todaysReservations.Count, activeNowCount, scheduledTodayCount, peakHour);

        var prosumerSummary = new ProsumerSummary(
            (int)invitedProsumersTask.Result, (int)activeProsumersTask.Result, (int)deactivatedProsumersTask.Result);

        return new DashboardSummaryResponse(nodeSummary, batterySlotSummary, reservationSummary, prosumerSummary);
    }

    // merge the most recently created nodes, prosumers, and reservations into one timestamp-sorted
    // feed; three bounded queries total, not a query per rendered item
    public async Task<RecentActivityResponse> GetRecentActivityAsync(
        int limit = 10, CancellationToken cancellationToken = default)
    {
        var normalizedLimit = Math.Clamp(limit <= 0 ? 10 : limit, 1, MaxActivityLimit);

        var recentReservationsTask = _mongoContext.Reservations
            .Find(FilterDefinition<Reservation>.Empty)
            .SortByDescending(r => r.CreatedAt)
            .Limit(normalizedLimit)
            .ToListAsync(cancellationToken);
        var recentNodesTask = _mongoContext.MicrogridNodes
            .Find(FilterDefinition<MicrogridNode>.Empty)
            .SortByDescending(n => n.CreatedAt)
            .Limit(normalizedLimit)
            .ToListAsync(cancellationToken);
        var recentProsumersTask = _mongoContext.Prosumers
            .Find(FilterDefinition<Prosumer>.Empty)
            .SortByDescending(p => p.CreatedAt)
            .Limit(normalizedLimit)
            .ToListAsync(cancellationToken);

        await Task.WhenAll(recentReservationsTask, recentNodesTask, recentProsumersTask);

        var items = new List<ActivityItem>();
        items.AddRange(recentReservationsTask.Result.Select(r =>
            new ActivityItem("ReservationCreated", $"Reservation booked for slot {r.SlotId}", r.CreatedAt)));
        items.AddRange(recentNodesTask.Result.Select(n =>
            new ActivityItem("NodeCreated", $"Node \"{n.Name}\" registered", n.CreatedAt)));
        items.AddRange(recentProsumersTask.Result.Select(p =>
            new ActivityItem(
                "ProsumerCreated",
                $"Prosumer {(string.IsNullOrWhiteSpace(p.FullName) ? p.Nic : p.FullName)} invited",
                p.CreatedAt)));

        var merged = items
            .OrderByDescending(i => i.CreatedAt)
            .Take(normalizedLimit)
            .ToList();

        return new RecentActivityResponse(merged);
    }
}
