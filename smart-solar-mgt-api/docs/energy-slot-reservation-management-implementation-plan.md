# Energy Slot Reservation Management Feature — Implementation Plan

Status: Implemented. This document records the design for Energy Slot Reservation Management
(backend API + web application integration) as built. See `project-specification.md`, sections 3.4
and 6, and this project's `CLAUDE.md` for the governing domain rules.

## Context

The web app already implements User, Prosumer, and Microgrid Node Management, all following one
consistent layering: a minimal-API `Endpoints/*.cs` group → a `Services/<Feature>/I*Service` +
implementation (constructor-injects `MongoContext` directly — there is no repository layer
anywhere in this codebase) → `Models/Entities/*` MongoDB POCOs → `Models/Dtos/*` request/response
records. Validation is inline in endpoint handlers (no FluentValidation), and mutations return a
result enum that the endpoint maps to an `IResult` via a switch expression.

Node Management already shipped a purpose-built seam for this feature:
`Services/Reservations/IReservationLookupService.cs` /
`Services/Reservations/StubReservationLookupService.cs`, which `MicrogridNodeService.DeactivateAsync`
calls before deactivating a node (blocking it with `409 ActiveReservationsExist` when active
reservations exist). The stub always returned `false`. This feature replaces it with a real
implementation and adds the `Reservations` collection/service — **no other Node Management code
changed** as a result, exactly as that seam was designed for.

