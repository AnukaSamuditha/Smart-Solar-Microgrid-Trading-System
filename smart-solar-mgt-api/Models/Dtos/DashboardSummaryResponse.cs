// DashboardSummaryResponse.cs
// Purpose: Aggregated platform-status snapshot for the backoffice web app's dashboard landing
// page (KPI strip, battery-slot distribution widget). Built by DashboardService from live counts
// across MicrogridNodes/Reservations/Prosumers — nothing here is persisted. Staff account counts
// are deliberately not included (see DashboardService's file header) — the frontend's
// Backoffice-only staff KPI tile reuses the existing GET /api/v1/users/ endpoint instead.

namespace smart_solar_mgt_api.Models.Dtos;

public record DashboardSummaryResponse(
    NodeSummary Nodes,
    BatterySlotSummary BatterySlots,
    ReservationSummary Reservations,
    ProsumerSummary Prosumers);

public record NodeSummary(int ActiveCount, int TotalCount, double TotalCapacityKw);

public record BatterySlotSummary(int Available, int Reserved, int Occupied, int Total);

// today's reservation dispatch activity, bucketed by StartTime against the current instant —
// backs the KPI strip's "Today's Dispatch" tile
public record ReservationSummary(
    int TotalToday,
    int ActiveNowCount,
    int ScheduledTodayCount,
    int? PeakHour);

public record ProsumerSummary(int Invited, int Active, int Deactivated);
