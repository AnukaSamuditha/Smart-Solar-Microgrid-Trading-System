# Microgrid Node Management Feature — Implementation Plan

Status: Proposed — pending review/approval. This document records the design for Microgrid Node
Management (backend API + web application integration) before implementation begins. See
`project-specification.md`, sections 3.3 and 6, and this project's `CLAUDE.md` for the governing
domain rules.

## Context

The web app already implements two features whose layering Microgrid Node Management should
mirror: **User Management** (`Endpoints/UserEndpoints.cs`, `Services/Users/*`, Backoffice-only)
and **Prosumer Management** (`Endpoints/ProsumerEndpoints.cs`, `Services/Prosumers/*`, Backoffice
+ Grid Operator, see `docs/prosumer-management-implementation-plan.md`). Both follow the same
chain: a minimal-API `Endpoints/*.cs` group → a `Services/<Feature>/I*Service` + implementation
(constructor-injects `MongoContext` directly — there is no repository layer anywhere in this
codebase) → `Models/Entities/*` MongoDB POCOs → `Models/Dtos/*` request/response records.
Validation is inline in endpoint handlers (no FluentValidation), there is no global exception
middleware or `ApiResponse<T>` wrapper, and mutations return a result enum that the endpoint maps
to an `IResult` via a switch expression. `Models/Dtos/PagedResult<T>` already exists — its own doc
comment was written anticipating "future list endpoints (e.g. Grid Nodes, Reservations)" — and is
reused here unchanged for the node list endpoint.

Per spec §3.3, Node Management covers: creating solar grid hubs with GPS location, capacity specs
(kW/h), and available battery storage slots; updating operational schedules; and deactivating
nodes, which must be **blocked while active energy reservations exist** against that node.
Reservation Management (spec §3.4) is a separate, not-yet-built feature, so this plan introduces a
small seam — an injected lookup interface — so the deactivation rule can be wired to the real
Reservation feature later with zero changes to Node Management's own code.

The project scenario (§6) additionally clarifies the authorization split that the spec's feature
list alone doesn't state: **the Backoffice team is responsible for managing the registration of
solar microgrid nodes and maintaining their operational schedules**, while **Grid Operators... are
responsible for updating battery slot availability**. This plan encodes that split directly into
per-endpoint authorization policies rather than giving every node action the same blanket policy
(the approach Prosumer Management used, where both roles share every action except reactivation).

Unlike `Prosumer` (keyed by NIC, a natural key) or `User` (an auto-generated ObjectId with no
embedded documents), `MicrogridNode` needs two new kinds of storage in this codebase: a
**geospatial field** (GPS location) and a **list of embedded sub-documents with their own status
lifecycle** (battery slots). Both are new patterns here, so this plan calls them out explicitly
below.

## Decisions confirmed with the product owner before implementation

- **GPS location is stored as a native MongoDB GeoJSON point** —
  `GeoJsonPoint<GeoJson2DGeographicCoordinates>` on the `Location` field, with a `2dsphere` index.
  `MongoDB.Driver` already ships `MongoDB.Driver.GeoJsonObjectModel`, so this needs **no new NuGet
  package**. Storing a proper geospatial type now (rather than two bare `double` fields) means a
  future "nearby grid nodes" feature (mobile app spec §4.3) can use native `$near`/`$geoWithin`
  queries with no migration. The **API contract stays simple**, though: `CreateMicrogridNodeRequest`
  and `MicrogridNodeResponse` expose plain `Latitude`/`Longitude` doubles: the service layer is the
  only place that knows about the GeoJSON representation, converting to/from it at the
  entity boundary.
- **GPS capture in the frontend (Phase 2) is an interactive map picker**, not manual lat/lng text
  fields or address geocoding. Specifically, **Leaflet + OpenStreetMap** (via `react-leaflet`), not
  the Google Maps JS API — no API key or billing setup is needed for a simple pin-drop picker, and
  the mobile app's own use of Google Maps (spec §4.3) is a separate, native-Android concern that
  doesn't require the web app to match it. The picker emits `{ latitude, longitude }`, which maps
  directly onto the backend DTO fields above.
