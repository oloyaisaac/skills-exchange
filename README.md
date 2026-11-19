# Skills Exchange

A hyperlocal peer-to-peer services marketplace Android app for East African urban centers (Uganda, Kenya, Tanzania, Rwanda). Connects nearby residents with verified local workers for home repairs, maintenance, technical tasks, personal services, and recurring contracts.

## Project status

> **Pre-production. Not release-ready.** An independent assessment on 2026-11-19 found build-blocking source defects, world-readable job/chat documents, a committed third-party API key, and unverified payment/SOS completion paths. See [`PRODUCTION_READINESS.md`](PRODUCTION_READINESS.md) and [`ASSESSMENT_REPORT.md`](ASSESSMENT_REPORT.md) before attempting a build or release. This README documents intended behaviour, not certified behaviour.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: MVVM with Repository pattern
- **Local Database**: Room
- **Backend**: Firebase (Firestore, Auth, Storage, Cloud Messaging)
- **Serverless**: Firebase Cloud Functions
- **Payments**: MTN MoMo collection and escrow, server-side and sandbox-only. **Airtel Money is a UI label only — there is no Airtel integration.**
- **Maps**: Google Maps Android SDK
- **AI**: Gemini 2.0 Flash (audio transcription, search grounding)
- **Build**: Gradle with Kotlin DSL

## Features

> **Status key:** the list below describes intended product behaviour. For what is implemented, tested
> or still missing, read [`docs/traceability.md`](docs/traceability.md); for document precedence, see
> [`docs/DOCUMENT_MAP.md`](docs/DOCUMENT_MAP.md). No feature here is claimed as verified end to end.

- **Unified Account Model**: Single profile supports both Client and Worker modes with seamless switching
- **Client Onboarding**: Phone registration, GPS location detection, manual district correction
- **Worker Verification Pipeline**: Uganda National ID upload, 2 guarantor system, skill qualification evidence
- **125-Service Taxonomy**: Structured service catalog with primary and related additional skill validation
- **Verification Lifecycle**: 48-hour SLA tracking, changes-required resubmission, admin approval queue
- **Worker Training**: 2-3 day rule training with admin activation gating
- **Dual Marketplace**: Nearby worker discovery and open job multi-quote bidding
- **Job Types**: One-time and recurring contracts (Daily, Weekly, Bi-weekly, Monthly, Custom)
- **Escrow Payments**: Mobile Money escrow lock, verified payouts, material advance disbursement
- **Doorstep Security**: Dynamic 4-digit PIN verification, ground proof photos, SOS alerts
- **Real-time Chat**: Firestore chat with phone number masking
- **Offline Support**: Queued actions persist locally; replay/sync on reconnect is **not implemented** (queue is preserved and logged, not sent)
- **Ratings & Reviews**: Worker rating aggregation with district-level privacy
- **Push Notifications**: FCM for job updates, quotes, payments, and verification status

## Prerequisites

- Android Studio (Ladybug or newer recommended)
- JDK 17
- Firebase project with Android app configured
- Google Maps API key
- Gemini API key
- MTN MoMo sandbox credentials (Airtel Money appears only as a UI/channel label; no provider integration)

## Build & Run

1. Open Android Studio and select **Open**, then choose the project directory
2. Allow Android Studio to sync the project and install dependencies
3. Create a `.env` file in the project root with the client keys (see `.env.example`):
   ```
   GEMINI_API_KEY=your_gemini_api_key
   MAPS_API_KEY=your_google_maps_api_key
   ```
4. Do **not** edit the manifest to add a Maps key. `MAPS_API_KEY` is injected at build time by the Secrets Gradle Plugin into the `${MAPS_API_KEY}` placeholder in `app/src/main/AndroidManifest.xml`. A missing/placeholder key renders the map fallback instead of crashing.
5. Backend configuration belongs to Cloud Functions, not the APK. `functions/src/momoCollection.ts` requires `MOMO_ENVIRONMENT=sandbox`, `MOMO_BASE_URL=https://sandbox.momodeveloper.mtn.com`, `MTN_SUBSCRIPTION_KEY`, `MTN_API_USER` and `MTN_API_KEY`. (Earlier versions of this README listed `MOMO_API_KEY`/`MOMO_API_PASS`/`WEBHOOK_SECRET`; those names are not read by the current code, and the HMAC webhook secret was deliberately removed.) Never commit real keys.
6. Connect a physical device or start an emulator with Google Play Services
7. Run the app using the **Run** button or `./gradlew installDebug`

