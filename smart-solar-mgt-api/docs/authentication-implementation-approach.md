# Authentication & User Account Management — Implementation Approach

Status: **Proposed — pending review/approval.** No implementation plan or code exists yet; this document only.

## 1. Overview & Scope

**In scope (this feature):**
- Backoffice and Grid Operator account management and login.
- Invitation-based account setup (no admin-assigned passwords).
- JWT access/refresh token authentication.
- Role-based authorization (Backoffice vs. Grid Operator).
- Seeded initial administrative account.
- MailHog-backed dev email for invitations.

**Explicitly out of scope (future features):**
- Solar Prosumer authentication (mobile-only, NIC-keyed — separate identity model per `project-specification.md`).
- Password reset flow (the invitation-token mechanism is designed to be reusable for this later).
- Fine-grained permissions beyond role checks.
- Email verification independent of the invitation flow.

## 2. Roles & RBAC

Two roles for now, matching `project-specification.md` exactly: **`Backoffice`** and **`GridOperator`** (enum-backed field on `User`). No permission matrix yet — unwarranted at two roles.

- Role is embedded as a `role` claim (`ClaimTypes.Role`) in the access token, enabling standard `[Authorize(Roles = "Backoffice")]` / policy-based checks.
- Role name strings are centralized in one place (`Authorization/RoleNames.cs`) rather than scattered as magic strings.
- **Backoffice**: can create Backoffice and Grid Operator accounts; manages account lifecycle (activate/deactivate).
- **Grid Operator**: can log in and manage only their own account; no user-management capability exists in their claim set (not just blocked by a 403 — no route is authorized for them).

**Extensibility path (not built now):** if fine-grained permissions are needed later, add a `permissions` claim array and introduce policy handlers alongside the existing role checks — this requires no change to token issuance or existing endpoints.

## 3. Initial Super Admin Account (Manual Seed)

The spec does not define a "Super Admin" role — `Backoffice` already denotes full system administration. The seeded account is therefore a normal `Backoffice` user, distinguished only by an `IsSeededAdmin` flag (safety marker to prevent it from being accidentally deactivated — not a separate role).

Seeding is a **manual, developer-triggered operation** — it never runs automatically as part of normal application startup. This keeps account creation an explicit, deliberate act per environment rather than something that happens implicitly on every deploy or restart.

- Triggered via a CLI switch on the same executable (`dotnet run -- seed-admin`), checked in `Program.cs` before the web host starts: if present, only the services needed for seeding are built, the seed routine runs once, and the process exits without calling `app.Run()`. This reuses the existing DI/config wiring rather than requiring a separate script or console project.
- Reads `Seed__SuperAdminEmail`, `Seed__SuperAdminUsername`, `Seed__SuperAdminPassword` from configuration at invocation time.
- Idempotent: looks up by normalized `Email`; if found, logs and exits without changes — safe to run repeatedly. If not found, hashes the password (same path as any other user) and inserts.
- A unique index on `Users.Email` guards against races on repeated/concurrent manual runs.
- If the required env vars are missing when the command is invoked, it fails fast with a clear error — since this is now an explicit action, silent no-ops are no longer appropriate.

## 4. Invitation-Based Account Setup

```
Backoffice user → Create account (email, role) → User doc created (Status=Invited, no password)
    → Generate invitation token (32 random bytes) → persist only SHA-256 hash + expiry (48h default)
    → Email invitation link (raw token, shown once) → user opens link
    → POST accept-invitation {token, newPassword} → hash token, look up, check not expired/used
    → atomically mark used → set PasswordHash, Status=Active → user can log in
```

- Applies identically to Backoffice-created Backoffice accounts and Grid Operator accounts.
- Token consumption is atomic (single update filtered on `UsedAt == null`) to prevent double-use races.
- Expired or already-used tokens are rejected.

## 5. Email Abstraction & MailHog

- `IEmailSender` — a minimal, invitation-agnostic interface (`SendAsync(to, subject, htmlBody)`) so it's reusable for any future transactional email (e.g. password reset later).
- `SmtpEmailSender` implementation using **MailKit**, configured entirely via env vars (`Smtp__Host`, `Smtp__Port`, `Smtp__From`, `Smtp__User`, `Smtp__Password`, `Smtp__UseSsl`).
- Local dev: `docker-compose.local.yml` runs **MailHog** (SMTP `1025`, web UI `8025`) alongside a local MongoDB — not used in production (IIS deployment has no containers).
- Swapping to a production provider later means adding one new `IEmailSender` implementation and changing DI registration — no change to calling code.

## 6. JWT Design

**Access token**
- Lifetime: 15 minutes (configurable).
- Claims: `sub`, `email`, `role`, `jti`, `iat`, `exp`, `iss`, `aud` — no sensitive data (no password hash, no PII beyond email).
- Validated via standard `JwtBearer` middleware: issuer, audience, lifetime, and signing key are all checked, tight clock skew.