- **Battery storage slots are individually tracked**, not a bare capacity integer. Each node holds
  an embedded `List<BatterySlot>` (`SlotId`, `Status`: `Available` / `Reserved` / `Occupied`),
  generated at creation time from a requested slot count (`SlotId`s `"1".."N"`). This is more
  granular than the spec's minimum ask, but it means future Reservation Management can claim a
  *specific* slot by id rather than only decrementing a count — avoiding a data-model change when
  that feature is built. Grid Operators update one slot's `Status` at a time via a dedicated
  endpoint (see below), matching scenario §6.
- **The active-reservations deactivation check is an injected interface, not an inline stub.**
  `Services/Reservations/IReservationLookupService.cs` declares
  `Task<bool> HasActiveReservationsAsync(string nodeId, CancellationToken ct)`.
  `MicrogridNodeService.DeactivateAsync` calls it before flipping status; a `true` result maps to a
  new `NodeActionResult.ActiveReservationsExist`, which the endpoint returns as `409 Conflict`
  (`error: "ActiveReservationsExist"`). A `StubReservationLookupService` is registered today that
  always returns `false`, with a `// TODO` comment pointing at spec §3.4. **When Reservation
  Management is built, only the DI registration in `Program.cs` changes** — no Node
  service/endpoint code needs to change. This mirrors the codebase's existing convention of
  forward-referencing not-yet-built features (see `PagedResult<T>`'s own doc comment).
- **A Reactivate action is included**, even though spec §3.3 names only create/update-schedule/
  deactivate. Without it, a node taken offline (e.g. for maintenance) would be permanently dead.
  This mirrors User/Prosumer Management's existing Deactivate/Reactivate pair. Reactivate is
  **Backoffice-only**, consistent with Backoffice owning node lifecycle per scenario §6.
- **Authorization is split by action, not blanket like Prosumer Management**:
  - Create, Update Schedule, Deactivate, Reactivate → **Backoffice only** (`RoleNames.Backoffice`
    policy) — scenario §6's "registration... and maintaining... operational schedules."
  - Update Battery Slot Status → **Backoffice + Grid Operator** (new `"NodeBatterySlotManagement"`
    policy, `RequireRole` OR semantics like the existing `"ProsumerManagement"` policy) — Grid
    Operators do this day-to-day per scenario §6, and Backoffice retains an admin override.
  - List / Get → **Backoffice + Grid Operator** (reuse the same `"NodeBatterySlotManagement"`
    policy, or introduce a `"NodeManagement"` read policy with identical role membership) — Grid
    Operators need visibility into nodes to do their slot-management job.
- **Schedule model**: the spec doesn't define what a node "schedule" contains, so this plan adopts
  a **weekly operating-hours shape** as the most natural reading for a solar hub's trading
  availability — `List<ScheduleEntry>` with `DayOfWeek`, `OpenTime`/`CloseTime` (`TimeOnly?`), and
  `IsClosed` (bool, for days with no trading). This is called out explicitly as an assumption to
  revisit if the product owner intended something else (e.g. maintenance-window scheduling).
- **No hard delete.** Only create/update-schedule/update-slot-status/deactivate/reactivate, same
  reasoning as Prosumer Management staying literal to its own spec section beyond the confirmed
  additions above.
- **Backend pagination, search, and status filtering** on the list endpoint, reusing the exact
  `PagedResult<T>` shape and query-param conventions (`search`, `status`, `page`, `pageSize`,
  `sortBy`, `sortDir`) Prosumer Management introduced — no new pattern needed here.

## Backend changes (`smart-solar-mgt-api`)

1. `Models/Enums/MicrogridNodeStatus.cs` (new) — `Active`, `Deactivated`.
2. `Models/Enums/BatterySlotStatus.cs` (new) — `Available`, `Reserved`, `Occupied`.
3. `Models/Entities/BatterySlot.cs` (new) — embedded POCO (no `[BsonId]`, it's a sub-document
   inside `MicrogridNode.BatterySlots`, not its own collection):
   - `SlotId` — `string`.
   - `Status` — `BatterySlotStatus`, `[BsonRepresentation(BsonType.String)]` (matches
     `Prosumer.Status`'s representation choice).
4. `Models/Entities/ScheduleEntry.cs` (new) — embedded POCO:
   - `DayOfWeek` — `System.DayOfWeek`, `[BsonRepresentation(BsonType.String)]`.
   - `OpenTime` / `CloseTime` — `TimeOnly?`.
   - `IsClosed` — `bool`.
5. `Models/Entities/MicrogridNode.cs` (new) — MongoDB POCO:
   - `Id` — `[BsonId] [BsonRepresentation(BsonType.ObjectId)] string`, auto-generated (unlike
     `Prosumer`, a node has no natural-key field to use as `_id`).
   - `Name` — `string` (human-readable hub label/site name).
   - `Location` — `GeoJsonPoint<GeoJson2DGeographicCoordinates>` (GPS coordinates; coordinate
     order is `[longitude, latitude]` per the GeoJSON spec — the service layer maps this from/to
     the DTO's `Latitude`/`Longitude` fields to avoid leaking that ordering detail past the
     entity boundary).
   - `CapacityKw` — `double` (capacity spec, kW/h per spec §3.3).
   - `BatterySlots` — `List<BatterySlot>`.
   - `Schedule` — `List<ScheduleEntry>`.
   - `Status` — `MicrogridNodeStatus`, `[BsonRepresentation(BsonType.String)]`.
   - `CreatedAt` — `DateTime`; `CreatedBy` — `string` (id of the Backoffice user who created it).
   - `UpdatedAt` — `DateTime?`; `UpdatedBy` — `string?`.
6. `Services/Reservations/IReservationLookupService.cs` (new) — the deactivation-block seam:
   ```csharp
   public interface IReservationLookupService
   {
       Task<bool> HasActiveReservationsAsync(string nodeId, CancellationToken ct = default);
   }
   ```
7. `Services/Reservations/StubReservationLookupService.cs` (new) — always returns `false`, with a
   `// TODO: replace with a real lookup against the Reservations collection once Energy Slot
   Reservation Management (spec §3.4) is implemented` comment. Registered in `Program.cs` today;
   swapped for a real implementation later with no other code changes.
8. `Models/Dtos/CreateMicrogridNodeRequest.cs` (new) — `record CreateMicrogridNodeRequest(string
   Name, double Latitude, double Longitude, double CapacityKw, int BatterySlotCount)`.
9. `Models/Dtos/UpdateMicrogridNodeScheduleRequest.cs` (new) — `record
   UpdateMicrogridNodeScheduleRequest(List<ScheduleEntryDto> Schedule)`, where `ScheduleEntryDto`
   is a small record mirroring `ScheduleEntry`'s shape for the wire format.
10. `Models/Dtos/UpdateBatterySlotStatusRequest.cs` (new) — `record
    UpdateBatterySlotStatusRequest(BatterySlotStatus Status)`, targeted at a specific `slotId` in
    the route.
11. `Models/Dtos/MicrogridNodeResponse.cs` (new) — public projection with a `FromEntity` static
    method (mirroring `ProsumerResponse`), flattening `Location` back to `Latitude`/`Longitude`.
12. `Program.cs`:
    - `builder.Services.AddScoped<IReservationLookupService, StubReservationLookupService>();`
    - `builder.Services.AddScoped<IMicrogridNodeService, MicrogridNodeService>();`
    - Extend `AddAuthorizationBuilder()`:
      ```csharp
      .AddPolicy("NodeBatterySlotManagement", policy => policy.RequireRole(RoleNames.Backoffice, RoleNames.GridOperator))
      ```
      (Create/Schedule/Deactivate/Reactivate reuse the existing `RoleNames.Backoffice` policy
      directly — no new policy needed for those.)
    - `app.MapMicrogridNodeEndpoints();` alongside the existing `MapProsumerEndpoints()`.
13. `Endpoints/MicrogridNodeEndpoints.cs` (new), group `/api/v1/nodes`:
    - `POST /` → `CreateNodeAsync` — `.RequireAuthorization(RoleNames.Backoffice)`. Validates
      `Name` non-empty, `Latitude` in `[-90, 90]`, `Longitude` in `[-180, 180]`, `CapacityKw > 0`,
      `BatterySlotCount >= 0` (all inline, matching `ProsumerEndpoints`'s existing validation
      style). Returns `201 Created` with `MicrogridNodeResponse`.
    - `GET /` → `ListNodesAsync` — `.RequireAuthorization("NodeBatterySlotManagement")`. Query
      params: `search` (matches `Name`, case-insensitive partial match), `status`
      (`Active`/`Deactivated`, optional), `page`, `pageSize`, `sortBy` (`Name`/`CapacityKw`/
      `CreatedAt`), `sortDir`. Returns `PagedResult<MicrogridNodeResponse>`.
    - `GET /{id}` → `GetNodeAsync` — `.RequireAuthorization("NodeBatterySlotManagement")`.
    - `PATCH /{id}/schedule` → `UpdateScheduleAsync` — `.RequireAuthorization(RoleNames.Backoffice)`.
    - `PATCH /{id}/battery-slots/{slotId}` → `UpdateBatterySlotStatusAsync` —
      `.RequireAuthorization("NodeBatterySlotManagement")`. `404` (`error: "SlotNotFound"`) if
      `slotId` doesn't exist on the node.
    - `PATCH /{id}/deactivate` → `DeactivateNodeAsync` — `.RequireAuthorization(RoleNames.Backoffice)`.
      `409 Conflict` (`error: "ActiveReservationsExist"`) when the reservation lookup returns
      `true`.
    - `PATCH /{id}/reactivate` → `ReactivateNodeAsync` — `.RequireAuthorization(RoleNames.Backoffice)`.
14. `Services/Nodes/IMicrogridNodeService.cs` / `MicrogridNodeService.cs` (new) —
    constructor-injects `MongoContext` and `IReservationLookupService`. Methods:
    - `CreateAsync(name, latitude, longitude, capacityKw, batterySlotCount, createdByUserId, ct)`
      — builds the `BatterySlots` list, converts lat/lng to `GeoJsonPoint`, inserts the document.
    - `GetByIdAsync(id, ct)`.
    - `ListAsync(search, status, page, pageSize, sortBy, sortDir, ct)` — same filter/sort/
      `Skip`/`Limit`/`CountDocumentsAsync` pattern as `ProsumerService.ListAsync`.
    - `UpdateScheduleAsync(id, schedule, performedByUserId, ct)`.
    - `UpdateBatterySlotStatusAsync(id, slotId, status, performedByUserId, ct)` — uses a
      positional array filter (`Builders<MicrogridNode>.Filter` + `.Update.Set("BatterySlots.$.Status",
      ...)` with an `ArrayFilters` element, or an equivalent `Filter.ElemMatch` + rewrite) to
      update the single matching embedded slot; returns `NodeActionResult.SlotNotFound` if no slot
      with that id exists.
    - `DeactivateAsync(id, performedByUserId, ct)` — calls
      `_reservationLookup.HasActiveReservationsAsync(id, ct)` first; if `true`, returns
      `NodeActionResult.ActiveReservationsExist` without writing; otherwise delegates to a shared
      private `SetStatusAsync` helper (mirroring `ProsumerService`'s pattern).
    - `ReactivateAsync(id, performedByUserId, ct)` — delegates to the same `SetStatusAsync`
      helper.
    - A `NodeActionResult` enum: `Succeeded`, `NotFound`, `ActiveReservationsExist`,
      `SlotNotFound`.
15. `Data/MongoContext.cs`:
    - Add `IMongoCollection<MicrogridNode> MicrogridNodes => _database.GetCollection<MicrogridNode>("MicrogridNodes");`
    - Extend `EnsureIndexesAsync` with a `2dsphere` index on `Location`:
      ```csharp
      var nodeLocationIndex = new CreateIndexModel<MicrogridNode>(
          Builders<MicrogridNode>.IndexKeys.Geo2DSphere(n => n.Location));
      await MicrogridNodes.Indexes.CreateOneAsync(nodeLocationIndex, cancellationToken: cancellationToken);
      ```
16. `smart-solar-mgt-api.http` — add example requests for all six endpoints, including the
    battery-slot and schedule update payload shapes.

No EF migrations needed (MongoDB, schema is implicit POCOs, same as Users/Prosumers).

## Frontend changes (`smart-solar-mgt-fe`) — Phase 2

- Add `react-leaflet` + `leaflet` (+ `@types/leaflet`) as new npm dependencies — nothing mapping-
  related exists in this codebase yet.
- `lib/api/nodes.ts` (new) — `listNodes(params)`, `getNode`, `createNode`, `updateNodeSchedule`,
  `updateBatterySlotStatus`, `deactivateNode`, `reactivateNode`, typed against the backend's
  `PagedResult`/`MicrogridNodeResponse` shapes, following `lib/api/prosumers.ts`'s structure.
- `lib/api/errors.ts` — add error-code → copy entries: `ActiveReservationsExist`,
  `SlotNotFound`, plus standard `NotFound`/validation entries in the node context.
- `lib/validations/node.ts` (new) — zod: `createNodeSchema` (name, latitude/longitude ranges,
  capacity `> 0`, battery slot count `>= 0`), `updateScheduleSchema` (array of day entries with
  open/close time cross-field validation when not closed).
- `hooks/use-nodes.ts` (new) — TanStack Query hooks: `useNodes({ search, status, page, pageSize,
  sortBy, sortDir })` with all params in the `queryKey`, `useNode(id)`, plus create/update-
  schedule/update-slot/deactivate/reactivate mutations invalidating the `["nodes"]` key family —
  mirroring `hooks/use-prosumers.ts`.
- `components/dashboard/nodes/` (new folder):
  - `node-map-picker.tsx` — wraps `react-leaflet`'s `MapContainer`/`TileLayer`/`Marker`; click
    handler drops/moves a pin and reports `{ latitude, longitude }` up to the form via
    `react-hook-form`'s `setValue`. Used inside both create and edit dialogs.
  - `nodes-table.tsx` — columns: Name, Location (short lat/lng or reverse-geocoded label if added
    later), Capacity, Battery slots (e.g. "3/5 available"), Status, row actions.
  - `node-status-badge.tsx` — Active/Deactivated.
  - `create-node-dialog.tsx` — shadcn `Dialog` + `react-hook-form` + `zodResolver`, embeds
    `node-map-picker.tsx`, mirrors `CreateProsumerDialog` structurally. **Backoffice only.**
  - `edit-node-schedule-dialog.tsx` — a day-by-day open/close time editor. **Backoffice only.**
  - `battery-slots-panel.tsx` — a grid of slot chips (id + status) with a per-slot status
    dropdown/action; visible to **both** Backoffice and Grid Operator, but this is the *only*
    panel Grid Operators can act on.
  - `deactivate-node-dialog.tsx` — shadcn `AlertDialog` confirmation; surfaces the
    `ActiveReservationsExist` error as an inline message (not just a toast) since it's an expected,
    actionable outcome once Reservation Management exists. **Backoffice only.**
  - `node-row-actions.tsx` — dropdown menu, role-conditional: Backoffice sees Edit Schedule /
    Deactivate / Reactivate; Grid Operator sees only "Manage Battery Slots" (opens
    `battery-slots-panel.tsx`).
- `app/(dashboard)/nodes/page.tsx` — table page wrapped in `<RequireRole
  roles={["Backoffice","GridOperator"]}>` (both roles need list/read access; row actions are
  gated individually as above), same layout convention as `app/(dashboard)/prosumers/page.tsx`.
- `lib/nav-config.ts` — add a `Nodes` (or "Grid Nodes") nav entry with
  `roles: ["Backoffice", "GridOperator"]`.

## Explicitly out of scope

Real Reservation Management integration (only the `IReservationLookupService` seam, stubbed to
always allow deactivation); a `$near`/distance-based "nearby nodes" query endpoint (the GeoJSON +
`2dsphere` index groundwork supports adding one later with no schema change, but no endpoint is
built in this phase); hard delete; bulk actions; any mobile-app changes (spec §4.3's "nearby grid
nodes" map view is a separate, later mobile feature that would consume a future `$near` endpoint).

## Verification

End-to-end manual pass once both phases exist: create a node by dropping a pin on the map and
confirm the stored/returned lat/lng round-trip correctly; reject invalid lat/lng ranges, a
non-positive capacity, and a negative slot count (backend 400s and frontend inline errors); update
a schedule as Backoffice and confirm a Grid Operator request to the same endpoint returns `403`;
update a battery slot's status as both a Backoffice and a Grid Operator account; attempt
deactivation and confirm it succeeds today (the stub always reports no active reservations) —
noted as the expected, temporary behavior until Reservation Management is built; reactivate a
deactivated node as Backoffice; confirm a Grid Operator never sees Create/Schedule/Deactivate/
Reactivate actions in the UI, and a direct API call to any of those endpoints as Grid Operator
returns `403`; `dotnet test` and `npm run lint`.