Scope is backend + web application frontend, matching spec §3.4. Spec §4's mobile-only concerns
(QR-code transaction dispatch §4.2, Grid Operator scan-and-finalize Operator Mode §4.4, Google Maps
nearby-nodes §4.3, prosumer self-service booking) are out of scope — there is no prosumer
authentication in this codebase yet, so every reservation created via the web app is a
**staff-assisted booking** (a Backoffice or Grid Operator acting on a prosumer's behalf), not
prosumer self-service.

## Decisions

- **A battery slot holds at most one active reservation at a time.** `BatterySlotStatus` was
  designed as a single-flag toggle (`Available`/`Reserved`/`Occupied`), not a time-sliced interval
  calendar, and the spec gives no indication of needing intra-day multi-booking per slot.
- **The `Reservations` collection is the source of truth for double-booking prevention, not the
  embedded `MicrogridNode.BatterySlots[].Status` flag.** `PATCH /api/v1/nodes/{id}/battery-slots/{slotId}`
  already lets a Grid Operator flip any slot's status directly, with no awareness of reservations —
  gating bookings on that flag would let it desync from reality. Instead, "can this slot be booked"
  is answered by querying `Reservations` for `NodeId`+`SlotId` with `Status == Confirmed &&
  EndTime > now` — the same "active reservation" predicate `IReservationLookupService` already uses
  for node deactivation, applied one level more granularly. This is self-healing with zero
  background jobs: once a reservation's `EndTime` elapses, it stops blocking new bookings
  automatically (this codebase has no `BackgroundService`/`IHostedService`/cron anywhere).
  `BatterySlot.Status` is still flipped `Available ↔ Reserved` as a best-effort display cache on
  create/cancel, for `battery-slots-panel.tsx`'s benefit, but is explicitly non-authoritative — a
  known, accepted limitation.
- **Non-transactional write safety via insert-then-verify.** No Mongo transactions/sessions exist
  anywhere in this codebase. `ReservationService.CreateAsync` pre-checks for a conflict, inserts the
  `Confirmed` reservation, then re-checks excluding its own id — if a concurrent request won the
  race, it deletes the just-inserted document and returns `SlotNotAvailable`. The only document that
  can be left inconsistent on failure is the brand-new reservation itself, never the node's shared
  `BatterySlots` array.
- **7-day / 12-hour rule semantics:**
  - Create: `now < StartTime <= now + 7d`; only `StartTime` is bounded.
  - Update (reschedule): the new `StartTime`/`EndTime` pass the same future/7-day check as create;
    separately, the request is only allowed if the reservation's *current* `StartTime` is still
    ≥12h away. Update only reschedules the time window — node/slot stay fixed.
  - Cancel: same 12-hour check against the current `StartTime`.
  - A reservation whose `StartTime` already passed gets a distinct `AlreadyStarted` result on
    update/cancel, separate from `InsufficientNotice`.
- **Response enrichment happens server-side.** `ReservationResponse` includes `NodeName` and
  `ProsumerFullName`, resolved via batch `Find`+`$in` lookups in `ListAsync`/`GetByIdAsync` (or
  taken directly from the already-loaded prosumer/node on `CreateAsync`), computed at read time only
  and never stored on the `Reservation` entity — so a later node rename or prosumer edit can't leave
  stale names on old reservations.
- **NIC input is normalized identically to `ProsumerService`** (trim + uppercase). A prosumer must
  have `Status == Active` (not merely "not Deactivated") to be booked.
- **Authorization is one blanket `"ReservationManagement"` policy** (Backoffice + Grid Operator)
  across all five endpoints — unlike Node Management, scenario §6 gives no basis for restricting any
  reservation action to one role.
- **List filtering includes a date range** (`dateFrom`/`dateTo` against `StartTime`) so Grid
  Operators "monitoring power trading bookings" can separate upcoming from past bookings — there is
  no "completed" status in this phase to do that for them.
- **No max-reservations-per-prosumer cap**, and **no live-recomputed slot availability on the
  existing Node Management endpoints** — neither is stated by the spec.

## Backend changes (`smart-solar-mgt-api`)

1. `Models/Enums/ReservationStatus.cs` — `Confirmed`, `Cancelled`.
2. `Models/Entities/Reservation.cs` — `Id` (auto ObjectId), `ProsumerNic`, `NodeId`, `SlotId`,
   `StartTime`/`EndTime` (UTC), `Status`, `CreatedAt`/`CreatedBy`, `UpdatedAt?`/`UpdatedBy?`.
3. `Models/Dtos/CreateReservationRequest.cs`, `UpdateReservationRequest.cs`,
   `ReservationResponse.cs` (the latter's `FromEntity` takes the enrichment values as extra
   parameters, since they require external lookups the entity doesn't have).
4. `Services/Reservations/IReservationService.cs` / `ReservationService.cs` — `CreateAsync`,
   `GetByIdAsync`, `ListAsync`, `UpdateAsync`, `CancelAsync`; result enums
   `CreateReservationResult{Succeeded, ProsumerNotFound, ProsumerDeactivated, NodeNotFound,
   NodeDeactivated, SlotNotFound, SlotNotAvailable}` and
   `ReservationActionResult{Succeeded, NotFound, AlreadyCancelled, AlreadyStarted,
   InsufficientNotice}`.
5. `Services/Reservations/ReservationLookupService.cs` — real `IReservationLookupService`
   implementation, replacing `StubReservationLookupService.cs` (deleted).
6. `Endpoints/ReservationEndpoints.cs`, group `/api/v1/reservations`, all routes
   `.RequireAuthorization("ReservationManagement")`: `POST /`, `GET /`, `GET /{id}`,
   `PATCH /{id}` (reschedule), `PATCH /{id}/cancel`.
7. `Data/MongoContext.cs` — `Reservations` collection accessor; three indexes sized to their query
   shapes: `(NodeId, Status, EndTime)` for `HasActiveReservationsAsync`,
   `(NodeId, SlotId, Status, EndTime)` for the create-time conflict check, `(ProsumerNic)` for the
   list filter.
8. `Program.cs` — DI registrations for `ReservationLookupService`/`ReservationService`, the
   `"ReservationManagement"` policy, `app.MapReservationEndpoints()`.
9. `smart-solar-mgt-api.http` — example requests for all five endpoints.

## Frontend changes (`smart-solar-mgt-fe`)

- `lib/api/reservations.ts`, `lib/validations/reservation.ts`, `hooks/use-reservations.ts` —
  mirroring `lib/api/nodes.ts`/`hooks/use-nodes.ts`'s structure.
- `lib/time.ts` — `toApiDateTime`/`toInputDateTime` helpers for full timestamps (ISO 8601 round
  trip via `Date`/`toISOString`, distinct from the existing `TimeOnly`-specific helpers).
- `lib/api/errors.ts` — error-code entries for the new result enums plus create-validation codes.
- `components/dashboard/reservations/` — table, status badge, pagination, create/edit/cancel
  dialogs, row actions, mirroring `components/dashboard/nodes/` and `components/dashboard/prosumers/`.
- `app/(dashboard)/reservations/page.tsx` — replaces the existing `ContentPanel` placeholder with
  the full list/filter/create UI.

## Explicitly out of scope

Mobile app integration entirely (prosumer self-service booking, QR-code dispatch, Operator Mode
finalize, nearby-nodes map) — these require prosumer authentication, which doesn't exist yet.
Live-recomputed slot availability on the Node Management endpoints (they keep showing the
best-effort cached `BatterySlot.Status`). A "Completed"/in-progress reservation status (that's
`BatterySlotStatus.Occupied`, set by the out-of-scope mobile finalize flow). A
max-reservations-per-prosumer cap. Booking-confirmation notifications. Mongo multi-document
transactions.

## Verification

`dotnet test`; manual pass via `smart-solar-mgt-api.http` — create a reservation and confirm the
slot's cached status flips to `Reserved`; attempt a duplicate booking on the same slot and confirm
`409 SlotNotAvailable`; attempt to create outside the 7-day/future window and confirm `400`;
reschedule/cancel ≥12h before start and confirm success, then <12h before and confirm
`409 InsufficientNotice`; cancel and confirm the slot flips back to `Available`; confirm
`PATCH /api/v1/nodes/{id}/deactivate` now returns `409 ActiveReservationsExist` while a future
`Confirmed` reservation exists, and succeeds once it's cancelled.
