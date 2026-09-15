# UI Layout & Color Report — Smart Solar Microgrid Trading System

Every screen in the app, its components (identified by their `android:id` resource name), their placement, and their colors. Read straight from the layout XML files and the adapter/`colorFor()` logic that drives dynamic colors — nothing here is guessed.

## 0. Design tokens (apply everywhere unless noted)

The app declares **no custom theme** — `themes.xml` is `Theme.Material3.Light.NoActionBar` with every color left at Material3's default baseline palette (no `colorPrimary`/`colorSecondary` overrides). `colors.xml` only defines `black` (`#FF000000`) and `white` (`#FFFFFFFF`), unused directly by any layout.

Practical effect: every `MaterialButton`, `TextInputLayout`, `TabLayout`, and `BottomNavigationView` gets its color from **Material3's default light scheme** — a purple/indigo primary (≈`#6750A4`), light lavender surface tones, white cards. This is visible in every screenshot as the purple buttons/selected-tab-indicator and pale lavender bottom nav background. No screen overrides this.

**Explicit hex colors used anywhere in the app** (everything else is theme-default):

| Hex | Meaning | Used for |
|---|---|---|
| `#2E7D32` (green) | APPROVED / ACTIVE | Booking status text, station status text |
| `#F9A825` (amber) | PENDING | Booking status text |
| `#1565C0` (blue) | COMPLETED | Booking status text |
| `#C62828` (red) | CANCELLED / error text | Booking status text, error/validation text |
| `#EF6C00` (dark orange) | FULL | Station status text (list) |
| `#757575` (grey) | EXPIRED / MAINTENANCE / OFFLINE / muted metadata | Booking & station status text, sync-status/secondary text |
| `#B71C1C` (dark red) | debug banner background | Debug tool banners |
| `#000000` / `#FFFFFF` | camera screen background / text | Scan QR screen |
| `#AA000000` / `#DD000000` | translucent black overlay | Scan QR hint bar, Map offline banner |
| Google Maps marker hues: `HUE_GREEN` / `HUE_ORANGE` / `HUE_AZURE` | ACTIVE / FULL / MAINTENANCE·OFFLINE | Map station pins |

**Status color legend (`BookingStatus`)** — defined identically in three places (`BookingsAdapter`, `OperatorBookingsAdapter`, `BookingActionSummaryFragment`):

| Status | Color |
|---|---|
| PENDING | `#F9A825` amber |
| APPROVED | `#2E7D32` green |
| COMPLETED | `#1565C0` blue |
| CANCELLED | `#C62828` red |
| EXPIRED | `#757575` grey |

**Station status legend** — two independent mappings exist (list rows use a slightly different orange than map pins):

| Status | List row text (`StationListAdapter`) | Map pin hue |
|---|---|---|
| ACTIVE | `#2E7D32` green | Green |
| FULL | `#EF6C00` dark orange | Orange |
| MAINTENANCE / OFFLINE | `#757575` grey | Azure (light blue) |

---

## 1. Auth screens

### `activity_splash.xml` (SplashActivity)
`ConstraintLayout`, vertically chained and centered as a group ("packed" chain style):

| Component | Type | Position | Color |
|---|---|---|---|
| `tvAppName` | TextView | Top of the centered chain; text = app name, 24sp bold | Default text color (black/dark grey, theme default) |
| `progressSplash` | ProgressBar | 24dp below `tvAppName`, bottom of chain | Theme-default purple spinner |

### `activity_login.xml` (LoginActivity)
`ScrollView` → vertical `LinearLayout`, centered vertically, 24dp padding, top-to-bottom:

| Component | Type | Position | Color |
|---|---|---|---|
| (untitled) app name | TextView | Top, centered horizontally, 22sp bold, 32dp margin below | Default text color |
| `tilNic` / `etNic` | TextInputLayout / EditText | Below title, hint "NIC" | Theme purple outline/label when focused, default outline otherwise |
| `tilPassword` / `etPassword` | TextInputLayout / EditText | 12dp below NIC field, hint "Password", password-toggle end icon | Same theme purple focus color |
| `btnLogin` | MaterialButton | 24dp below password field, full width | Theme purple filled button, white text |
| `progressLogin` | ProgressBar | 16dp below button, centered, hidden until loading | Theme purple |
| `tvGoToRegister` | TextView | 24dp below progress, centered, "Don't have an account? Register" | Default text color |

