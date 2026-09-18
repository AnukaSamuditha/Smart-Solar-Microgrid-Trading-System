# Prosumer Management Feature — Implementation Plan

Status: Proposed — pending review/approval. This document records the design for Prosumer
Management (backend API + web application integration) before implementation begins. See
`project-specification.md`, section 3.2, and this project's `CLAUDE.md` for the governing
domain rules.

## Context

The web app already implements an equivalent feature, User Management, for Backoffice/Grid
Operator accounts (`Endpoints/UserEndpoints.cs`, `Services/Users/*`, and the frontend's
`components/dashboard/users/*`). Prosumer Management was explicitly deferred at that time
(`docs/user-management-implementation-plan.md`: "Prosumer user management (deferred, separate
identity model)") and nothing exists for it yet — confirmed via a repo-wide search for
"prosumer" in the backend (zero hits). This document proposes a Prosumer Management feature that
structurally mirrors User Management's layering (Endpoints → Services → MongoContext on the
backend; table/dialogs/hooks/validation-schema on the frontend) while diverging where the spec's
own rules for prosumers differ from users:

- Prosumers are keyed by **NIC** (National Identity Card number), not a generated id — spec §3.2
  says NIC "should be the primary key" literally, so the Prosumer document's Mongo `_id` **is**
  the NIC string, unlike `User`, which uses an auto-generated ObjectId.
- Prosumer accounts are **not** invitation-based — the password is supplied directly at creation
  (NIC + email + password), unlike Users, who are invited by email and set their own password
  later. No login/authentication endpoint for prosumers is included in this phase; the password is
  stored (hashed) for a future mobile-app prosumer-authentication feature, which is out of scope
  here (per `CLAUDE.md`: "Solar Prosumer auth... not yet built").
- Prosumer management is usable by **both Backoffice and Grid Operator** roles, unlike User
  Management, which is Backoffice-only. **Reactivating a deactivated prosumer is Backoffice-only.**
  This needs a new combined authorization policy; today only a Backoffice-only policy exists.
- The list endpoint gains **backend search, status filtering, and pagination** — none of which
  exist yet anywhere in this codebase (the existing Users list returns every user, unfiltered,
  unpaginated, by explicit prior decision). Search must be backend-driven, debounced from the
  frontend. Status filtering is also backend-driven (not client-side-only), because combined with
  pagination, a client-side-only filter would miss matches sitting on pages that haven't been
  fetched. A same-page column sort remains a lightweight frontend-only nicety.
- Per spec §3.2, only **create, update, deactivate, reactivate** are defined for prosumers — no
  hard delete (User Management added one beyond its own spec; this feature intentionally does
  not, to stay literal to §3.2).

## Decisions confirmed with the product owner before implementation

- **Prosumer fields**: NIC (primary key, immutable after creation), Email, Password (hashed),
  plus an optional **Full Name** field — not named in the spec's three required creation fields,
  but added (mirroring `User.Username` being optional) so the table/UI has a human-readable label
  beyond the NIC.
- **NIC format**: validated as a Sri Lankan NIC — old format `^[0-9]{9}[vVxX]$` or new format
  `^[0-9]{12}$` — on both backend and frontend.
- **No hard delete.** Only the four spec-named actions.
- **Backend pagination** (`page`/`pageSize`) on the list endpoint, a new pattern in this codebase.
- **Status filter is a backend query param**, joining `search` before pagination is applied, so
  filtering is correct across all pages rather than only the currently-loaded one.
- **Password rule**: minimum 8 characters, enforced server-side (matching the existing
  `AcceptInvitationRequest` convention exactly — see `Endpoints/AuthEndpoints.cs`); the frontend
  additionally nudges for an uppercase letter and a digit, the same way `reset-password.ts`
  already does for Backoffice/Grid Operator password setup, without the backend enforcing that
  extra complexity (consistent with the existing precedent, not a new inconsistency).

## Backend changes (`smart-solar-mgt-api`)

1. `Models/Enums/ProsumerStatus.cs` (new) — `Active`, `Deactivated`. No `Invited` state; a
   prosumer is `Active` immediately on creation since a password is supplied up front.
