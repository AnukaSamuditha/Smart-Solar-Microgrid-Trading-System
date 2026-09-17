# User Management Feature — Implementation Plan

Status: Approved. This document records the plan for the Backoffice/Grid Operator user
management UI, invitation-flow fixes, login error differentiation, and the frontend theme fix
that was designed and approved before implementation began.

## Context

The backend already implements a working invitation-based account model for two roles —
`Backoffice` and `GridOperator` (there is no separate "Super Admin" role; the seeded admin is
just a `Backoffice` user flagged `IsSeededAdmin`, which only protects that one account from
deactivation/deletion). Create, list, update, delete, deactivate, and reactivate endpoints all
exist and are already gated to `Backoffice` only — Grid Operators have no route beyond `/me`,
which already satisfies "no grid operator should access user management" with no new
authorization work required. Email invitations via MailHog are fully wired end-to-end, and the
frontend already has a working invitation-acceptance UI at `/reset-password` — it just isn't
linked correctly from the invitation email yet.

What was missing: the actual admin-facing UI (the Users page was a stub), a few backend gaps
blocking requested features (a distinguishable "profile incomplete" notice at login, and
`createdAt`/`lastLoginAt` data for two required table columns), and a dark-green panel color
that needed to become neutral black to match the sidebar.

Decisions confirmed with the product owner before implementation:
- **Single-tier permissions**: any `Backoffice` user can create/edit/deactivate/delete both
  Backoffice and Grid Operator accounts. No new "Super Admin" policy is introduced — this
  matches the existing backend design exactly.
- **Status badge added** (Active/Invited/Deactivated) to the table, beyond the 5 explicitly
  named columns (avatar+username, email, role, date added, last accessed).
- Seeded-admin protection (can't deactivate/delete that one account) is handled by surfacing the
  existing `SeededAdminProtected` 409 as a toast, not by hiding the menu item client-side.

## Backend changes (`smart-solar-mgt-api`)

1. `Models/Entities/User.cs` — add `LastLoginAt` (nullable `DateTime`).
2. `Models/Dtos/UserResponse.cs` — add `CreatedAt`/`LastLoginAt` to the response projection.
3. `Services/Users/IUserService.cs` / `UserService.cs` — add `RecordLoginAsync`.
4. `Endpoints/AuthEndpoints.cs` `LoginAsync` — split the collapsed unauthorized condition into
   distinguishable error codes: `InvalidCredentials`, `ProfileIncomplete` (invited, no password
   set yet), `AccountDeactivated`. Record the login timestamp on success.
5. `Services/Users/UserService.cs` — fix the invitation email link to point at `/reset-password`
   (the frontend's actual invitation-acceptance route) instead of the nonexistent `/accept-invite`.
6. `smart-solar-mgt-api.http` — add missing deactivate/reactivate examples and a note on the new
   login error codes.

No EF migrations needed (MongoDB, schema is implicit POCOs); no new authorization policy.

## Frontend changes (`smart-solar-mgt-fe`)

- Install shadcn `table`, `dialog`, `alert-dialog`, `select`, `badge`, `sonner` components
  (Base UI wrappers, matching this project's existing "base-nova" style).
- `lib/api/errors.ts` — add error-code → copy entries for the new login/mutation error codes.
- `lib/api/users.ts` — add list/create/update/deactivate/reactivate/delete API functions.
- `hooks/use-users.ts` (new) — TanStack Query hooks mirroring `hooks/use-login.ts`.
- `lib/validations/user.ts` (new) — zod schemas for create/update forms.
- `components/dashboard/users/*` (new) — table, avatar w/ deterministic gradient initials, role
  and status badges, add/edit dialog, deactivate/delete confirmation dialogs, row-actions menu.
- `app/(dashboard)/users/page.tsx` — replace the stub with the real table UI, rendered directly
  against the page background (no `Card`/`ContentPanel` wrapper, per explicit design requirement).
- `app/globals.css` `.dark` block — replace green-tinted `--card`/`--popover`/`--secondary`/
  `--muted`/`--accent` values with neutral oklch grays matching the existing `--sidebar` family,
  leaving `--primary` (lime `#d9ff43`), `--destructive`, `--ring`, and the login page's
  `.auth-aurora`/`.auth-logo-badge` accents untouched.

## Explicitly out of scope

Prosumer user management (deferred, separate identity model); a distinct "Super Admin"
authorization tier; resend-invitation; bulk actions/row checkboxes; server-side pagination.

## Verification

End-to-end manual pass covering: create → MailHog invitation email → accept invitation → sign
in → dashboard; profile-incomplete notice for an un-accepted invite; account-deactivated notice;
edit with email-conflict handling; deactivate/reactivate/hard-delete with confirmations; client-
side search; visual check of the de-greened panels across all dashboard pages; `dotnet test` and
`npm run lint`.
