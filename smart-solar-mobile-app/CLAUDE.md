# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A native Android app (Kotlin, package `com.example.smart_solar_mgt_app`, minSdk 26 / target & compileSdk 37) for a smart solar microgrid energy-trading system. **Prosumers** reserve a time slot and energy amount at a solar charging station; **Grid Operators** approve reservations and complete the physical energy transfer via a QR-code handshake. The app was originally **offline-first** with no backend, but is now being integrated with the real C# Web API (`smart-solar-mgt-api`) incrementally, in phases, without a wholesale rewrite - see `di/ServiceLocator.kt` comments and section 8 of `SMART_SOLAR_APP_REPORT.md` for the original design intent.

**Auth/account lifecycle now talks to the real backend** (`core/network/`, `data/repository/RemoteAuthRepository*.kt`, `RemoteProsumerRepository*.kt`, plain OkHttp + `org.json` - no Retrofit/Moshi, matching this project's "no added framework layers" preference). `core/network/ApiConfig.kt` resolves the base URL from `local.properties`' `API_BASE_URL` (defaults to the emulator's `10.0.2.2:5011`). Booking/reservation/QR flows are still 100% local SQLite, not yet integrated.

- **Grid Operator login** is real backend auth (`POST /api/v1/auth/login`) - no local seeded account exists any more (removed in `DatabaseHelper`'s v3→v4 migration).
- **Prosumer self-registration** (`RegisterActivity`) submits directly to `POST /api/v1/prosumers/register` (no password collected) and writes nothing locally; the account starts `PendingApproval`.
- **Grid Operators review self-registered prosumers** from the mobile app itself: `ui/gridoperator/prosumers/PendingProsumersFragment` (a 5th bottom-nav tab, "Requests") lists `PendingApproval` requests and calls approve/deny, mirroring `OperatorHomeFragment`'s pending-bookings pattern.
- **Prosumer login** (`SecurityManagerImpl.login`) tries local SQLite first, then remote: a NIC-shaped identifier goes straight to `POST /api/v1/auth/prosumer/login`; an email-shaped one tries staff login first and falls back to prosumer login only on the specific `ProsumerMobileOnly` signal (see the method's comment for why that's safe).
- **`PendingActivationActivity`/`AccountDeniedActivity`/`ResetPasswordActivity`** are the three new terminal/utility screens a prosumer login attempt can route to (`PendingApproval`/`AccountCreationDenied`/`PasswordNotSet`). Reset Password re-uses the same `POST /api/v1/auth/accept-invitation` endpoint the web invitation flow uses.
- **`ResetPasswordActivity` is a deep-link target**: `solarsync://reset-password?token=...` (custom URI scheme, not a domain-verified Android App Link - this project has no real hosted domain, only `http://localhost:3000` in dev) opens the screen directly with the code pre-filled and the code field hidden. `ProsumerMobileApprovalEmailTemplate` emails this link as a button (plus the raw code as a fallback for opening on a desktop). There's no manual "set your password" link on the Login screen any more - reaching this screen is either via that email link or the automatic `PasswordNotSet` redirect from a login attempt.
- **SolarSync branding** now matches the web app throughout: `activity_login.xml`/`activity_splash.xml` use the lime-gradient rounded-square badge (`bg_solarsync_logo_badge`/`_large`) + the lucide "Zap" bolt mark (`ic_solarsync_logo`) + "SolarSync" wordmark - both previously used a plain sun icon with no relation to the actual brand mark used everywhere on `smart-solar-mgt-fe`. The adaptive launcher icon (`drawable/ic_launcher_background.xml`/`ic_launcher_foreground.xml`) is the same badge/bolt, and `R.string.app_name` is now "SolarSync" (was the literal `smart-solar-mgt-app` project-name placeholder). The legacy per-density `.webp` launcher icons under `mipmap-*dpi/` were left untouched and still show the old default Android Studio robot mark - they're dead weight given `minSdk 26` (the adaptive `mipmap-anydpi/ic_launcher.xml` always wins on API 26+) and regenerating raster images isn't a text-editing task; only worth fixing if something outside this API-level guarantee ever reads them directly (e.g. a Play Store listing asset pipeline).
- **Registration and Grid-Operator approve/deny are now offline-safe**, via a real `core/sync/SyncManager.kt` + `SyncWorker.kt` (WorkManager `CoroutineWorker`, `NetworkType.CONNECTED` constraint) draining a generic `sync_outbox` table (`core/db/DatabaseContract.kt`) - the `SyncManager`/outbox that `ServiceLocator`'s original comment and `SMART_SOLAR_APP_REPORT.md` section 8 described as designed-but-not-built now exists, for this feature at least (bookings/QR are still not wired to it). `RegisterViewModel`/`PendingProsumersFragment` write locally + enqueue an outbox row and return immediately regardless of connectivity; `SmartSolarApp.onCreate` schedules a 15-minute periodic safety-net sync, and every local-first write also calls `SyncManager.scheduleImmediateSync()` so the online case doesn't wait for it. A definitive server rejection (e.g. NIC already in use) marks the outbox row `SYNC_FAILED` (kept, never retried) rather than disappearing silently; `PendingActivationActivity` shows that state when reached right after registering. `users.password_hash` is nullable now (v4→v5 migration) since a self-registered row has none until reset - `SecurityManagerImpl.login` always defers such a row to the remote endpoint rather than trust a stale local guess.
- **Expired-access-token recovery**: `RemoteProsumerRepositoryImpl`'s authenticated calls (list/approve/deny) retry once through `RemoteAuthRepository.refreshSession()` (`POST /api/v1/auth/refresh`) on a 401 before giving up, mirroring the web frontend's silent-refresh interceptor (`smart-solar-mgt-fe/providers/api-client.ts`). If the refresh token itself is expired/revoked, `SyncWorker` treats the residual 401 as transient (stays `PENDING_SYNC`, not `SYNC_FAILED`) rather than abandoning the queued action - it resolves on its own once the Grid Operator logs in again.
- **Remote-authenticated prosumer profile caching**: every prosumer-facing screen (`HomeFragment`, `ProfileFragment`, booking screens) reads the local `users` table only - they predate remote prosumer auth and were never updated. A prosumer authenticated via `POST /api/v1/auth/prosumer/login` had no guaranteed local row (and `session.userId` could end up being whatever they *typed* to log in - email or NIC - rather than the canonical NIC every local table keys off), which surfaced as "Welcome, `<email>`" on Home and "session no longer valid" on Profile. Fixed via a new backend endpoint `GET /api/v1/prosumers/me` (`RoleNames.Prosumer` policy, NIC from the JWT `sub` claim) that `SecurityManagerImpl.loginRemoteProsumer` calls right after login to (a) fix `session.userId` to the real NIC and (b) upsert a local profile-only cache row via `AuthRepository.upsertProsumerProfileCache` (name/email/phone/address; `passwordHash` stays null so `SecurityManagerImpl.login`'s existing "no local password → defer to remote" rule is untouched). A fetch failure degrades to the old behavior rather than blocking login.

Two full-code READMEs already exist and are the best first read for deep detail:
- `SMART_SOLAR_APP_REPORT.md` — architecture, data layer, security, per-role feature walkthrough.
- `UI_LAYOUT_COLOR_REPORT.md` — every screen's layout, view IDs, and exact colors (Material3 default theme, no custom overrides; status colors are hardcoded hex per status, e.g. `#2E7D32` confirmed, `#F9A825` pending — defined identically in `BookingsAdapter`, `OperatorBookingsAdapter`, and `BookingActionSummaryFragment`).

## Commands

Standard Gradle Android project (no Kotlin DSL script tasks beyond the defaults).

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease

# Unit tests (JVM, app/src/test)
./gradlew testDebugUnitTest
./gradlew test --tests "com.example.smart_solar_mgt_app.SomeClassTest"   # single test class

# Instrumented tests (app/src/androidTest, needs a device/emulator)
./gradlew connectedDebugAndroidTest

# Lint
./gradlew lint

# Install debug build (includes the extra debug-only approval tool activity)
./gradlew installDebug
```

`local.properties` must define the Google Maps API key referenced by the `secrets-gradle-plugin` (used by `ui/prosumer/map` and `ui/gridoperator/map`) — it is gitignored.

Gradle JDK toolchain is pinned to **25** (`gradle/gradle-daemon-jvm.properties`); AGP is 9.3.2. There is no separate Kotlin Gradle plugin declared — Kotlin support comes bundled with AGP's built-in Kotlin DSL support at this version.

## Architecture

Strict one-directional layering, enforced by convention only (no DI framework, no Room):

```
Activity / Fragment  →  ViewModel  →  Repository (interface + *Impl)  →  LocalDbManager  →  SQLite (DAOs)
```

- **UI never touches SQLite directly.** Fragments/Activities call only a `ViewModel`; ViewModels call only a `data/repository/*Repository` interface (looked up via `ServiceLocator`, never constructed inline).
- **`core/db/LocalDbManager.kt`** is the *only* class allowed to open the database. It owns cursor↔model mapping and wraps every multi-table write in a single SQLite transaction. This is where real business-rule enforcement happens — always by re-checking state *inside* the transaction, never trusting a value the caller read moments earlier (e.g. `createBooking` re-fetches the station and checks `availableSlots > 0` inside the transaction to prevent a double-booking race).
- **Manual DI via `di/ServiceLocator.kt`** — a single Kotlin `object` with `by lazy` properties for every manager/repository, initialized once in `SmartSolarApp.onCreate()`. New managers/repositories get added here as they're implemented, wired by hand (`ServiceLocator.bookingRepository`, etc.) — no Hilt/Dagger.
- **`core/common/Resource.kt`** — `AppResult<T>` (`Success`/`Failure`, deliberately not named `Result` to avoid colliding with `kotlin.Result`) plus a sealed `AppError` (`NotFound`, `Unauthorized`, `InvalidStatusTransition`, `TooLateToModify`, `UniqueConstraintViolation`, `Unknown`). **Repository methods return `AppResult` for expected business failures and never throw for those** — but *do* throw `UnauthorizedAccessException` for role violations, since that's a security error, not a normal outcome callers should have to unwrap.
- **No Room, no Flow, no Hilt** — deliberately raw `SQLiteOpenHelper` + DAOs, `LiveData` + coroutines (`Dispatchers.IO` + `viewModelScope`/`lifecycleScope`), and Navigation Component with a `BottomNavigationView` per role (`nav_prosumer.xml` / `nav_operator.xml`).
- **Sync is designed but not implemented.** Every writable row carries a `sync_status` column (`LOCAL_ONLY`/`PENDING_SYNC`/`SYNCED`/`SYNC_FAILED`) for a future WorkManager-based outbox, but nothing currently reads it except a UI "pending" icon.
- One reusable `BookingsFragment`/`BookingScope` combo backs three tabs (Current/Pending/History) for *both* the Prosumer and Grid Operator bookings screens via in-memory filtering over one JOIN query, rather than separate fragments or separate SQL queries per tab.

### Data layer

Single DB file `smart_solar.db` (`core/db/DatabaseHelper.kt`, a plain `SQLiteOpenHelper`, foreign keys turned on explicitly). Table/column names are centralized in `core/db/DatabaseContract.kt`. Tables: `users` (both roles, `nic` PK, PBKDF2 hash), `stations` (5 seeded, fixed Sri Lanka locations), `bookings`, `transactions` (the QR "Energy Transfer Pass"), `session` (non-authoritative mirror — never used for auth decisions). DAOs (`core/db/dao/*.kt`) are thin `ContentValues`/`Cursor` mappers only; all business logic lives in `LocalDbManager` or the repositories above it.

### Security model

- Passwords: PBKDF2WithHmacSHA256, 120k iterations, self-describing stored format (`core/security/PasswordHasher.kt`), constant-time verification (`MessageDigest.isEqual`).
- Session: `core/security/SecureSessionStore.kt` (`EncryptedSharedPreferences`, AES-256-GCM) is the **sole authoritative** source of who's logged in as what role.
- Role enforcement happens twice: in the UI (navigation) and again in the repository layer via `core/security/RoleGuard.kt`, so calling an operator-only repository method directly still fails safely. `OperatorMainActivity` additionally re-checks role in `onCreate` since the whole Activity is a role boundary.
- QR tokens: `core/security/QrTokenService.kt`, HMAC-SHA256 signed with an Android Keystore key (never exported). Payload is `transactionId|bookingId|expiryMillis` — deliberately no PII. Always check signature (`Tampered`) before expiry (`Expired`). TTL is the single shared constant in `util/TransactionRules.kt`.

### Notable per-role structure

- **Prosumer** (`ui/prosumer/`, single `ProsumerMainActivity`): Home, Bookings, Map, Profile tabs + New Booking (same form/fragment for create *and* modify) + QR Pass screens.
- **Grid Operator** (`ui/gridoperator/`, single `OperatorMainActivity`, seeded account only — `OP0000001` / `Operator@123`, no self-registration): Home (pending-approval queue), Scan QR, Bookings (read-only, cross-prosumer), Map (reuses Prosumer's `MapViewModel`/`StationListAdapter`/`StationMarkerIcons` directly since they're pure display logic).
- QR scan-to-complete is deliberately two steps: `TransactionRepository.verifyToken` (read-only) shows a confirmation sheet, then an explicit "Complete Transfer" tap calls `completeTransfer`, which re-checks both statuses again inside its own transaction.
- **`app/src/debug/`** is a separate Gradle source set (a standalone debug-only approval Activity, absent from release builds entirely) — predates the real Operator Home dashboard and is now redundant but not yet removed. Calls the same `BookingRepository` methods as the real UI.

## Conventions to preserve

- Every Activity's root layout needs `android:fitsSystemWindows="true"` — target SDK 37 enforces edge-to-edge by default; omitting it has previously caused top-anchored views to render under the status bar and silently eat touch events.
- Use `ListAdapter` + `DiffUtil` for RecyclerViews, not manual `notifyDataSetChanged()`.
- New business-rule validation belongs in a dedicated validator class re-checked at two layers: UI-level (form constraints) and inside the relevant `LocalDbManager` transaction — never trust the UI layer alone.
