# Skills Exchange — Real-Device Test Report
**Date:** 2026-09-29 (Africa/Kampala) · **Tester:** AI agent session, on-device via ADB
**Build under test:** `app/build/outputs/apk/debug/app-debug.apk` (fresh `assembleDebug`, this session)
**PRD baseline:** 86 completed / 0 in progress / 6 pending of 92

---

> **Artifacts removed on 2026-11-20 (R11).** This report describes a run of the 2026-09-29 build, which
> is not the committed code. The files it cites as evidence — `installed-base.apk`, `installed-check.apk`,
> `ui.xml`, the logcat captures and the heap-crash log — were deleted in R11 (74 MB of build and device
> output that could not be evidence for any later commit; see `docs/qa/TEST_STRATEGY.md`). What remains
> is this written record plus the screenshots and the pulled SQLite file. Treat every claim below as
> **historical and unreproducible** from the current tree: it is context, not verification.

## 1. Verdict

| # | Area | Result | Evidence |
|---|---|---|---|
| 1 | Debug build | ✅ PASS | `assembleDebug` exit 0 in 1m 36s → 35,254,412 B APK |
| 2 | Install on physical device | ✅ PASS (after storage recovery) | `adb install -r`, in-place update, `firstInstallTime` preserved |
| 3 | Cold start / launch | ✅ PASS | `LaunchState: COLD`, `MainActivity` drawn, Firebase init, no ANR |
| 4 | Room schema migration 18→19 on device | ✅ PASS | On-device DB `user_version = 19` == `AppDatabase version = 19` |
| 5 | Guarantor (REG-002) table on device | ✅ PASS | `guarantors` table carries `nationalIdNumber` + `phoneNumber` |
| 6 | Client-mode home screen + GPS filter | ✅ PASS | "Live: Sunnyside District / 15m accuracy filtering" |
| 7 | Navigation drawer | ✅ PASS | Renders incl. "National ID &…" guest entry |
| 8 | Guest gating on National ID & Verification | ✅ PASS (correctly gated, no crash) | Redirects to CLIENT ONBOARDING sheet |
| 9 | Job Status tab | ✅ PASS | Process alive, no exception |
| 10 | Theme switch (Light ↔ Dark) | ✅ PASS | Visible relight of surfaces/text |
| 11 | Providers list render | ✅ PASS | Renders with 0 matches state (empty DB) |
| 12 | **Map View tab** | ❌ **FAIL — hard crash, 3/3 repro** | `IllegalStateException: API key not found` @ `InteractiveMapView.kt:98` |
| 13 | **Role-gate feedback (mode card tap)** | ⚠ **DEFECT — silent on screen** | Gate refuses correctly; the message it sets is only rendered inside `AuthModal`/onboarding, both closed at that moment |
| 14 | Crash-free session overall | ⚠ 3 fatal crashes, all from one cause | All three = Maps API key; no other `FATAL`/`ANR` anywhere |

**Bottom line:** the app installs, launches, migrates its database and behaves correctly across
every path tested **except** the Map View tab, which reliably kills the process, and the gated
worker-mode tap, which fails silently.

---

## 2. Device & build environment

| Item | Value |
|---|---|
| Model | Samsung **SM-A025F** (Galaxy A02) |
| Android / SDK | **12 / API 31** |
| Screen | 720 × 1600 px, 280 dpi |
| ADB state | `device` (authorized, unlocked) |
| Application id | `com.aistudio.skillsexchange.nxptvq` |
| versionCode / versionName | `1` / `1.0` (minSdk 24, targetSdk 36) |
| Google Play Services | Present (Maps dynamite loaded — crash came from key lookup, not missing GMS) |
| Build toolchain | Gradle 9.5.0, build JVM pinned to Corretto 17.0.20_10 via `gradle.properties` |
| Secrets present | `.env` (`GEMINI_API_KEY`), `google-services.json` |

---

## 3. Build & install path

