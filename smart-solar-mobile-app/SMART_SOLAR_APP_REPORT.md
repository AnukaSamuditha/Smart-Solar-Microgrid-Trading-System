# Smart Solar Microgrid Trading System — App Report

## 1. What the app is

A native Android app (Kotlin, package `com.example.smart_solar_mgt_app`, min/target SDK 37) that lets **Prosumers** (solar energy owners) reserve a time slot and energy amount at a solar charging/trading station, and lets **Grid Operators** approve those reservations and complete the physical energy transfer via a QR-code handshake. The app is **offline-first**: every feature works against a local SQLite database with no backend yet — a future C# Web API is meant to be swapped in later without touching UI or business logic.

Two roles, two completely separate app experiences after login:
- **Prosumer**: register, log in, browse stations (list + Google Map), create/modify/cancel reservations, view reservation history, view an "Energy Transfer Pass" QR code once a reservation is approved.
- **Grid Operator** (seeded account only, no self-registration): log in, see pending reservations and approve/reject them, browse a read-only overview of all reservations, view the station map, and scan a prosumer's QR code with the camera to complete the energy transfer.

## 2. Architecture — the shape of the codebase

Strict one-directional layering, enforced by convention (no framework forces it, but every piece follows it):

```
Activity / Fragment  →  ViewModel  →  Repository  →  LocalDbManager  →  SQLite (DAOs)
```

- **UI never touches SQLite directly.** Fragments/Activities only ever call a `ViewModel`, which only ever calls a `data/repository/*Repository` interface.
- **`LocalDbManager`** (`core/db/LocalDbManager.kt`) is the *only* class allowed to open the database. It owns cursor↔model mapping and wraps every multi-table write in a single SQLite transaction (`db.beginTransaction()/endTransaction()`).
- **Manual dependency injection** via `di/ServiceLocator.kt` — a single Kotlin `object` with `by lazy` properties for every manager/repository. No Hilt/Dagger; everything is wired by hand and looked up as `ServiceLocator.bookingRepository`, etc.
- **`core/common/Resource.kt`** defines `AppResult<T>` (`Success`/`Failure`) and `AppError` (a sealed class: `NotFound`, `Unauthorized`, `InvalidStatusTransition`, `TooLateToModify`, `UniqueConstraintViolation`, `Unknown`). Named `AppResult` rather than `Result` specifically to avoid colliding with Kotlin's built-in `kotlin.Result`. Every write operation that can fail for a business reason (not a crash) returns one of these instead of throwing.
- **No Room, no Kotlin Flow, no Hilt.** The project deliberately uses the more manual/classic tools: raw `SQLiteOpenHelper`, `LiveData` + Kotlin coroutines (`Dispatchers.IO` + `viewModelScope`/`lifecycleScope`), and Navigation Component with a `BottomNavigationView` per role.
- **Sync/network layer is designed but not built.** Comments in `ServiceLocator` and old memory notes describe a future `CommunicationManager` / `SyncManager` / WorkManager-based outbox for when the real backend ships, and every writable row already carries a `sync_status` column (`LOCAL_ONLY` / `PENDING_SYNC` / `SYNCED` / `SYNC_FAILED`) for that purpose — but no networking code exists yet. Today `sync_status` is written but never read by anything except the UI (small "sync pending" icon).

## 3. Data layer

**Database:** one file, `smart_solar.db`, version 1, created by `core/db/DatabaseHelper.kt` (a classic `SQLiteOpenHelper`). Foreign keys are turned on explicitly (`setForeignKeyConstraintsEnabled(true)`), which SQLite doesn't do by default.

**Tables** (all names/columns centralized in `core/db/DatabaseContract.kt` to avoid stringly-typed literals scattered around):