### `activity_register.xml` (RegisterActivity)
Same `ScrollView` → `LinearLayout` pattern, top-aligned (not centered), 24dp padding:

| Component | Type | Position | Color |
|---|---|---|---|
| (untitled) heading | TextView | Top, "Create a Prosumer Account", 20sp bold | Default text color |
| `tilNic`/`etNic` | TextInputLayout/EditText | 1st field | Theme purple |
| `tilFullName`/`etFullName` | TextInputLayout/EditText | 2nd field, 12dp below NIC | Theme purple |
| `tilEmail`/`etEmail` | TextInputLayout/EditText | 3rd field | Theme purple |
| `tilPhone`/`etPhone` | TextInputLayout/EditText | 4th field | Theme purple |
| `tilAddress`/`etAddress` | TextInputLayout/EditText | 5th field, multi-line | Theme purple |
| `tilPassword`/`etPassword` | TextInputLayout/EditText | 6th field, password-toggle icon | Theme purple |
| `tilConfirmPassword`/`etConfirmPassword` | TextInputLayout/EditText | 7th field, password-toggle icon | Theme purple |
| `btnRegister` | MaterialButton | 24dp below last field, full width | Theme purple filled, white text |
| `progressRegister` | ProgressBar | Below button, centered, hidden until loading | Theme purple |
| `tvGoToLogin` | TextView | Bottom, "Already have an account? Login" | Default text color |

