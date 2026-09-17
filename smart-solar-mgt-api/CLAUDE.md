# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project context

This is the **Web Service** component of the Smart Solar Microgrid Trading System, a three-part
system (web app, native Android mobile app, and this API) described in full at
`../project-specification.md` (repo root, one level up from this directory). Read that file for
feature/domain requirements — it is the source of truth for business rules.

Non-negotiable architectural constraints from the spec that apply to this service:

- **FAT service pattern**: all business logic lives in this API. The web and mobile clients are UI
  layers only and must never contain business logic.
- This API is the **only** component that talks to the database (MongoDB, NoSQL). Clients never
  access the database directly.
- Clients communicate with this service **exclusively via REST**.
- Target deployment is a **Windows IIS server**.

Domain rules to keep in mind when implementing endpoints (see the spec for full detail):
- Prosumers are keyed by **NIC** (National Identity Card number), not a generated user id.
- Web app users have two roles: **Backoffice** (system administration) and **Grid Operator**
  (operational tools only) — authorization must distinguish these.
- Deactivated prosumer accounts can only be reactivated by a Backoffice user.
- Microgrid node deactivation must be blocked while active energy reservations exist against that
  node.
- Energy slot reservations must be scheduled within 7 days, and updates/cancellations require at
  least 12 hours' notice — validate these as business rules in the service layer, not just in UI.

The current branch (`feature/user-auth`) is for building user authentication. See
`docs/authentication-implementation-approach.md` for the approved architecture (roles, JWT design,
invitation flow, project structure) behind what's described below.

## Current state of this project

JWT authentication and Backoffice/Grid Operator account management have been implemented
(login/refresh/logout, invitation-based account setup, RBAC, a manually-triggered super-admin
seed command). `MongoDB.Driver`, `Microsoft.AspNetCore.Authentication.JwtBearer`, and `MailKit`
are wired up; `smart-solar-mgt-api.sln` ties this project together with the sibling
`smart-solar-mgt-api.Tests` (xUnit) project. Solar Prosumer auth and a standalone password-reset
endpoint are not yet built (see the approach doc's scope section) — the web frontend's
"reset password" page is actually the invitation-acceptance flow (token + new password),
matching `POST /api/v1/auth/accept-invitation`.

The web frontend is now integrated directly against this API (no BFF layer) using HttpOnly
cookies: `login`/`refresh` set `access_token` (Path `/`) and `refresh_token` (Path
`/api/v1/auth`) cookies via `ICookieAuthService` (`Services/Auth/CookieAuthService.cs`),
additively alongside the JSON-body/bearer response the mobile app uses. A non-HttpOnly
`XSRF-TOKEN` cookie plus `Authorization/CsrfProtectionMiddleware.cs` implement double-submit
CSRF protection for cookie-authenticated mutating requests (bearer-authenticated requests are
exempt). `Cors:AllowedOrigins` (`appsettings.json`) allowlists the frontend origin for
credentialed cross-origin requests — keep it in sync with the frontend's actual origin in every
environment.

## Code documentation requirements

These apply to every `.cs` file added or modified in this project:

- **File header comment block**: each `.cs` file must start with a comment block describing the
  file's purpose (e.g. what it contains and why). Add this to new files and to existing files when
  you significantly modify them.
- **Method-level inline comment**: each method must begin with a brief inline comment describing
  what it does. Keep it short — a one-line summary, not a full doc comment block.

## Commands

Run all commands from this directory (`smart-solar-mgt-api/`).

```bash
# Restore dependencies
dotnet restore

# Run the API locally (reads Properties/launchSettings.json for profiles/ports)
dotnet run                       # default profile
dotnet run --launch-profile http   # http://localhost:5011
dotnet run --launch-profile https  # https://localhost:7260 / http://localhost:5011

# Build
dotnet build

# Watch mode (auto-restart on file changes)
dotnet watch run
```

```bash
# Run the test suite (xUnit, in smart-solar-mgt-api.Tests/)
dotnet test

# Start local MongoDB + MailHog for manual testing (docker-compose.local.yml)
docker compose -f docker-compose.local.yml up -d

# Create the initial Backoffice super-admin account — manual, never runs at app startup
# (requires Seed:SuperAdminEmail/Username/Password to be set, e.g. via dotnet user-secrets)
dotnet run -- seed-admin
```

Secrets (JWT signing key, Mongo connection string, SMTP credentials, seed admin credentials) are
never committed — set them with `dotnet user-secrets` locally, or as IIS App Pool environment
variables (`Section__Key` syntax) in production. See
`docs/authentication-implementation-approach.md`, section 9.

Manual request examples for ad-hoc testing live in `smart-solar-mgt-api.http` (usable with the
VS Code/Rider REST client or `dotnet` HTTP file tooling).

## Tech stack

- .NET 10 / ASP.NET Core minimal APIs (`net10.0`, nullable + implicit usings enabled).
- OpenAPI support via `Microsoft.AspNetCore.OpenApi` (`app.MapOpenApi()`, dev-only).
- Root namespace: `smart_solar_mgt_api`.
- Persistence: MongoDB via `MongoDB.Driver` (see `Data/MongoContext.cs`).
- Auth: `Microsoft.AspNetCore.Authentication.JwtBearer`, framework `PasswordHasher<TUser>` for
  password hashing.
- Email: `MailKit`, targeting MailHog in development (see `docker-compose.local.yml`).
- Tests: xUnit + Moq in the sibling `smart-solar-mgt-api.Tests` project.
