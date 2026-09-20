// RecentActivityResponse.cs
// Purpose: A merged, most-recent-first feed of recent node/prosumer/reservation creation events
// for the dashboard's activity widget. There is no dedicated audit-log entity in this codebase,
// so DashboardService assembles this by querying CreatedAt desc across a few collections and
// merging in memory (see its file header). Label is pre-formatted server-side so the frontend
// only needs to pick an icon per Type, not branch on entity-specific fields.

namespace smart_solar_mgt_api.Models.Dtos;

public record RecentActivityResponse(IReadOnlyList<ActivityItem> Items);

// Type is one of "NodeCreated", "ProsumerCreated", "ReservationCreated"
public record ActivityItem(string Type, string Label, DateTime CreatedAt);