*(Register's own success dialog — a plain `AlertDialog`, not part of this layout — uses system dialog styling: white surface, purple "OK" action text, per Fix 1.)*

---

## 2. Prosumer screens

### `activity_prosumer_main.xml` (ProsumerMainActivity — the shell)
`ConstraintLayout`, `fitsSystemWindows="true"`:

| Component | Type | Position | Color |
|---|---|---|---|
| `navHostProsumer` | FragmentContainerView (NavHost) | Fills from top to just above the bottom nav | — |
| `bottomNavProsumer` | BottomNavigationView | Pinned to the bottom edge, full width | Theme-default pale lavender bar; selected icon/label tinted theme purple, unselected grey (Material3 defaults, no overrides) — 4 items: **Home, Bookings, Map, Profile** |

### `fragment_home.xml` (HomeFragment)
`SwipeRefreshLayout` → `ScrollView` → vertical `LinearLayout`, 20dp padding:

| Component | Type | Position | Color |
|---|---|---|---|
| `tvWelcome` | TextView | Top, "Welcome, {NIC}", 20sp bold | Default text color |
| `progressHome` | ProgressBar | Below welcome, centered, 32dp top margin | Theme purple |
| `groupContent` | LinearLayout (container) | Wraps everything below once loaded | — |
| Three-card row (`Pending`/`Approved`/`Completed`) | 3× `MaterialCardView`, each with a bold 22sp count `TextView` (`tvPendingCount`, `tvApprovedCount`, `tvCompletedCount`) + a plain label TextView ("Pending"/"Approved"/"Completed") | Horizontal row, equal-weight, 16dp top margin | White card surface (Material3 default), default text color for counts/labels |
| `cardUpcoming` | MaterialCardView | Below the count row, 16dp top margin, hidden if no upcoming booking | White card surface |
| — "Upcoming Reservation" label | TextView | Top of the card, bold | Default text color |
| `tvUpcomingStation` | TextView | Below label, 8dp top margin | Default text color |
| `tvUpcomingDateTime` | TextView | Below station | Default text color |
| `tvUpcomingEnergy` | TextView | Below date/time | Default text color |
| `tvUpcomingStatus` | TextView | Below energy, "Status: X", 8dp bottom margin | Default text color (**not** status-colored here — plain text) |
| `btnViewQr` | MaterialButton | Bottom of the upcoming card, hidden unless status is APPROVED | Theme purple filled |
| `tvNoUpcoming` | TextView | Below the card (shown instead of it when nothing upcoming), "No upcoming reservations" | Default text color |
| `btnNewBooking` | MaterialButton | Bottom of `groupContent`, full width, 20dp top margin | Theme purple filled |
| `emptyStateHome` (with `btnNewBookingEmpty`) | LinearLayout / MaterialButton | Centered, 48dp top margin, shown only when the prosumer has zero bookings ever | Default text + theme purple button |

### `fragment_bookings.xml` (BookingsFragment)
Plain vertical `LinearLayout` (no ScrollView — the list itself scrolls):

| Component | Type | Position | Color |
|---|---|---|---|
| `etSearch` (in a `TextInputLayout`) | EditText | Top, 16dp horizontal/top margin, hint "Search by station" | Theme purple outline |
| `etDateFilter` (in a `TextInputLayout`, `endIconMode="clear_text"`) | EditText | Below search, 16dp margin all around, hint "Filter by date" | Theme purple outline; auto "×" clear icon |
| `tabLayoutBookings` | TabLayout | Below the date filter | Theme purple selected-tab underline/text; 3 tabs — **Current, Pending, History** |
| `rvBookings` | RecyclerView | Fills remaining space below tabs | — (rows are `item_booking.xml`, see §4) |
| `progressBookings` | ProgressBar | Centered, overlaid on the list area | Theme purple |
| `emptyStateBookings` (with `tvEmptyBookingsMessage`, `btnClearFilters`) | LinearLayout / TextView / MaterialButton (text-style) | Centered over the list area | Default text; purple text-only button, "Clear Filters" |

### `fragment_booking_detail.xml` (BookingDetailFragment)
`ScrollView` → vertical `LinearLayout`, 24dp padding:

| Component | Type | Position | Color |
|---|---|---|---|
| `progressBookingDetail` | ProgressBar | Top, centered, 32dp top margin | Theme purple |
| `groupBookingDetail` | LinearLayout | Revealed once loaded | — |
| `tvDetailStation` | TextView | Top of group, 20sp bold | Default text color |
| `tvDetailDateTime` | TextView | 12dp below station | Default text color |
| `tvDetailEnergy` | TextView | Below date/time, 4dp margin | Default text color |
| `tvDetailStatus` | TextView | Below energy, bold, 4dp margin | Default text color (plain, not status-colored) |
| `tvDetailSyncStatus` | TextView | Below status, 4dp margin | **`#757575` grey** (explicit) |
| `btnViewQr` | MaterialButton | 16dp below sync line, hidden unless APPROVED | Theme purple filled |
| `btnModify` | MaterialButton | Below View QR, hidden unless PENDING/APPROVED | Theme purple filled |
| `btnCancel` | MaterialButton (`OutlinedButton` style) | 12dp below Modify | Theme purple outline, transparent fill |
| `tvNoticeHelper` | TextView | 8dp below Cancel, shown only when inside the 12h notice window | **`#C62828` red** (explicit) |

### `fragment_new_booking.xml` (NewBookingFragment — create & modify)
`ScrollView` → vertical `LinearLayout`, 24dp padding:

| Component | Type | Position | Color |
|---|---|---|---|
| (untitled) heading | TextView | Top, "New Booking", 20sp bold, 16dp bottom margin | Default text color |
| `tilStation` (ExposedDropdownMenu style) / `etStation` | TextInputLayout / AutoCompleteTextView | 1st field, hint "Solar Station" | Theme purple outline |
| `tilDate`/`etDate` | TextInputLayout/EditText | 2nd field, 12dp top margin, hint "Reservation Date", non-focusable (opens `MaterialDatePicker`) | Theme purple outline |
| `tilTime`/`etTime` | TextInputLayout/EditText | 3rd field, hint "Reservation Time", non-focusable (opens `MaterialTimePicker`) | Theme purple outline |
| `tilEnergyAmount`/`etEnergyAmount` | TextInputLayout/EditText | 4th field, hint "Energy Amount (kWh)", numeric | Theme purple outline |
| `tvCapacityHint` | TextView | 4dp below energy field | Default text color |
| `btnConfirmBooking` | MaterialButton | 20dp below hint, full width, text = "Confirm Reservation" (create) or "Save Changes" (edit mode) | Theme purple filled |
| `progressNewBooking` | ProgressBar | Below button, centered, hidden until saving | Theme purple |

*(The date/time `MaterialDatePicker`/`MaterialTimePicker` dialogs are system Material components — purple selected-date circle, purple "OK"/Cancel text — not custom-styled.)*

### `fragment_booking_action_summary.xml` (BookingActionSummaryFragment — new, Fix 2)
`ScrollView` → vertical `LinearLayout`, 24dp padding:

| Component | Type | Position | Color |
|---|---|---|---|
| `progressActionSummary` | ProgressBar | Top, centered, 32dp margin | Theme purple |
| `tvActionSummaryError` | TextView | Centered, 32dp margin, shown only on load failure | **`#C62828` red** (explicit) |
| `groupActionSummary` | LinearLayout | Revealed once loaded | — |
| `tvActionHeadline` | TextView | Top of group, 20sp bold — "Reservation Created" / "Updated" / "Cancelled" | Default text color |
| (card) `MaterialCardView` | Card | 16dp below headline | White card surface |
| `tvSummaryStation` | TextView | Top of card, 16sp bold | Default text color |
| `tvSummaryDateTime` | TextView | 8dp below station | Default text color |
| `tvSummaryEnergy` | TextView | Below date/time | Default text color |
| `tvSummaryBookingId` | TextView | 12dp below energy | **`#757575` grey** (explicit) |
| `tvSummaryStatus` | TextView | 4dp below booking ID, bold | **Status-color-coded** (see legend §0 — set programmatically via `colorFor(status)`) |
| `tvSummarySyncStatus` | TextView | 4dp below status, shown only if not yet synced | **`#757575` grey** (explicit) |
| `btnBackToBookings` | MaterialButton | Below the card (outside the conditional group), 24dp top margin, always shown once loading finishes | Theme purple filled |

### `fragment_map.xml` (MapFragment / OperatorMapFragment — shared layout)
`ConstraintLayout`, full-bleed:

| Component | Type | Position | Color |
|---|---|---|---|
| `mapPane` → `mapContainer` (SupportMapFragment) | FrameLayout / Google Map | Fills entire screen | Google Maps' own tile colors; markers colored per §0's hue table |
| `swipeRefreshMap` → `rvStations` | SwipeRefreshLayout / RecyclerView | Fills entire screen, hidden unless in List View (rows are `item_station.xml`, see §4) | — |
| `btnToggleView` | MaterialButton | Top-right corner, 16dp margin, text "List View"/"Map View" | Theme purple filled |
| `progressMap` | ProgressBar | Dead-center of screen | Theme purple |
| `tvEmptyMap` | TextView | Dead-center, "No stations available" | Default text color |
| `tvMapBanner` | TextView | Pinned to the very top, full width, shown only when offline, "You're offline - map tiles may not load" | **Background `#DD000000`** translucent black, **text `#FFFFFF`** white (both explicit) |

### `fragment_qr_pass.xml` (QrPassFragment)
`ScrollView` → vertical `LinearLayout`, 24dp padding:

| Component | Type | Position | Color |
|---|---|---|---|
| `progressQrPass` | ProgressBar | Top, centered, 32dp margin | Theme purple |
| `tvQrPassError` | TextView | Centered, 32dp margin, error-only | **`#C62828` red** (explicit) |
| `groupQrPass` | LinearLayout | Revealed once loaded, centered horizontally | — |
| (untitled) "Energy Transfer Pass" | TextView | Top, 20sp bold | Default text color |
| (untitled) subtitle | TextView | 4dp below title, "Show this QR code..." | **`#757575` grey** (explicit) |
| (card) `MaterialCardView` around `imgQrCode` | Card / ImageView | 24dp below subtitle, 240dp×240dp QR bitmap, 16dp inner margin | White card surface; QR itself is pure black-on-white (rendered by `QrBitmapEncoder`) |
| (card) `MaterialCardView` | Card | 24dp below QR card | White card surface |
| `tvQrStation` | TextView | Top of card, 16sp bold | Default text color |
| `tvQrDateTime` | TextView | 8dp below station | Default text color |
| `tvQrEnergy` | TextView | Below date/time | Default text color |
| `tvQrTransactionId` | TextView | 12dp below energy | **`#757575` grey** (explicit) |
| `tvQrExpiry` | TextView | 4dp below transaction ID | **`#757575` grey** (explicit) |

### `fragment_profile.xml` (ProfileFragment)
`ScrollView` → vertical `LinearLayout`, 24dp padding:

| Component | Type | Position | Color |
|---|---|---|---|
| `progressProfile` | ProgressBar | Top, centered, 32dp margin | Theme purple |
| `groupProfileContent` | LinearLayout | Revealed once loaded | — |
| `tvFullName` | TextView | Top, 20sp bold | Default text color |
| `tvAccountStatus` | TextView | 4dp below name, 16dp bottom margin | Default text color |
| `tvNic` | TextView | 8dp margin | Default text color |
| `tvEmail` | TextView | 8dp margin | Default text color |
| `tvPhone` | TextView | 8dp margin | Default text color |
| `tvAddress` | TextView | 8dp margin, 24dp bottom margin | Default text color |
| `btnEditProfile` | MaterialButton | Full width | Theme purple filled |
| `btnRequestDeactivation` | MaterialButton | 12dp below Edit | Theme purple filled |
| `btnLogout` (`OutlinedButton` style) | MaterialButton | 24dp below Deactivation | Theme purple outline, transparent fill |

### `fragment_edit_profile.xml` (EditProfileFragment)
`ScrollView` → vertical `LinearLayout`, 24dp padding:

| Component | Type | Position | Color |
|---|---|---|---|
| `tilNic`/`etNic` | TextInputLayout/EditText | Top field, **disabled** (`enabled="false"`, non-editable) | Theme's disabled/greyed outline |
| `tilFullName`/`etFullName` | TextInputLayout/EditText | 12dp below NIC | Theme purple outline |
| `tilEmail`/`etEmail` | TextInputLayout/EditText | Below name | Theme purple outline |
| `tilPhone`/`etPhone` | TextInputLayout/EditText | Below email | Theme purple outline |
| `tilAddress`/`etAddress` | TextInputLayout/EditText | Below phone, multi-line | Theme purple outline |
| `btnSaveProfile` | MaterialButton | 24dp below address, full width | Theme purple filled |
| `btnCancelEdit` (`TextButton` style) | MaterialButton | 8dp below Save | Theme purple text-only, no fill/outline |
| `progressEditProfile` | ProgressBar | Below Cancel, centered, hidden until saving | Theme purple |

### `fragment_placeholder.xml` (generic unused-tab placeholder, `ui/common/PlaceholderFragment`)
`FrameLayout`, full-bleed:

| Component | Type | Position | Color |
|---|---|---|---|
| `tvPlaceholderMessage` | TextView | Dead-center | Default text color |

### `activity_role_placeholder.xml` (legacy — superseded by the real Operator dashboard; layout file remains unused)
`ConstraintLayout`, vertically chained/centered, 24dp padding:

| Component | Type | Position | Color |
|---|---|---|---|
| `tvPlaceholderTitle` | TextView | Top of chain, 20sp bold | Default text color |
| `btnLogout` | MaterialButton | 24dp below title, bottom of chain | Theme purple filled |

---

## 3. Grid Operator screens

### `activity_operator_main.xml` (OperatorMainActivity — the shell)
`ConstraintLayout`, `fitsSystemWindows="true"` — identical structure to the Prosumer shell:

| Component | Type | Position | Color |
|---|---|---|---|
| `navHostOperator` | FragmentContainerView (NavHost) | Fills from top to just above bottom nav | — |
| `bottomNavOperator` | BottomNavigationView | Pinned to bottom, full width | Theme-default lavender bar, purple selected item — 4 items: **Home, Scan QR, Bookings, Map** |

### `fragment_operator_home.xml` (OperatorHomeFragment)
`SwipeRefreshLayout` → vertical `LinearLayout` (header row + list):

| Component | Type | Position | Color |
|---|---|---|---|
| Header row (`LinearLayout`, horizontal) | Container | Top, 20dp padding | — |
| (untitled) "Pending Approvals" | TextView | Left side of header, weight=1, 20sp bold | Default text color |
| `btnOpLogout` (`TextButton` style) | MaterialButton | Right side of header | Theme purple text-only |
| `progressOpHome` | ProgressBar | Below header, centered, 32dp margin | Theme purple |
| `tvOpHomeEmpty` | TextView | Centered, 48dp margin, "No pending approvals" | Default text color |
| `rvOpPending` | RecyclerView | Fills remaining space | — (rows are `item_operator_pending_booking.xml`, see §4) |

### `fragment_operator_bookings.xml` (OperatorBookingsFragment)
Plain vertical `LinearLayout`:

| Component | Type | Position | Color |
|---|---|---|---|
| `etOpSearch` (in `TextInputLayout`) | EditText | Top, 16dp margin, hint "Search by station or NIC" | Theme purple outline |
| `tabLayoutOpBookings` | TabLayout | Below search | Theme purple selected-tab styling — 3 tabs: **Current, Pending, History** (defaults to Pending) |
| `rvOpBookings` | RecyclerView | Fills remaining space | — (rows are `item_operator_booking.xml`, see §4) |
| `progressOpBookings` | ProgressBar | Centered overlay | Theme purple |
| `tvOpBookingsEmpty` | TextView | Centered overlay, 16sp | Default text color |

### `fragment_operator_scan.xml` (OperatorScanFragment)
`ConstraintLayout`, **black background** (`android:background="#000000"`) — the only screen with a non-default page background:

| Component | Type | Position | Color |
|---|---|---|---|
| `previewView` (CameraX `PreviewView`) | Camera preview | Fills entire screen | Live camera feed |
| `tvScanHint` | TextView | Pinned to top, full width, 16dp padding | **Background `#AA000000`** translucent black, **text `#FFFFFF`** white |
| `groupNoPermission` (with message TextView + `btnGrantPermission`) | LinearLayout / MaterialButton | Dead-center, shown only when camera permission is denied | White message text; theme purple filled button |

*(The scan result "Complete Transfer" bottom sheet is `bottomsheet_transfer_verification.xml` — see below — and the success/failure result dialogs are plain system `AlertDialog`s.)*

### `bottomsheet_transfer_verification.xml` (shown from OperatorScanFragment on a successful scan)
Vertical `LinearLayout`, 24dp padding, presented as a `BottomSheetDialog`:

| Component | Type | Position | Color |
|---|---|---|---|
| (untitled) "Energy Transfer Verification" | TextView | Top, 18sp bold | Default text color |
| `tvVerifyStation` | TextView | 16dp below title, 16sp bold | Default text color |
| `tvVerifyNic` | TextView | 8dp below station | Default text color |
| `tvVerifyDateTime` | TextView | Below NIC | Default text color |
| `tvVerifyEnergy` | TextView | Below date/time | Default text color |
| `btnCompleteTransfer` | MaterialButton | 24dp below energy, full width | Theme purple filled |
| `btnCancelVerification` (`OutlinedButton` style) | MaterialButton | 12dp below Complete | Theme purple outline |

---

## 4. Shared list-item layouts (used inside RecyclerViews across multiple screens)

### `item_booking.xml` — Prosumer Bookings list rows
`MaterialCardView`, 16dp horizontal / 6dp vertical margin, clickable, 16dp inner padding:

| Component | Type | Position | Color |
|---|---|---|---|
| `tvBookingStation` | TextView | Top-left, weight=1, 16sp bold | Default text color |
| `ivSyncPending` | ImageView (18dp, system sync icon) | Top-right, same row as station name, shown only if not synced | System icon tint (default grey) |
| `tvBookingDateTime` | TextView | Below station, 4dp margin | Default text color |
| `tvBookingEnergy` | TextView | Below date/time | Default text color |
| `tvBookingStatus` | TextView | Below energy, 6dp margin, bold | **Status-color-coded** (§0 legend) |

### `item_operator_booking.xml` — Operator's cross-prosumer Bookings rows (read-only)
`MaterialCardView`, same margins, **not clickable**:

| Component | Type | Position | Color |
|---|---|---|---|
| `tvObStation` | TextView | Top, bold | Default text color |
| `tvObNic` | TextView | 4dp below station | Default text color |
| `tvObDateTime` | TextView | Below NIC | Default text color |
| `tvObEnergy` | TextView | Below date/time | Default text color |
| `tvObStatus` | TextView | 4dp below energy, bold | **Status-color-coded** (§0 legend) |

### `item_operator_pending_booking.xml` — Operator Home's actionable approval rows
`MaterialCardView`, same margins:

| Component | Type | Position | Color |
|---|---|---|---|
| `tvOpNic` | TextView | Top, bold | Default text color |
| `tvOpStation` | TextView | 4dp below NIC | Default text color |
| `tvOpDateTime` | TextView | Below station | Default text color |
| `tvOpEnergy` | TextView | Below date/time | Default text color |
| `btnOpApprove` | MaterialButton | 8dp below energy, left half (weight=1) | Theme purple filled |
| `btnOpReject` (`OutlinedButton` style) | MaterialButton | Right half (weight=1) | Theme purple outline |

### `item_station.xml` — Map tab's List View rows
`MaterialCardView`, same margins, clickable:

| Component | Type | Position | Color |
|---|---|---|---|
| `tvStationName` | TextView | Top, 16sp bold | Default text color |
| `tvStationCapacity` | TextView | 4dp below name | Default text color |
| `tvStationSlots` | TextView | Below capacity | Default text color |
| `tvStationStatus` | TextView | 6dp below slots, bold | **Station-status-colored** — ACTIVE `#2E7D32` green / FULL `#EF6C00` orange / MAINTENANCE·OFFLINE `#757575` grey |

---

## 5. Debug-only screens (`app/src/debug/` — physically absent from release builds)

### `activity_debug_account_activation.xml` (DebugAccountActivationActivity)
Vertical `LinearLayout`, `fitsSystemWindows="true"`:

| Component | Type | Position | Color |
|---|---|---|---|
| (untitled) banner | TextView | Top, full width, 16dp padding, "DEBUG TOOL - not shipped in release builds" | **Background `#B71C1C`** dark red, **text `#FFFFFF`** white (both explicit) |
| `rvDebugPendingUsers` | RecyclerView | Fills remaining space | — (rows are `item_debug_pending_user.xml`) |
| `tvDebugEmpty` | TextView | Centered overlay, "No accounts awaiting activation" | Default text color |

### `item_debug_pending_user.xml`
`MaterialCardView`, 16dp horizontal / 6dp vertical margin:

| Component | Type | Position | Color |
|---|---|---|---|
| `tvDebugUserName` | TextView | Top, bold | Default text color |
| `tvDebugUserNic` | TextView | Below name | Default text color |
| `tvDebugUserEmail` | TextView | Below NIC | Default text color |
| `btnDebugActivate` | MaterialButton | 8dp below email, full width, "Activate" | Theme purple filled |

---

## Notes on color methodology

- **"Default text color"** everywhere means Material3's default `onSurface` color for a light theme — near-black, never explicitly set in any layout in this codebase.
- **"Theme purple"** refers to Material3's default baseline seed color (~`#6750A4` family) applied automatically to every `MaterialButton`, `TabLayout` indicator, `TextInputLayout` focus outline, and `BottomNavigationView` selected item, since the app never overrides `colorPrimary`.
- Every genuinely **explicit** color (hardcoded hex, not theme-derived) is called out in bold above — there are 11 distinct hex values used across the entire app, all listed in §0's token table.
- Status-driven colors (`tvBookingStatus`, `tvObStatus`, `tvSummaryStatus`, `tvStationStatus`) are **not** set in XML at all — they're `TextView`s with no color attribute, painted at runtime by each adapter/fragment's `colorFor()` function. This report lists the runtime color they'll actually show, not just what the XML says (which for these is nothing).