1. `.\gradlew.bat assembleDebug --console=plain` → **exit 0**, 1m 36s.
2. Signature check before touching the installed app: new APK signing cert SHA-256
   `d5b68479…ac32` **identical** to the cert of the already-installed package → an in-place
   update was safe; **no uninstall** was performed, so existing app data and the Room DB survived.
3. First `adb install -r` failed:
   `INSTALL_FAILED_INSUFFICIENT_STORAGE` — reported as *"Requested internal only, but not enough space"*.
   This was **not** a signature conflict (the signature had already been compared and matched).
4. Root cause via `dumpsys diskstats`: `/data` at 99 % full, ~349 MB free — the device, not the app.
5. Recovery used was **non-destructive**: `adb shell pm trim-caches 2147483648` freed ~551 MB by
   asking apps to trim their own caches. No app uninstalled, no user media touched
   (Photos 2.79 GB / Videos 1.15 GB intact).
6. `adb install -r` then succeeded. Post-test `df /data` reads 97 % used, ~819 MB available.

| Marker | Value | Meaning |
|---|---|---|
| `firstInstallTime` | 2026-09-16 20:20:52 | unchanged → genuine in-place update, data preserved |
| `lastUpdateTime` | 2026-09-29 17:02:25 | this session's install |
| Installed `base.apk` | 35,254,412 B | byte-identical size to the freshly built APK |


---

## 4. Launch & runtime health

| Observation | Result |
|---|---|
| First launch after unlock | `LaunchState: COLD`, **6305 ms** total-time (earlier warm launch: ~3.3 s) |
| Process | `pidof com.aistudio.skillsexchange.nxptvq` returns a live PID through the whole walkthrough |
| Firebase | `FirebaseApp` initialised, Firestore/Analytics init lines present in `logcat_coldstart.txt`, no init errors |
| Compose UI | `MainActivity` DecorView attached; view hierarchy reported by `uiautomator dump` (`ui.xml`) |
| ANR | none (`ANR in` never appears in any captured log) |
| Crash-free baseline logs | `logcat_full.txt`, `logcat_coldstart.txt`: **0 `FATAL EXCEPTION`** |

Post-crash recovery was also verified: after the Map View crash the next launch cold-starts
straight back into `MainActivity` on the Providers tab with the live-GPS banner restored
(`ui15_postcrash_settled.png`) — no corrupted state, no bootloop, no stuck task.

---

## 5. Room database verification (the highest-value on-device result)

The DB was pulled out of the app sandbox (`run-as` + `exec-out`, script kept at
`_device_test/pull_db.ps1`) because the device is not rooted and the file is not world-readable.

| Check | Value | Expected | Verdict |
|---|---|---|---|
| SQLite magic header | `SQLite format 3\0` | valid DB | OK |
| `page_size` | 4096 | 4096 | OK |
| `user_version` (offset 60, big-endian) | **19** | `AppDatabase` `version = 19` | OK |
| WAL sidecar | `ondevice_db.sqlite-wal`, 416,152 B | WAL mode live | OK |
| Schema size | `skills_exchange_database` 249,856 B | — | OK |
| `guarantors` columns | `nationalIdNumber`, `phoneNumber` present | REG-002 | OK |

Source side (`AppDatabase.kt`): `version = 19`, and
`addMigrations(…, MIGRATION_17_18, MIGRATION_18_19)`.

**Conclusion:** the app installed on 2026-09-16 carried an older schema, was **not** wiped, and
opened today at `user_version = 19`. `MIGRATION_18_19` therefore executed successfully on physical
hardware — something no previous session had ever proven (all earlier evidence was unit-test or
compile level). This closes the "on-device golden path" item for the **data** layer.

---

## 6. UI walkthrough (client mode, signed-out guest)