**Signing: HMAC-SHA256 (symmetric key), via `Jwt__SigningKey` env var.**
Rationale: per the mandated FAT-service architecture, this API is the *only* component that ever validates these tokens — there's no second service needing independent public-key verification, so a shared secret is simpler to operate on IIS than certificate-based key rotation, with no security downside here. If that topology changes later, moving to RS256 is a contained change (all JWT config is already externalized).

**Refresh token**
- Opaque random value (not a JWT), 7-day lifetime (configurable), persisted server-side with only its SHA-256 hash stored.
- **Rotation-on-use**: each `/auth/refresh` call issues a new access+refresh pair and revokes the old refresh token, linked via a `FamilyId`.
- **Reuse detection**: presenting an already-rotated (revoked) token revokes the entire token family and forces re-login — mitigates stolen-token replay.
- **Logout**: revokes the specific refresh token presented. Access tokens are not individually revocable (no blacklist); the short 15-minute lifetime is the accepted mitigation — noted as a deliberate tradeoff, with a `jti` blacklist collection flagged as a possible future enhancement.

**Token delivery: JSON body now, HttpOnly cookie support added later without a redesign.**
- **Phase 1 (this feature — backend only, no web frontend yet)**: login/refresh responses return both tokens in the JSON body, consumed as `Authorization: Bearer <token>`. This is what the native Android app needs (no browser cookie jar), and it's also the simplest path to exercise/test via `.http` files or Postman before any web client exists.
- Token validation is wired through `JwtBearer`'s `OnMessageReceived` event so the access token can be read from **either** the `Authorization` header **or** a cookie — the header path is active today; the cookie path is simply unused until something sets that cookie.
- **Future (once the web frontend is built)**: login/refresh will *additionally* set the access and refresh tokens as `HttpOnly`, `Secure`, `SameSite=Strict` cookies for the browser client, without removing the JSON-body/bearer path the mobile app depends on — an additive change to the auth endpoints, not a change to the signing/validation core.
- Because cookies are sent automatically by the browser, adopting them will also require CSRF protection (`SameSite=Strict` plus a double-submit or anti-forgery token) — flagged now as a known dependency of that later step, not built in this feature.
- The native Android app is expected to keep using bearer tokens (stored in EncryptedSharedPreferences/Keystore) rather than cookies, since a native app doesn't share a browser's cookie model.

## 7. Password Security

**`Microsoft.AspNetCore.Identity.PasswordHasher<TUser>`** used standalone (PBKDF2-HMACSHA256) — not the full ASP.NET Core Identity membership system, which would conflict with the custom Mongo-backed, invitation-driven flow.

Rationale: ships in the shared framework already referenced (no extra dependency), Microsoft-maintained, versioned hash format supports future algorithm upgrades. Same hashing path is used for the seeded account and all invited users — no weaker special case.

## 8. Token & Invitation Generation Security

Shared pattern for both invitation tokens and refresh tokens:
- Generated via `RandomNumberGenerator.GetBytes(32)` (256 bits of entropy), base64url-encoded for transport.
- Only the SHA-256 hash is ever persisted — the raw value is shown/transmitted exactly once.
- Single-use and time-limited, enforced via atomic conditional updates (no TOCTOU races).

## 9. Configuration & Secrets

| Section | Keys | Secret? |
|---|---|---|
| `Jwt` | `SigningKey`, `Issuer`, `Audience`, `AccessTokenLifetimeMinutes`, `RefreshTokenLifetimeDays` | `SigningKey` |
| `Mongo` | `ConnectionString`, `DatabaseName` | `ConnectionString` |
| `Smtp` | `Host`, `Port`, `From`, `User`, `Password`, `UseSsl` | `User`, `Password` |
| `Seed` | `SuperAdminEmail`, `SuperAdminUsername`, `SuperAdminPassword` | all |
| `Invitation` | `TokenLifetimeHours` | no |

- Non-secret defaults live in `appsettings.json` / `appsettings.Development.json`.
- **Secrets are never committed.** Local dev: `dotnet user-secrets`. Production (IIS): environment variables set on the App Pool/site, using the ASP.NET Core Module's `__` double-underscore nesting convention (e.g. `Jwt__SigningKey`).
- A committed `.env.example` documents variables needed only for the local `docker-compose.local.yml` stack (MailHog/Mongo); real `.env` files stay gitignored.

## 10. Project Structure

Single-project, folder-based layering — not a multi-project "Clean Architecture" split. The FAT-service pattern already centralizes all logic in this one API; there's no second consumer that would benefit from separately-compiled layers, so extra project indirection would add cost with no present payoff. A `.sln` is added now (near-zero cost, expected tooling) alongside one sibling test project.