2. `Models/Entities/Prosumer.cs` (new) — MongoDB POCO:
   - `Nic` — `[BsonId]` `string` (the Mongo `_id`; no `[BsonRepresentation(BsonType.ObjectId)]`,
     since it's a plain string primary key, not an ObjectId).
   - `Email` — `string`.
   - `FullName` — `string?`.
   - `PasswordHash` — `string`.
   - `Status` — `ProsumerStatus`, `[BsonRepresentation(BsonType.String)]` (matches `User.Status`'s
     representation choice).
   - `CreatedAt` — `DateTime`; `CreatedBy` — `string` (id of the Backoffice/Grid Operator user who
     created the record).
   - `UpdatedAt` — `DateTime?`; `UpdatedBy` — `string?`.
3. `Models/Dtos/CreateProsumerRequest.cs` (new) — `record CreateProsumerRequest(string Nic, string
   Email, string? FullName, string Password)`.
4. `Models/Dtos/UpdateProsumerRequest.cs` (new) — `record UpdateProsumerRequest(string Email,
   string? FullName)`. NIC is not editable through this endpoint (it's the primary key); no
   password-change endpoint in this phase.
5. `Models/Dtos/ProsumerResponse.cs` (new) — public projection omitting `PasswordHash`, with a
   `FromEntity` static method, mirroring `UserResponse`.
6. `Models/Dtos/PagedResult.cs` (new) — `record PagedResult<T>(IReadOnlyList<T> Items, long
   TotalCount, int Page, int PageSize)`. Generic and reusable by future paginated endpoints (e.g.
   Grid Nodes, Reservations) — this is the first paginated response shape in the codebase.
7. `Program.cs` — add a second authorization policy:
   `builder.Services.AddAuthorizationBuilder()
      .AddPolicy(RoleNames.Backoffice, policy => policy.RequireRole(RoleNames.Backoffice))
      .AddPolicy("ProsumerManagement", policy => policy.RequireRole(RoleNames.Backoffice, RoleNames.GridOperator));`
   (`RequireRole` with multiple roles is OR semantics — either role satisfies the policy.) Also
   call `app.MapProsumerEndpoints()` alongside the existing `MapUserEndpoints()`.
8. `Endpoints/ProsumerEndpoints.cs` (new), group `/api/v1/prosumers`:
   - `POST /` → `CreateProsumerAsync` — `.RequireAuthorization("ProsumerManagement")`. Validates
     NIC format, email format, password length (all inline, matching `UserEndpoints`'s existing
     inline-validation style — no FluentValidation is used anywhere in this codebase). Returns
     `201 Created` with the new `ProsumerResponse`, or `409 Conflict` (`error: "NicAlreadyInUse"`
     or `"EmailAlreadyInUse"`) on a duplicate.
   - `GET /` → `ListProsumersAsync` — `.RequireAuthorization("ProsumerManagement")`. Query
     params: `search` (matches NIC or email or full name, case-insensitive partial match),
     `status` (`Active`/`Deactivated`, optional), `page` (default 1), `pageSize` (default 20, capped
     e.g. at 100), `sortBy` (`Nic`/`Email`/`CreatedAt`, default `CreatedAt`), `sortDir`
     (`asc`/`desc`, default `desc`). Returns `PagedResult<ProsumerResponse>`.
   - `PUT /{nic}` → `UpdateProsumerAsync` — `.RequireAuthorization("ProsumerManagement")`. Updates
     email/full name; `409 Conflict` (`"EmailAlreadyInUse"`) if the new email collides with another
     prosumer.
   - `PATCH /{nic}/deactivate` → `DeactivateProsumerAsync` — `.RequireAuthorization("ProsumerManagement")`.
   - `PATCH /{nic}/reactivate` → `ReactivateProsumerAsync` — `.RequireAuthorization(RoleNames.Backoffice)`
     (Backoffice only — the one action Grid Operators may not perform, per spec §3.2).
9. `Services/Prosumers/IProsumerService.cs` / `ProsumerService.cs` (new) — constructor-injects
   `MongoContext` and `IPasswordHasherService` (the existing, entity-agnostic hasher used by
   `UserService`/`AuthEndpoints` today — no new hashing code needed). Methods:
   - `CreateAsync(nic, email, fullName, password, createdByUserId, ct)` — normalizes NIC/email,
     hashes the password, inserts the document, and maps a Mongo duplicate-key exception on `_id`
     to a `ProsumerActionResult.NicConflict` (email uniqueness is likewise caught and mapped to
     `EmailConflict`, via a unique index on `Email`, same pattern as `UserService.UpdateAsync`'s
     existing `DuplicateKey` handling).
   - `GetByNicAsync(nic, ct)`.
   - `ListAsync(search, status, page, pageSize, sortBy, sortDir, ct)` — builds a Mongo filter
     (regex on Nic/Email/FullName for `search`, equality on `Status`), applies sort, `Skip`/`Limit`
     for pagination, and a parallel `CountDocumentsAsync` for `TotalCount`.
   - `UpdateAsync(nic, email, fullName, performedByUserId, ct)`.
   - `DeactivateAsync` / `ReactivateAsync(nic, performedByUserId, ct)` — both delegate to a shared
     private `SetStatusAsync` helper, mirroring `UserService`'s exact pattern.
   - A `ProsumerActionResult` enum: `Succeeded`, `NotFound`, `NicConflict`, `EmailConflict`.
10. `Data/MongoContext.cs` — add `IMongoCollection<Prosumer> Prosumers =>
    _database.GetCollection<Prosumer>("Prosumers")`; extend `EnsureIndexesAsync` with a unique
    index on `Prosumers.Email` (NIC is already unique by virtue of being the Mongo `_id`).
11. `smart-solar-mgt-api.http` — add example requests for all five Prosumer endpoints, including
    the query-param variations for search/status/pagination.

No EF migrations needed (MongoDB, schema is implicit POCOs, same as Users).

## Frontend changes (`smart-solar-mgt-fe`)

- Install the shadcn `pagination` component (not yet present in `components/ui/`; every other
  needed primitive — `table`, `dialog`, `alert-dialog`, `select`, `badge`, `dropdown-menu`,
  `field`, `input`, `sonner` — is already installed from the User Management build).
- `lib/api/prosumers.ts` (new) — `listProsumers(params)`, `createProsumer`, `updateProsumer`,
  `deactivateProsumer`, `reactivateProsumer`, typed against the backend's `PagedResult`/
  `ProsumerResponse` shapes, following `lib/api/users.ts`'s existing structure.
- `lib/api/errors.ts` — add error-code → copy entries: `InvalidNic`, `NicAlreadyInUse`,
  `EmailAlreadyInUse` (prosumer context), `ValidEmailRequired`, `WeakPassword`.
- `lib/validations/prosumer.ts` (new) — zod: `createProsumerSchema` (NIC regex per the Decisions
  section, email, optional full name, password with the same min-8/uppercase/number rule as
  `reset-password.ts`), `updateProsumerSchema` (email + full name only; NIC is not a form field
  when editing).
- `hooks/use-debounce.ts` (new) — a generic `useDebouncedValue(value, delayMs)` hook; nothing like
  it exists in this codebase yet. The prosumers page debounces the search box's value (suggested
  300ms) before it feeds into the TanStack Query key/params, so the backend sees one request per
  pause-in-typing, not one per keystroke.
- `hooks/use-prosumers.ts` (new) — TanStack Query hooks: `useProsumers({ search, status, page,
  pageSize, sortBy, sortDir })` with all params in the `queryKey` (so different filter/page
  combinations cache independently, and `placeholderData` keeps the previous page visible while
  the next one loads), plus create/update/deactivate/reactivate mutations that invalidate the
  `["prosumers"]` key family on success — mirroring `hooks/use-users.ts` exactly.
- `components/dashboard/prosumers/` (new folder):
  - `prosumers-table.tsx` — columns: NIC, Name/Email, Status, Date added, row actions. Empty-state
    row like `UsersTable`.
  - `prosumer-status-badge.tsx` — Active/Deactivated only (not reusing `StatusBadge` from Users,
    which also models `Invited`, a state prosumers never have).
  - `create-prosumer-dialog.tsx` / `edit-prosumer-dialog.tsx` — shadcn `Dialog` +
    `react-hook-form` + `zodResolver`, mirroring `CreateUserDialog`/`EditUserDialog` structurally.
  - `deactivate-prosumer-dialog.tsx` — shadcn `AlertDialog` confirmation, mirroring
    `DeactivateUserDialog`.
  - `prosumer-row-actions.tsx` — dropdown menu with Edit and Deactivate/Reactivate (mutually
    exclusive by current status, like `UserRowActions`). **The Reactivate item only renders when
    the signed-in user's role is Backoffice** (read via the existing `useMe()` hook) — Grid
    Operators see Deactivate only when the record is Active, and see neither lifecycle action when
    it's already Deactivated, since they cannot reactivate it.
  - A debounced search `Input` and a status `Select` filter (All/Active/Deactivated) above the
    table, plus `Pagination` controls below it.
- `app/(dashboard)/prosumers/page.tsx` — replace the stub with the real table UI (client
  component, same layout convention as `app/(dashboard)/users/page.tsx`), wrapped in
  `<RequireRole roles={["Backoffice", "GridOperator"]}>` — **widened from today's
  Backoffice-only stub**, since this is the one dashboard feature Grid Operators also use.
- `lib/nav-config.ts` — widen the existing `Prosumers` nav entry's `roles` from `["Backoffice"]`
  to `["Backoffice", "GridOperator"]`.

## Explicitly out of scope

Prosumer login/authentication (mobile-app-only, a separate deferred feature); hard delete; NIC
edits after creation; a password-change endpoint for prosumers; bulk actions/row checkboxes;
any mobile-app changes (this is the web-app CRUD surface only, per spec §3.2, distinct from the
mobile app's own prosumer self-registration flow in §4.1).

## Verification

End-to-end manual pass covering: create a prosumer with a valid NIC/email/password; reject an
invalid NIC format, a malformed email, and a too-short password (both backend 400s and frontend
inline errors); duplicate-NIC and duplicate-email conflicts surfaced as toasts; backend search by
NIC and by email (confirm via the network tab that typing quickly produces one debounced request,
not one per keystroke); status filter combined with pagination across more than one page of
results; a Grid Operator account can create/update/deactivate a prosumer but never sees a
Reactivate option, and a direct API call to the reactivate endpoint as Grid Operator returns 403;
a Backoffice account can reactivate; `dotnet test` and `npm run lint`.