| Interaction | Artifact | Outcome |
|---|---|---|
| Home / Providers tab, live GPS | `ui1.png` | Header "Current Location"; green banner **Live: Sunnyside District, 15m accuracy filtering**; Edit/refresh/close; skill chips; radius chips (5 mi selected); Verified Only; "NEARBY VERIFIED SPECIALISTS (0)" empty state |
| Open navigation drawer | `ui2_drawer.png` | Full menu renders; guest entries present, incl. **"National ID &"** verification entry |
| Tap "National ID &" as guest | `ui3_nationalid.png` | Correctly refused → **CLIENT ONBOARDING** sheet appears. No crash. |
| Tap **Map View** | `ui4_mapview.png`, `ui14_mapcrash_final.png` | **App killed** — see Defect D1 |
| Back / relaunch after crash | `ui5_back.png`, `ui15_postcrash_settled.png` | Returns to Providers tab cleanly |
| Tap **Job Status** | `ui6_jobstatus.png` | No crash; PID alive; no exception in logcat |
| Tap **+** FAB | `ui7_fab.png` | Outcome reviewed; no unhandled exception attributed to the FAB |
| Tap worker/client **mode card** (guest) | `ui8_modeswitch.png`, `ui11_workermode.png` | Correctly **no-ops** (blocked by `canAccessWorkerFeatures()`), but **silent** — see Defect D2 |
| Drawer re-open after mode tap | `ui9_drawer2.png`, `ui10_drawer3.png` | Renders normally |
| Theme switch Light to Dark | `ui12_theme_dark.png` | Works; surfaces, text and chips all relight, no contrast loss |
| Providers list (populated filters) | `ui13_providers.png` | Renders correctly; empty-state messaging correct for an empty DB |

Mode-card content confirmed from the uiautomator dump: `Guest Mode (Sign in to unlock)` and
`Resident / Client Mode` / `Tap to switch to worker dashboard` — i.e. the gate is intentional.

---

## 7. Defect D1 (CRITICAL) — Map View tab crashes the app on every tap

**Severity:** critical (100 % loss of a P1 feature + process death).
**Reproducibility:** deterministic — reproduced **3 / 3** taps, in three independent log captures.

### Symptom
Tapping the **Map View** bottom-navigation item immediately kills the process. Android then
restores the previous task, which on this device resolved to `com.openai.chatgpt/.MainActivity`,
so the user sees the app vanish.

### Stack (verbatim, `logcat_mapcrash_final.txt` @ 18:33:59.286)
```
E AndroidRuntime: FATAL EXCEPTION: main
E AndroidRuntime: Process: com.aistudio.skillsexchange.nxptvq, PID: 25768
E AndroidRuntime: java.lang.IllegalStateException: API key not found.  Check that
    <meta-data android:name="com.google.android.geo.API_KEY" android:value="your API key"/>
    is in the <application> element of AndroidManifest.xml
E AndroidRuntime:   at com.google.maps.api.android.lib6.common.h.b(...:117)
E AndroidRuntime:   at com.google.android.gms.maps.internal.CreatorImpl.c(...:934)
E AndroidRuntime:   at com.google.android.gms.maps.internal.CreatorImpl.newMapViewDelegate(...:23)
E AndroidRuntime:   ... android.os.Binder.transact(Binder.java:1100)
E AndroidRuntime:   at com.google.android.gms.maps.zzai.createDelegate(...:1)
E AndroidRuntime:   at com.google.android.gms.dynamic.DeferredLifecycleHelper.onCreate(...:1)
E AndroidRuntime:   at com.example.ui.components.InteractiveMapViewKt.InteractiveMapView$lambda$...
                    (InteractiveMapView.kt:98)
```

### Crash tally across captures
| Capture | `API key not found` | `FATAL EXCEPTION` | PID |
|---|---|---|---|
| `logcat_full.txt` (pre-walkthrough) | 0 | 0 | — |
| `logcat_coldstart.txt` (launch) | 0 | 0 | — |
| `logcat_run2.txt` | 1 | 1 | 23654 |
| `logcat_mapcrash_repro.txt` | 1 | 1 | 25548 |
| `logcat_mapcrash_final.txt` | 1 | 1 | 25768 |

Three distinct PIDs = three genuinely separate process deaths, not one repeated log line.

