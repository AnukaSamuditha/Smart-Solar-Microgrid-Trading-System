# Dashboard Insights Feature — Implementation Plan

Status: Implemented. This document records the design for the backoffice web app's default
dashboard route (`/`) as built: a set of widgets giving Backoffice and Grid Operator users a
current-status snapshot of the platform. See `project-specification.md` and this project's
`CLAUDE.md` / the API's `CLAUDE.md` for the governing conventions.

## Context

The dashboard route (`app/(dashboard)/page.tsx`) previously rendered three empty `ContentPanel`
placeholders ("Grid Overview", "Active Reservations", "Battery Slot Availability"), each just
showing "Content coming soon". Every other area of the app (reservations, grid nodes, battery
slots, prosumers, users) is fully built, but there was no landing-page view summarizing overall
system status for the two web-app roles.

The backend has no energy-transaction, telemetry, or revenue entity — only `Reservation`,
`MicrogridNode` (with embedded `BatterySlot`/`ScheduleEntry`), `Prosumer`, and `User`. Every widget
below is grounded in those four entities; no metric here is invented.

The visual design went through two passes. The first built a generic widget set (KPI strip, a
Leaflet map, donut charts for prosumer/staff status, an "Upcoming Reservations" list). The second
— this one — was redesigned against a reference mockup the user supplied (a dense
"microgrid operations" command-center layout) and corrected per explicit feedback; see
**Visual design** and **Revision history** below for what changed and why.

## Widget catalog

