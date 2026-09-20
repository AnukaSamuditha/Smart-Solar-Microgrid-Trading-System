# Smart Solar Microgrid Trading System — API

.NET 10 minimal API. Auth, RBAC, invitation-based account setup. See `docs/authentication-implementation-approach.md` for design details and `CLAUDE.md` for repo conventions.

## Prerequisites

- .NET 10 SDK
- Docker + Docker Compose (local MongoDB + MailHog)

## 1. Start local services

```bash
docker compose -f docker-compose.local.yml up -d
```

Starts MongoDB (`27017`) and MailHog (SMTP `1025`, UI `8025`).

## 2. Set secrets

Never committed — use `dotnet user-secrets`:

```bash
dotnet user-secrets set "Jwt:SigningKey" "<32+ char random string>"
dotnet user-secrets set "Mongo:ConnectionString" "mongodb://localhost:27017"
dotnet user-secrets set "Seed:SuperAdminEmail" "admin@example.com"
dotnet user-secrets set "Seed:SuperAdminUsername" "superadmin"
dotnet user-secrets set "Seed:SuperAdminPassword" "<strong password>"
```

Non-secret defaults (issuer, audience, ports, CORS origins) already live in `appsettings.json` / `appsettings.Development.json`.

## 3. Restore & build

```bash
dotnet restore
dotnet build
```

## 4. Seed the initial admin account

One-time, manual — never runs automatically at startup:

```bash
dotnet run -- seed-admin
```

## 5. Run the API

```bash
dotnet run                         # default profile
dotnet run --launch-profile http   # http://localhost:5011
dotnet run --launch-profile https  # https://localhost:7260
dotnet watch run                   # auto-restart on file changes
```

### Reachable from the mobile app / a physical device

The launch profiles above bind to `localhost` only (loopback) — a phone on the same network
can't reach that. To test against a physical Android device (or the emulator via its host-loopback
alias), bind to all interfaces instead:

```bash
dotnet run --urls http://0.0.0.0:5011
```

Then point the mobile app at your machine's LAN IP (`local.properties`' `API_BASE_URL`, e.g.
`http://192.168.1.23:5011`) and make sure a local firewall isn't blocking inbound port `5011`.

## 6. Run tests

```bash
dotnet test
```

## Config quick reference

| Section | Purpose | Secret? |
|---|---|---|
| `Jwt` | signing key, issuer, audience, token lifetimes | `SigningKey` |
| `Mongo` | connection string, database name | `ConnectionString` |
| `Smtp` | MailHog/SMTP settings | `User`, `Password` |
| `Seed` | initial admin credentials (used only by `seed-admin`) | all |
| `Invitation` | invitation token lifetime, frontend base URL | no |
| `Cors` | `AllowedOrigins` — frontend origin(s) allowed for credentialed requests | no |

Production (IIS): set the same keys as environment variables using `Section__Key` syntax (e.g. `Jwt__SigningKey`).

## Auth model (web + mobile)

- Mobile/API clients: `Authorization: Bearer <accessToken>` from the login/refresh JSON body.
- Web frontend: `HttpOnly` cookies (`access_token`, `refresh_token`) set automatically by `/auth/login` and `/auth/refresh`.
- CSRF: web clients must echo the readable `XSRF-TOKEN` cookie back as an `X-XSRF-TOKEN` header on mutating requests. Bearer-authenticated requests are exempt.
- Keep `Cors:AllowedOrigins` in sync with the actual frontend origin in every environment — required for cookies to work cross-origin.

## Key endpoints

```
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/accept-invitation

GET    /api/v1/users/me
POST   /api/v1/users/backoffice        (Backoffice only)
POST   /api/v1/users/grid-operators    (Backoffice only)
GET    /api/v1/users                   (Backoffice only)
PATCH  /api/v1/users/{id}/deactivate   (Backoffice only)
PATCH  /api/v1/users/{id}/reactivate   (Backoffice only)
```

Manual request examples: `smart-solar-mgt-api.http`.

## Troubleshooting

- **401 on every request**: check `Jwt:SigningKey` is set and matches what issued the token.
- **CORS error in browser**: add the frontend origin to `Cors:AllowedOrigins`.
- **403 `CsrfValidationFailed`**: web client is missing the `X-XSRF-TOKEN` header on a mutating request.
- **Mongo connection refused**: confirm `docker compose -f docker-compose.local.yml up -d` is running.
- **Mobile app says "Can't reach the server"**: the default launch profiles bind to `localhost` only, which a phone can't reach — run with `dotnet run --urls http://0.0.0.0:5011` instead (see "Reachable from the mobile app" above), and confirm `local.properties`' `API_BASE_URL` on the mobile side points at your machine's actual LAN IP, not `localhost`.