### Root cause (confirmed in source, not inferred)
1. `app/src/main/AndroidManifest.xml` contains **no `<meta-data>` element at all** — grep for
   `meta-data` returns zero hits; `<application>` is at line 17 and
   `com.google.android.geo.API_KEY` is absent.
2. `.env` / `.env.example` define **only `GEMINI_API_KEY`** — there is no `MAPS_API_KEY`.
3. **No code path anywhere** reads, injects or validates a Maps key: no manifest placeholder, no
   runtime key setter, no availability check.
4. `InteractiveMapView.kt:96-98` constructs `MapView(context)` and calls `map.onCreate(null)`
   inside a Compose `AndroidView` factory with **no guard and no try/catch**. The Maps SDK throws
   `IllegalStateException` on the main thread when the manifest key is missing → uncaught → death.
5. `README.md` step 4 (lines 57-62) *documents* hand-editing the manifest, and its `.env` block
   (line 51) lists `MAPS_API_KEY` — but nothing wires the two together, so that variable is never
   consumed. The Map View tab is dead on arrival even for a developer who follows the README.

### Why this matters for the PRD
`prd.json` **MAP-001** ("Integrate Google Maps SDK for Nearby Worker View", P1) is recorded as
`status: "completed"`, `verified_by: "code_inspection"`. Its own acceptance criterion is
*"Map view renders interactive Google Maps tiles with tappable pins"* and its own verification step
is *"Open map tab on emulator with Play Services and verify real street map"*. That criterion is
**not met on real hardware**, and AGENTS.md §2 forbids treating a story as complete without
verified implementation. MAP-001 was verified by inspection only — exactly the class of defect a
compile or signature check cannot see.