| # | Widget | Roles | Data source | Backend work |
|---|---|---|---|---|
| 1 | KPI stat strip (Network Health, Storage Availability, Today's Dispatch, Prosumer Trading, +1 Backoffice-only Staff tile) | Both (+1 Backoffice-only tile) | active/total node count + total capacity kW; battery slots free/reserved/occupied; today's reservation dispatch (active now / scheduled / peak hour); prosumer status counts; (Backoffice: staff account count via `/users`) | `GET /api/v1/dashboard/summary` |
| 2 | Active Hub Nodes table (read-only) | Both | all Active nodes (name, status, location, capacity, battery slots, today's reservations, utilization) | none — reuses `GET /api/v1/nodes?status=Active&pageSize=100` + today's reservations (below) |
| 3 | Battery Slot Distribution (aggregated pool bar + per-node slot-block grid) | Both | platform-wide slot status breakdown; per-node `BatterySlot[]` | `GET /api/v1/dashboard/summary` (aggregate) + reuses the same active-nodes fetch as widget #2 |
| 4 | Hourly Dispatch & Reservation Load Profile (stacked bar chart, 06:00–21:00) | Both | today's Confirmed reservations, bucketed by `StartTime` hour and completed/active/scheduled | none — reuses `GET /api/v1/reservations?status=Confirmed&dateFrom=<today>&dateTo=<today>` |
| 5 | Chronological Activity Log | Both | latest creates across nodes/prosumers/reservations, merged by `CreatedAt` desc | `GET /api/v1/dashboard/recent-activity` |

**Removed from the first pass, per explicit correction:** a Leaflet node map (superseded by the
node table), a Prosumer Overview donut, a Backoffice-only Staff Overview panel, and an "Upcoming
Reservations" list. See **Revision history**.

## Visual design

Section layout/information-density was inspired by the reference mockup, not copied wholesale:

- **No "Needs Attention"/triage-queue section.** The mockup surfaces alerts (maintenance windows,
  high cell temperature, pending approvals) that would require an alerting/incident entity this
  backend doesn't have — inventing one is out of scope.
- **One unified view, not separate Backoffice/Grid Operator tabs.** Both roles see the same five
  widgets; the only role-conditional content is the KPI strip's Staff tile, which simply isn't
  rendered (and calls no `/users` request) for a Grid Operator.
- **The Active Hub Nodes table is read-only.** The first pass gave it a "Manage" button that
  opened `BatterySlotsSheet` — the same sheet the dedicated Battery Slots/Grid Nodes routes use to
  actually change slot state. That duplicated those routes' management behavior inside a
  dashboard widget that should only ever display information, so the row action was removed
  entirely; the widget has no click/button affordance of any kind.
- **Rounded corners throughout**, per this app's existing `shadcn` theme (`Card` already renders
  `rounded-xl`, `Badge` renders fully pill-shaped) — the mockup's sharp/square corners were
  explicitly not carried over. The battery-slot-distribution's per-slot blocks use `rounded-sm`
  for the same reason (a discrete-block visual without square corners).
- `font-mono`/`tabular-nums` for KPI figures and log timestamps, bordered-box KPI sub-metric
  chips, and colored type tags on the activity log rows were adapted from the mockup, all built
  with this app's existing color tokens (`--primary`, `--destructive`, `amber-500`, `blue-500`),
  never new ones.
- **The Hourly Dispatch chart deliberately does not include the mockup's "Max Node Capacity (kW)"
  reference line.** Reservations have no per-booking power/kW field in this domain (only a
  node's total `CapacityKw` exists) — plotting a kW ceiling against a count-based Y axis would
  either fabricate a kWh-per-reservation figure or mix units. The chart keeps a Y axis of
  reservation counts only, stacked by completed/active/scheduled, which is fully backed by real
  `StartTime`/`EndTime`/`Status` data.

## Decisions

- **`DashboardService` uses plain `Find`/`CountDocumentsAsync` queries plus in-memory LINQ
  grouping, not MongoDB aggregation pipelines.** This is the first cross-entity stats service in
  the codebase; every existing service (e.g. `ReservationService`) follows this same simple-query
  pattern. At this system's expected scale (tens of nodes, hundreds of reservations/prosumers),
  pulling a handful of small documents into memory to count/group them is simpler to read, test,
  and debug than introducing the codebase's first `Aggregate()` pipeline for a marginal efficiency
  gain.
- **`ReservationSummary` (part of `/dashboard/summary`) reports today's dispatch by `StartTime`**
  (`TotalToday`, `ActiveNowCount`, `ScheduledTodayCount`, `PeakHour`), not by `CreatedAt` — this is
  what the KPI strip's "Today's Dispatch" tile actually needs (today's booking *activity*, not
  when bookings were made). The Active Hub Nodes table and Hourly Dispatch chart need the raw
  per-reservation list (for per-node and per-hour breakdowns), which the summary's aggregate
  counts can't provide, so those two widgets separately call `GET /api/v1/reservations` with
  today's date range via the shared `useTodaysReservations()` hook — the KPI tile intentionally
  does not duplicate that fetch, since the pre-aggregated summary is cheaper for a single headline
  number.
- **`useTodaysReservations()` and `classifyReservation()` (`components/dashboard/overview/reservation-activity.ts`)
  are shared** between the Active Hub Nodes table's Reservations column and the Hourly Dispatch
  chart, so both widgets agree on what counts as completed (`EndTime <= now`), active
  (`StartTime <= now < EndTime`), or scheduled (`StartTime > now`) at any given moment.
- **`GET /api/v1/nodes?pageSize=100` is reused as-is** for both the Active Hub Nodes table and the
  Battery Slot Distribution widget (same query params → same TanStack Query cache entry, one
  network call for both), matching existing precedent (`app/(dashboard)/battery-slots/page.tsx`
  fetches active nodes the same way). Accepted at the current/expected node-count scale.
- **Dashboard queries refresh on a 60-second interval** (`staleTime`/`refetchInterval: 60_000` in
  `hooks/use-dashboard.ts`), layered on TanStack Query's default `refetchOnWindowFocus`. This is a
  "current status" landing page, not a live telemetry feed — no websocket/SSE infrastructure
  exists anywhere in this codebase, and none was introduced for this feature.
- **Charts use Recharts via shadcn's `chart.tsx` wrapper** (`npx shadcn@latest add chart`) — a
  stacked `BarChart` for the Hourly Dispatch widget (completed/active/scheduled per hour).
- **Battery Slot Distribution renders one small block per individual battery slot** (not a single
  proportional bar) for each node, reusing `BATTERY_SLOT_STATUS_META`'s existing color classes
  (`components/dashboard/nodes/battery-slot-status.ts`) so it stays visually consistent with
  `SlotCapacityBar` used elsewhere. The platform-wide "Aggregated Pool" row still uses a single
  proportional bar (built from the summary's counts, not real slot objects, since it represents
  the whole platform, not one node).

## Backend changes (`smart-solar-mgt-api`)

1. `Models/Dtos/DashboardSummaryResponse.cs` — `DashboardSummaryResponse`, `NodeSummary`
   (`ActiveCount`, `TotalCount`, `TotalCapacityKw`), `BatterySlotSummary`
   (`Available`/`Reserved`/`Occupied`/`Total`), `ReservationSummary`
   (`TotalToday`/`ActiveNowCount`/`ScheduledTodayCount`/`PeakHour`), `ProsumerSummary`.
2. `Models/Dtos/RecentActivityResponse.cs` — `RecentActivityResponse`, `ActivityItem`;
   `ActivityItem.Label` is a pre-formatted display string assembled server-side.
3. `Services/Dashboard/IDashboardService.cs` / `DashboardService.cs` — `GetSummaryAsync`,
   `GetRecentActivityAsync(limit = 10)`. Constructor-injects `MongoContext` directly, matching
   every other service's no-repository-layer pattern.
4. `Endpoints/DashboardEndpoints.cs` — `GET /api/v1/dashboard/summary`,
   `GET /api/v1/dashboard/recent-activity`, both under the `DashboardAccess` policy.
5. `Program.cs` — registers `IDashboardService` (scoped), adds the `DashboardAccess`
   authorization policy (`RequireRole(Backoffice, GridOperator)`, same shape as
   `ReservationManagement` etc.), and calls `app.MapDashboardEndpoints()`.
6. `Data/MongoContext.cs` — adds descending `CreatedAt` indexes on `Reservations`,
   `MicrogridNodes`, and `Prosumers` to back the recent-activity feed's sort queries.
7. `smart-solar-mgt-api.http` — example `GET` requests for the two routes.

**Not present** (built in the first pass, removed once the widgets that needed them were
replaced): a `/dashboard/reservations-trend` endpoint (7-day daily counts) and a
`NodesAtCapacity`/`NodeAtCapacity` field on `BatterySlotSummary` — both are unused once the
Hourly Dispatch chart and Battery Slot Distribution widget (which convey the same information
directly from real data) replaced their first-pass predecessors.

## Frontend changes (`smart-solar-mgt-fe`)

1. `npx shadcn@latest add chart` — adds `components/ui/chart.tsx` + the `recharts` dependency.
2. `lib/api/dashboard.ts` — `getDashboardSummary()`, `getRecentActivity(limit?)`, typed to mirror
   the backend DTOs.
3. `hooks/use-dashboard.ts` — `useDashboardSummary()`, `useRecentActivity()` (each `staleTime`/
   `refetchInterval: 60_000`), and `useTodaysReservations()` (wraps the existing
   `useReservations()` hook with today's date range, `status: "Confirmed"`, `pageSize: 100`).
4. `components/dashboard/overview/` —
   - `kpi-strip.tsx` — the 4–5 KPI tiles.
   - `active-hub-nodes-table.tsx` — read-only node table (no row actions).
   - `battery-slot-distribution.tsx` — aggregated pool bar + per-node slot-block grid.
   - `hourly-dispatch-chart.tsx` — stacked bar chart, 06:00–21:00.
   - `recent-activity-feed.tsx` — the Chronological Activity Log.
   - `reservation-activity.ts` — shared `classifyReservation()` used by the table and the chart.
5. `app/(dashboard)/page.tsx` — a two-column layout: KPI strip full-width, then the Active Hub
   Nodes table + Hourly Dispatch chart stacked in the wider left column, Battery Slot Distribution
   + Chronological Activity Log stacked in the narrower right column.

## Explicitly out of scope

- Any kWh-traded / revenue / real-time power-reading metric — no backing entity exists.
- A per-reservation power/kW field — the Hourly Dispatch chart's Y axis is a count, not kW; see
  **Visual design**.
- Websocket/SSE-based live push updates — 60-second polling is sufficient here.
- A "Needs Attention"/triage-queue section — no alerting/incident entity exists in this backend.
- Per-role dashboard tabs/views — one shared view for both roles.
- Any management action (status change, drill-down sheet, etc.) from within a dashboard widget —
  the Active Hub Nodes table is read-only by design; management stays on the dedicated routes.
- A live map of node locations, a Prosumer Overview panel, a Staff Overview panel, and an
  "Upcoming Reservations" list — all built in the first pass, removed per explicit correction (see
  Revision history).
- A `/grid-nodes/{id}` detail route.
- Raising `MicrogridNodeService`'s `MaxPageSize` beyond the existing precedent.

## Revision history

**Pass 2 (this revision).** The user supplied a reference mockup and gave five corrections against
the first pass:

1. The Active Hub Nodes table had a "Manage" button duplicating the Grid Nodes/Battery Slots
   management routes — removed; the widget is now purely informational.
2. The battery-slot widget didn't match the mockup — rebuilt from a donut chart into an aggregated
   pool bar + per-node slot-block grid (`battery-slot-distribution.tsx`).
3. The reservations-trend widget didn't match the mockup's hourly profile — rebuilt from a 7-day
   daily bar chart into an hourly (06:00–21:00) stacked bar chart
   (`hourly-dispatch-chart.tsx`), which also retired the `/dashboard/reservations-trend` endpoint.
4. Prosumer Overview, Staff Overview, and Upcoming Reservations widgets were removed outright —
   none appear in the mockup as standalone panels (prosumer/staff status is a KPI tile only, and
   there's no separate upcoming-reservations panel).
5. The KPI strip was rebuilt for closer visual/informational fidelity to the mockup's five cards
   (bordered-box sub-metric chips, a small status/peak-hour badge per tile, `font-mono` figures),
   and `NodeSummary.TotalCount` was added server-side so the network-health tile can show "X / Y
   nodes online" rather than only the active count.

**Pass 1.** Initial build: KPI strip, a live Leaflet map of nodes, a battery-slot donut +
"nodes at capacity" list, a 7-day reservations trend chart, an upcoming-reservations list, a
prosumer-status donut, a Backoffice-only staff-status panel, and a recent-activity feed.

## Verification

- Backend: `dotnet build` and `dotnet test` in `smart-solar-mgt-api` (11 existing tests,
  unaffected by this feature); manual `.http`/curl pass of `/dashboard/summary` and
  `/dashboard/recent-activity` as a Backoffice token (both 200; confirmed live against real seed
  data — see the session's verification output for exact response shapes).
- Frontend: `npm run build` and `npm run lint` in `smart-solar-mgt-fe` (clean on both).
- Manual browser check still outstanding as of this revision — the Claude-in-Chrome extension
  wasn't connected in this session, so only backend responses and Next.js compile/serve output
  were verified live; a full visual pass (both roles) is still owed once the extension is
  available or the user checks `http://localhost:3000` directly.