> Build prerequisite: the current `gradle.properties` contains machine-specific Windows paths (`D:/…`) and a pinned local JDK. A clean checkout will not build until those are made portable — see `ASSESSMENT_REPORT.md` F-009. Installation instructions above are therefore **UNVERIFIED** on this commit.

## Project Structure

| Layer | Path |
|---|---|
| UI Screens | `app/src/main/java/com/example/ui/screens/` |
| UI Components | `app/src/main/java/com/example/ui/components/` |
| ViewModel | `app/src/main/java/com/example/ui/viewmodel/` |
| Data (DB) | `app/src/main/java/com/example/data/db/` |
| Remote Services | `app/src/main/java/com/example/data/remote/` |
| Repository | `app/src/main/java/com/example/data/repository/` |
| Utilities | `app/src/main/java/com/example/util/` |
| Cloud Functions | `functions/src/index.ts` |
| Firestore Rules | `firestore.rules` |
| Storage Rules | `storage.rules` |
| Unit Tests | `app/src/test/java/com/example/` |

## Repository review architecture

An independent review programme (2026-11-19) defines 15 specialist reviewers covering architecture, Android client, backend, data, integrations, authentication, security, payments, privacy/KYC, safety/fraud, QA, UI/UX & accessibility, performance/reliability, DevOps/release, and documentation.

| Document | Purpose |
|---|---|
| [`ASSESSMENT_REPORT.md`](ASSESSMENT_REPORT.md) | Investigation findings, complexity classification, risk register, reviewer rationale |
| [`REVIEWER_REGISTER.md`](REVIEWER_REGISTER.md) | Reviewer roster, coverage matrix, gates, status tracking |
| [`REVIEW_PLAN.md`](REVIEW_PLAN.md) | Execution waves, evidence protocol, report template |
| [`PRODUCTION_READINESS.md`](PRODUCTION_READINESS.md) | Per-domain release gates and blockers |
| [`reviewers/`](reviewers/) | One instruction file per reviewer + `reports/` output convention |

**Review cycle complete (2026-11-19):** all 15 reviews executed against baseline `6b39a10`; **0 PASS / 15 FAIL**. Readiness: 0 of 13 domains. See [`reviewers/reports/`](reviewers/reports/) and [`PRODUCTION_READINESS.md`](PRODUCTION_READINESS.md).

## Development Phases

1. Baseline, Infrastructure & Test Stabilization
2. Security, Document Storage & Database Rule Hardening
3. Unified Account Architecture, Roles & Mode Switching
4. Service Taxonomy & Skill Catalog System (125 Services)
5. Client Onboarding & Location Management
6. Worker KYC Verification & Guarantor Engine
7. Verification Lifecycle, Changes-Required & 48h SLA Tracking
8. Worker Training Tracking & Activation Gating
9. Admin verification queue and dispute arbitration console (Android, gated by a server-verified `admin` claim; there is no web dashboard)
10. Core Marketplace, Quotes & Job Types
11. Mobile Money Payments & Escrow Engine
12. Location Privacy, Google Maps & AI Services
13. Communication, Safety & Doorstep Safeguards
14. Offline Synchronization & Reliability
15. Production QA & Release Readiness
16. Production Readiness & Security Hardening

## Testing

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Run specific test
./gradlew testDebugUnitTest --tests com.example.ExampleRobolectricTest

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

Cloud Functions tests (pure TypeScript modules; no emulator or network):

```bash
cd functions
npm test        # 171 tests on Node 22.22.3 (2026-11-20; the count grows with each new suite)
```

> The Android commands above could not be executed in the assessment environment (no JDK/Android SDK). Treat historical build logs in the repository as stale until re-run on the current commit.

## License

Proprietary - All rights reserved