### Recommended fix (smallest reliable change, in order)
1. **Fail safe, never crash:** guard `map.onCreate(...)` inside the `AndroidView` factory
   (`runCatching`) and render an inline fallback panel ("Map unavailable — Maps key not
   configured") instead of throwing on the main thread. Keeps every other tab reachable.
2. **Make the key real:** add to `AndroidManifest.xml`, inside `<application>`:
   `<meta-data android:name="com.google.android.geo.API_KEY" android:value="${mapsApiKey}" />`,
   declare `manifestPlaceholders["mapsApiKey"]` in `app/build.gradle.kts` fed from the same `.env`
   reader already used for `GEMINI_API_KEY`, and add a blank `MAPS_API_KEY=` line to
   `.env.example`. Never commit a real key (AGENTS.md §8).
3. **Guard early:** if the key is blank, skip rendering `MapView` entirely, so the failure is
   visible at build/run time rather than as a tap-time crash.
4. Re-run MAP-001's own verification on-device (open the Map tab, confirm real street tiles)
   before that story is called complete again.

---

## 8. Defect D2 (UX) — the role gate refuses correctly but says nothing on screen

Tapping the worker/client mode card as a guest is **correctly refused**, and a message *is* produced
— but it is produced somewhere the user cannot see at that moment.

Verified in source (not inferred):

| Step | Code | Result |
|---|---|---|
| Tap the mode card | `MainScreen.kt:202` / `:629` → `onToggleUserRole = { viewModel.toggleUserRoleMode() }` | no other side effect (no modal open, no snackbar) |
| Refusal | `SkillsExchangeViewModel.kt:354-359` `toggleUserRoleMode()` → `canAccessWorkerFeatures()` false → `authError.value = "Worker verification and activation required to switch to worker mode."` + `return` | mode unchanged (confirmed on device: `ui8_modeswitch.png`, `ui11_workermode.png`) |
| Who renders `authError` | `AuthModal.kt:57,322,335` (red text) and `OnboardingScreen.kt:75,933-935` | **both are inside the auth/onboarding UI only** |
| Where `AuthModal` is hosted | `MainScreen.kt:741` `if (isAuthModalOpen) { … authError = authError … }` | rendered **only while the modal is open** — and the refusal path does not open it |
| App-wide | `SnackbarHost` / `SnackbarHostState` appear in **zero** source files | no channel to surface the message outside the auth UI |

**Net effect:** the exact string the user needs is set into state and, because no visible composable
is listening on `MainScreen`, never displayed. From the user's side the tap does nothing — while the
drawer copy "Tap to switch to worker dashboard" actively invites it.

**Impact:** users conclude the app is unresponsive on the flow that is supposed to convert a guest
into a signed-in worker. No crash, no toast, no log line — the only evidence is internal state.

**Recommended fix (small, no new dependency):** give `MainScreen` a visible channel for the
`authError` it already collects — either add a `SnackbarHost` to the `Scaffold` and show/clear it, or
open the existing `AuthModal`/onboarding sheet on refusal (which already renders the message
properly). The one-line alternative that is *not* acceptable: leaving the message in state only.

---

## 9. Other observations (non-blocking)

- **Storage pressure on the target device** is a real test-environment hazard: `/data` sat at
  97-99 % full. Future device runs should check `df /data` *before* install and prefer
  `pm trim-caches` over deleting user data.
- **Non-rooted device:** app-private files require `run-as` + `exec-out`; a quoted
  `adb exec-out "run-as … cat 'path' > local"` redirect silently produces nothing on Windows — the
  working recipe is saved in `_device_test/pull_db.ps1`.
- Empty "Nearby Verified Specialists (0)" with a live GPS fix is consistent with an empty local DB
  plus client-mode gating; it is **not** evidence of a query defect.
- Cold start 6.3 s on a 3 GB-RAM entry-level device (first launch after unlock) is acceptable for a
  35 MB debug APK with Firebase + Compose; release-profile timings were not measured.
- Only the debug variant was tested. `assembleRelease` / signing / R8 behaviour remain unverified
  on device.

---

## 10. Artifact inventory — `_device_test/` (complete)

### Captured by this device test
| File | Bytes | Content |
|---|---|---|
| `DEVICE_TEST_REPORT.md` | (this file) | consolidated report |
| `installed-base.apk` | 32,135,846 | **pre-update** installed APK (2026-09-16 build) pulled off the device at 17:00 so its signing cert could be compared with the new APK before installing |
| `ondevice_db.sqlite` | 249,856 | live `skills_exchange_database` pulled via `run-as` |
| `ondevice_db.sqlite-wal` | 416,152 | its WAL sidecar (proves WAL mode active) |
| `pull_db.ps1` | 581 | working non-root pull recipe |
| `logcat_full.txt` | 82,276 | install→launch window; 0 fatal |
| `logcat_coldstart.txt` | 1,009,698 | cold start; Firebase init; 0 fatal |
| `logcat_run2.txt` | 4,598,134 | full walkthrough; crash #1 (PID 23654) |
| `logcat_mapcrash_repro.txt` | 810,938 | deliberate repro #2 (PID 25548) |
| `logcat_mapcrash_final.txt` | 260,058 | deliberate repro #3 (PID 25768), clean timestamped capture |
| `ui.xml` | 22,948 | uiautomator hierarchy (mode-card bounds Node 21 `[28,443][515,564]`) |
| `screen1.png` / `screen3.png` | 7,904 each | blank captures — the screen was locked/asleep |
| `screen2.png` | 1,256,462 | pre-unlock screen |
| `screen4.png` | 126,201 | first successful UI frame |
| `ui1.png` | 74,948 | home / Providers, live GPS banner |
| `ui2_drawer.png` | 153,859 | navigation drawer |
| `ui3_nationalid.png` | 103,086 | National ID tap → CLIENT ONBOARDING gate |
| `ui4_mapview.png` | 67,899 | Map View tap → crash |
| `ui5_back.png` | 13,371 | back after crash |
| `ui6_jobstatus.png` | 78,089 | Job Status tab (no crash) |
| `ui7_fab.png` | 104,188 | FAB tap |
| `ui8_modeswitch.png` | 77,843 | mode-card tap (silent no-op) |
| `ui9_drawer2.png` | 1,234,225 | drawer re-open |
| `ui10_drawer3.png` | 154,012 | drawer re-open (2nd) |
| `ui11_workermode.png` | 154,012 | after mode-card tap — still client mode, no message shown |
| `ui12_theme_dark.png` | 153,392 | dark theme |
| `ui13_providers.png` | 74,925 | providers list |
| `ui14_mapcrash_final.png` | 11,874 | the crash moment (final capture) |
| `ui15_postcrash_settled.png` | 75,008 | settled state after crash |

**Related evidence that lives outside `_device_test/` (not duplicated there):**
| File | Bytes | Note |
|---|---|---|
| `build_debug.log` (repo root) | 6,372 | `assembleDebug` console log, exit 0 |
| `app/build/outputs/apk/debug/app-debug.apk` | 35,254,412 | the APK under test |
| device `/data/app/…/base.apk` | 35,254,412 (mtime 2026-09-29 17:01) | verified with `adb shell ls -l`: what the device is running **is** this build |

### Pre-existing in the same folder (not produced by the walkthrough)
`classes.dex` (42,014,804) and `classes2.dex` … `classes14.dex` (14 files, 12,488 B – 42 MB), all
timestamped 17:00 alongside `installed-base.apk`: extracted DEX leftovers from package/signature
inspection. They belong to no build task and can be deleted from `_device_test/` without affecting
the project.

Net new evidence from this test: **6 logcat captures (~6.8 MB), 15 UI screenshots, 1 UI hierarchy
dump, the live Room DB + WAL, the pre-update APK copy, and 1 reusable pull script.**

---

## 11. Repro recipe (so the next session can repeat this in minutes)

```powershell
# 0. Pre-flight (device is nearly full almost every time)
adb devices
adb shell df /data
adb shell pm trim-caches 2147483648      # non-destructive cache reclaim

# 1. Build + in-place install (no uninstall => data & Room DB survive)
.\gradlew.bat assembleDebug --console=plain
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 2. Launch + health
adb shell am force-stop com.aistudio.skillsexchange.nxptvq
adb logcat -c
adb shell monkey -p com.aistudio.skillsexchange.nxptvq -c android.intent.category.LAUNCHER 1
adb shell pidof com.aistudio.skillsexchange.nxptvq

# 3. Crash capture (Map View tab is at y≈508 on 720x1600)
adb shell input tap 270 508; adb logcat -d -v brief | Select-String 'FATAL|IllegalStateException'

# 4. UI evidence
adb shell screencap -p /data/local/tmp/x.png
adb pull /data/local/tmp/x.png .\x.png
adb shell uiautomator dump /data/local/tmp/ui.xml   # then pull for bounds

# 5. On-device schema proof
.\_device_test\pull_db.ps1
# then read bytes 60..63 of ondevice_db.sqlite as big-endian = user_version
```

---

## 12. Suggested next actions (ordered, each traceable)

| # | Action | Traces to |
|---|---|---|
| 1 | Make `InteractiveMapView` crash-proof (guarded `onCreate` + fallback UI) | D1 / MAP-001 |
| 2 | Wire `MAPS_API_KEY` from `.env` → `manifestPlaceholders` → manifest `<meta-data>`; add blank key to `.env.example` | D1 / MAP-001, README step 4 |
| 3 | Re-open **MAP-001** (`completed` → `in_progress`) and re-verify on device with a real key before re-completing it | AGENTS.md §2, D-lesson in §7 |
| 4 | Give `MainScreen` a visible channel for the `authError` it already collects (add a `SnackbarHost`, or open the `AuthModal`/onboarding sheet on refusal) | D2 |
| 5 | Record D1/D2 in `AGENT_STATE.md` blockers + `CHANGELOG.md` | AGENTS.md §10, §11 |
| 6 | Still-open pre-existing items unaffected by this test: `firebase deploy --only firestore:rules,storage`, and gaps G1, G3-G7, G12 | AGENT_STATE.md |