Endpoint style: **minimal API endpoint groups** (`MapGroup`), continuing the existing template's style — .NET 10 minimal APIs have route-group, filter, and OpenAPI parity with MVC controllers, so controllers would be an unnecessary addition.

```
smart-solar-mgt-api/
├── smart-solar-mgt-api.sln                 [new]
├── smart-solar-mgt-api.csproj
├── Program.cs                              (composition root: DI, pipeline, endpoint mappings)
├── appsettings.json
├── appsettings.Development.json
├── CLAUDE.md
├── Endpoints/
│   ├── AuthEndpoints.cs                    # login, refresh, logout, accept-invitation
│   └── UserEndpoints.cs                    # create-backoffice, create-grid-operator, me, list, deactivate/reactivate
├── Services/
│   ├── Auth/                               # JwtTokenService, RefreshTokenService, PasswordHasherService, InvitationService
│   ├── Email/                              # IEmailSender, SmtpEmailSender
│   ├── Users/                              # UserService
│   └── Seed/                               # SuperAdminSeeder
├── Models/
│   ├── Entities/                           # User, RefreshToken, Invitation
│   ├── Enums/                              # UserRole, UserStatus
│   └── Dtos/                               # request/response models
├── Data/                                   # MongoContext, MongoOptions
├── Authorization/                          # RoleNames
├── Configuration/                          # JwtOptions, SmtpOptions, SeedOptions, InvitationOptions
├── Properties/launchSettings.json
├── docker-compose.local.yml                [new — dev only: MailHog + MongoDB]
├── .env.example                            [new]
└── docs/
    └── authentication-implementation-approach.md

smart-solar-mgt-api.Tests/                  [new sibling project]
├── smart-solar-mgt-api.Tests.csproj        # xUnit, references main project
└── Services/                               # unit tests per service
```

Every new `.cs` file carries a file-header comment block and a one-line inline comment at the start of each method, per this repo's `CLAUDE.md`.

## 11. MongoDB Collections (Conceptual)

- **`Users`** — `Email` (unique index, login identifier), `Username` (display only), `PasswordHash` (nullable until setup complete), `Role`, `Status` (`Invited`/`Active`/`Deactivated`), `IsSeededAdmin`, `CreatedAt`/`CreatedBy`, `UpdatedAt`/`UpdatedBy`.
- **`RefreshTokens`** — `UserId`, `TokenHash` (unique index), `FamilyId`, `ExpiresAt` (TTL index), `RevokedAt`, `ReplacedByTokenHash`.
- **`Invitations`** — `UserId`, `TokenHash` (unique index), `ExpiresAt` (TTL index), `CreatedBy`, `UsedAt`.

Audit fields kept minimal; a full audit-log/history collection is a separate future concern.

## 12. API Endpoints (Conceptual)

```
Auth
├── POST /api/v1/auth/login
├── POST /api/v1/auth/refresh
├── POST /api/v1/auth/logout
└── POST /api/v1/auth/accept-invitation

User Management (Backoffice-only unless noted)
├── POST   /api/v1/users/backoffice
├── POST   /api/v1/users/grid-operators
├── GET    /api/v1/users                    (list)
├── GET    /api/v1/users/me                 (any authenticated user, own profile)
├── PUT    /api/v1/users/{id}                (update email/username)
├── DELETE /api/v1/users/{id}                (permanent delete)
├── PATCH  /api/v1/users/{id}/deactivate
└── PATCH  /api/v1/users/{id}/reactivate
```

Exact request/response contracts and authorization policy names are defined in the detailed implementation plan, not here.

## 13. Account Lifecycle

```
Invited → (accept-invitation) → Active ⇄ (Backoffice deactivate/reactivate) → Deactivated
```

`DELETE /api/v1/users/{id}` sits outside this state machine — it permanently removes the account
(and its refresh tokens/invitations) rather than transitioning its status, and is blocked for the
seeded super-admin account the same way deactivation is.

Password reset is future work; the invitation-token mechanism (random token, hashed at rest, single-use, time-limited) is directly reusable via a `Purpose` discriminator when that flow is built.

## 14. NuGet Packages

- `MongoDB.Driver` — persistence.
- `Microsoft.AspNetCore.Authentication.JwtBearer` — bearer token validation.
- `MailKit` — SMTP client for `SmtpEmailSender`.
- No password-hashing package (uses the framework-provided `PasswordHasher<TUser>`); no FluentValidation (DataAnnotations suffice at this scope).
- Test project: `xunit`, `xunit.runner.visualstudio`, `Microsoft.NET.Test.Sdk`, `Moq`.

## 15. Documentation & Coding Conventions

All new `.cs` files follow this repo's `CLAUDE.md` requirements: a file-header comment block describing the file's purpose, and a one-line inline comment at the start of every method.

---

**Next step:** upon approval of this document, a detailed implementation plan (endpoint contracts, exact data models, task breakdown) will be produced for separate review before any code is written.