| Table | Purpose | Key columns |
|---|---|---|
| `users` | Both roles in one table | `nic` (PK), `role` (`PROSUMER`/`GRID_OPERATOR`), `account_status`, PBKDF2 password hash |
| `stations` | Solar charging stations | `station_id` (PK), lat/lng, `capacity_kwh`, `available_slots`, `status` (`ACTIVE`/`MAINTENANCE`/`OFFLINE`/`FULL`) |
| `bookings` | A reservation | `booking_id` (PK), `prosumer_nic` (FK→users), `station_id` (FK→stations), date/time, `energy_amount`, `status` (`PENDING`/`CONFIRMED`/`CANCELLED`/`COMPLETED`/`EXPIRED`), `sync_status` |
| `transactions` | The QR "Energy Transfer Pass" issued once a booking is approved | `transaction_id` (PK), `booking_id` (FK), `qr_token`, `status` (`GENERATED`/`SCANNED`/`COMPLETED`/`EXPIRED`/`CANCELLED`), `operator_id`, timestamps |
| `session` | Non-authoritative mirror of the logged-in user | single row, role + login state |

The database is seeded on first creation with **5 fixed stations** across Sri Lanka (Colombo, Kandy, Galle, Jaffna, Kurunegala — one deliberately `FULL`, one `MAINTENANCE`) and **one Grid Operator test account** (`OP0000001` / `Operator@123`) since operators can't self-register.

**DAOs** (`core/db/dao/*.kt`) are thin, one per table (`UserDao`, `StationDao`, `BookingDao`, `TransactionDao`, `SessionDao`) — each does raw `ContentValues`/`Cursor` mapping and nothing else. All actual business rules live one layer up, in `LocalDbManager` or the repositories.

**Atomic operations in `LocalDbManager`** — the places where correctness actually gets enforced, always by re-checking state *inside* the transaction rather than trusting a value the caller read moments earlier:
- `createBooking` — re-fetches the station row inside the transaction and checks `status`/`availableSlots > 0` there, so two simultaneous last-slot bookings can't both succeed.
- `updateBooking` / `cancelBooking` — re-verify ownership (`prosumerNic` match), status, and the 12-hour notice rule inside the transaction.
- `approveBooking` — `PENDING → CONFIRMED` **and** generates the QR transaction row atomically (one DB transaction, so a booking can never end up "confirmed with no pass" or "pass exists but booking still pending").
- `rejectBooking` — `PENDING → CANCELLED`, restores the station slot.
- `completeTransaction` — the QR-scan completion: re-checks the transaction is still `GENERATED` and the booking still `CONFIRMED`, then flips both to `COMPLETED` in one transaction (protects against re-scanning an already-used pass, or completing a booking whose reservation was cancelled after the pass was issued).

## 4. Security

- **Passwords**: PBKDF2WithHmacSHA256, 120,000 iterations, random 16-byte salt, stored in a self-describing string format `PBKDF2$iterations$saltB64$hashB64` (`core/security/PasswordHasher.kt`) so the iteration count can be raised later without invalidating old hashes. Verification uses `MessageDigest.isEqual` (constant-time), not `==`.
- **Session**: `core/security/SecureSessionStore.kt` wraps `EncryptedSharedPreferences` (AES-256-GCM master key) and is the **sole authoritative** source of "who is logged in as what role." The `session` SQLite table is written alongside it but is explicitly documented and treated as a non-authoritative convenience mirror — nothing makes an auth decision from it.
- **Role enforcement happens twice**: once in the UI (which screen you can navigate to) and again inside the Repository layer via `core/security/RoleGuard.kt` (`RoleGuard.enforce(session, requiredRole)` throws `UnauthorizedAccessException` if the session is missing or wrong-role). This means calling a Grid-Operator-only repository method directly — bypassing the UI entirely — still fails safely. `OperatorMainActivity` additionally re-checks the role itself in `onCreate` (an Activity-level guard) since an entire Activity, not just a button, is a role boundary there.
- **QR tokens** (`core/security/QrTokenService.kt`): HMAC-SHA256, signed with a key that lives only in the Android Keystore (`AndroidKeyStore`, never exported). Token format is `base64(payload).base64(signature)` where the payload is `transactionId|bookingId|expiryMillis` — deliberately **no PII** (no NIC) in the payload. Verifying checks the signature first (`Tampered` if it fails), then the expiry (`Expired`), only returning `Valid(transactionId, bookingId)` if both pass. The TTL (24 hours, `util/TransactionRules.kt`) is a single shared constant so the signing side and the display side can never drift apart.

## 5. Prosumer experience

Single Activity (`ui/prosumer/ProsumerMainActivity`) hosting a `NavHostFragment` + `BottomNavigationView` with 4 tabs: **Home, Bookings, Map, Profile**, plus two non-tab destinations reachable by navigation actions: **New Booking** and **Energy Transfer Pass (QR)**.

- **Home** (`ui/prosumer/home/HomeFragment`) — dashboard: Pending/Confirmed/Completed counts, the single soonest active reservation as an "Upcoming Reservation" card, a "View QR" button (visible only once that reservation is `CONFIRMED`), and a "New Booking" button.
- **New Booking** (`ui/prosumer/newbooking/NewBookingFragment`) — one form used for both create *and* modify (an optional `bookingId` arg switches it into edit mode; an optional `stationId` arg pre-locks the station when reached from the map's "Book Here" button). Station dropdown, `MaterialDatePicker` constrained to `[today, today+7]`, `MaterialTimePicker`, energy-amount field capped by the station's capacity. All of these constraints are re-validated (not just UI-constrained) by `BookingValidator`, and then re-validated *again* atomically inside `LocalDbManager.createBooking`/`updateBooking`.
- **Bookings** (`ui/prosumer/bookings/BookingsFragment`) — one reactive JOIN query (`getBookingListItems`, bookings⋈stations) backs three `TabLayout` tabs — **Current** (`CONFIRMED`), **Pending**, **History** (`COMPLETED`/`CANCELLED`/`EXPIRED`) — plus a station-name search box, all filtered **in memory** in the ViewModel rather than three separate SQL queries.
- **Booking Detail** (`ui/prosumer/bookings/BookingDetailFragment`) — Modify/Cancel buttons, enabled only when `BookingTimeRules.canModifyOrCancel` (status is `PENDING`/`CONFIRMED` **and** at least 12 hours' notice remains) says so; a "View QR" button once `CONFIRMED`.
- **Energy Transfer Pass** (`ui/prosumer/qrpass/QrPassFragment`) — read-only screen: renders the signed QR token as a bitmap (ZXing `MultiFormatWriter`, `util/QrBitmapEncoder.kt`) plus station/date/time/energy, transaction ID, and a computed "Valid until" timestamp. Reachable from both Home and Booking Detail via one shared nav destination.
- **Map** (`ui/prosumer/map/MapFragment`) — real Google Maps SDK (`SupportMapFragment`) with all 5 stations shown as color-coded markers (marker `tag` = `stationId`, so a tap resolves the station with no re-query), a manual **List View** toggle that swaps to an offline-safe RecyclerView list (used automatically if Play Services isn't available, and shown to the user with an "offline" banner if there's no network — map tiles need real internet even though the rest of the app doesn't). Tapping a station opens a dialog with capacity/slots/status and, if bookable, a "Book Here" button that deep-links into New Booking with the station pre-selected.
- **Profile** (`ui/prosumer/profile/ProfileFragment` / `EditProfileFragment`) — view/edit name, email, phone, address; account status display; a "Request Deactivation" action; Logout.
- **Auth** (`ui/auth/`) — `SplashActivity` (checks `SecurityManager.currentSession()` and routes via `RoleRouter` straight to the right dashboard, or to Login if nobody's signed in), `LoginActivity`, `RegisterActivity` (Prosumer self-registration only — new accounts are `ACTIVE` immediately, auto-login after registering; Grid Operator has no registration UI and only exists via the DB seed).

## 6. Grid Operator experience

Single Activity (`ui/gridoperator/OperatorMainActivity`) with its own bottom nav and nav graph (`nav_operator.xml`): **Home, Scan QR, Bookings, Map**. No Profile tab — Logout lives on the Home screen instead.

- **Home** (`ui/gridoperator/home/OperatorHomeFragment`) — the actionable queue: every `PENDING` booking across *all* prosumers (`BookingRepository.getAllPendingBookings()`, role-guarded), each with an Approve/Reject button that calls `approveBooking`/`rejectBooking` directly and reloads the list.
- **Bookings** (`ui/gridoperator/bookings/OperatorBookingsFragment`) — read-only, cross-prosumer version of the Prosumer bookings screen: same `BookingScope` tabs (Current/Pending/History) reused directly from the Prosumer package, but each row also shows the owning NIC, and the search box matches station name **or** NIC. No click action — there's no operator-facing booking detail screen.
- **Map** (`ui/gridoperator/map/OperatorMapFragment`) — literally reuses the Prosumer's `MapViewModel`/`StationListAdapter`/`StationMarkerIcons` (pure display logic, no role dependency), just with a stripped detail dialog that has no "Book Here" button.
- **Scan QR** (`ui/gridoperator/scan/OperatorScanFragment`) — live camera scanning via **CameraX** (Preview + `ImageAnalysis` use cases bound to the fragment's lifecycle) piped into **ML Kit Barcode Scanning** restricted to `FORMAT_QR_CODE`. Deliberately a *different* library from the Prosumer's ZXing-based QR *generation* — one is a one-shot bitmap render, the other a live frame-by-frame decode pipeline, no reason to share a dependency. Handles the `CAMERA` runtime permission explicitly (shows a "Grant Camera Permission" button if denied).

  The scan-to-complete flow is **two steps, not one**, by design:
  1. A decoded token goes to `TransactionRepository.verifyToken(raw)` — **read-only**: checks the HMAC signature/expiry, then looks up the transaction (must still be `GENERATED`) and its booking (must still be `CONFIRMED`). No database writes happen here.
  2. On success, a `BottomSheetDialog` shows the booking summary (station, NIC, date/time, energy) with **Complete Transfer** / **Cancel** buttons. Only an explicit tap on "Complete Transfer" calls `TransactionRepository.completeTransfer(transactionId)`, which re-checks both statuses again *inside its own DB transaction* before marking the transaction and booking `COMPLETED`.

  A failed verification (tampered/expired token, already-completed pass, cancelled booking) shows a plain error dialog instead of the sheet — there's nothing to confirm.

## 7. Cross-cutting patterns worth knowing about

- **`ListAdapter` + `DiffUtil`** for every RecyclerView (bookings, pending approvals, station list) — no adapter does manual `notifyDataSetChanged()` except where the whole backing map changes (station-name lookup refresh).
- **One reusable fragment for three "scopes."** Both bookings screens (Prosumer and Operator) use a single fragment class with an in-memory scope/search filter on top of one base query, rather than three near-duplicate fragments or three parameterized SQL queries.
- **`android:fitsSystemWindows="true"`** is required on every Activity's root layout because target SDK 37 enforces edge-to-edge by default; a real bug was found and fixed where a top-anchored button (Map's List/Map toggle) was rendered *underneath* the status bar and silently ate zero touch events until this was added.
- **Repository methods return `AppResult`, never throw, for expected business failures** (wrong status, too late to cancel, not found) — but **do throw** `UnauthorizedAccessException` for role violations, since that's treated as a programming/security error rather than a normal outcome a caller should have to unwrap.
- **Debug-only approval tool** (`app/src/debug/`) — a physically separate Gradle source set (absent from release builds entirely, not just hidden behind a flag) with its own launcher icon, used during development to approve/reject bookings before the real Operator Home screen existed. It calls the exact same `BookingRepository` methods the real UI now uses, so it's redundant now that Phase 13 shipped the real dashboard, but hasn't been removed yet.

## 8. What's designed but not built

- Networking layer (`CommunicationManager`, `RemoteDataSource`) — every repository talks only to `LocalDbManager`; there is no HTTP client, no Retrofit, no real backend.
- Background sync (`SyncManager`, WorkManager-based scheduler, conflict resolution) — the `sync_status` column exists and is written correctly, but nothing reads it to actually push/pull data anywhere.
- Cross-device QR redemption — since each device only has its own local database, a QR generated on one device can't yet be scanned/verified from a different device's install. This is a known, accepted gap until the sync layer exists.
