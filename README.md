# Skills Exchange

A hyperlocal peer-to-peer services marketplace Android app for East African urban centers (Uganda, Kenya, Tanzania, Rwanda). Connects nearby residents with verified local workers for home repairs, maintenance, technical tasks, personal services, and recurring contracts.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: MVVM with Repository pattern
- **Local Database**: Room
- **Backend**: Firebase (Firestore, Auth, Storage, Cloud Messaging)
- **Serverless**: Firebase Cloud Functions
- **Payments**: MTN MoMo / Airtel Money Collection & Escrow APIs
- **Maps**: Google Maps Android SDK
- **AI**: Gemini 2.0 Flash (audio transcription, search grounding)
- **Build**: Gradle with Kotlin DSL

## Features

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
- **Offline Support**: Action queueing with automatic sync on reconnect
- **Ratings & Reviews**: Worker rating aggregation with district-level privacy
- **Push Notifications**: FCM for job updates, quotes, payments, and verification status

## Prerequisites

- Android Studio (Ladybug or newer recommended)
- JDK 17
- Firebase project with Android app configured
- Google Maps API key
- Gemini API key
- MTN MoMo / Airtel Money sandbox credentials

## Build & Run

1. Open Android Studio and select **Open**, then choose the project directory
2. Allow Android Studio to sync the project and install dependencies
3. Create a `.env` file in the project root with the following keys:
   ```
   GEMINI_API_KEY=your_gemini_api_key
   MAPS_API_KEY=your_google_maps_api_key
   MOMO_API_KEY=your_mtn_momo_api_key
   MOMO_API_USER=your_momo_username
   MOMO_API_PASS=your_momo_password
   WEBHOOK_SECRET=your_webhook_secret
   ```
4. Add your Google Maps API key to `app/src/main/AndroidManifest.xml` inside the `<application>` tag:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_MAPS_API_KEY" />
   ```
5. Connect a physical device or start an emulator with Google Play Services
6. Run the app using the **Run** button or `./gradlew installDebug`

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

## Development Phases

1. Baseline, Infrastructure & Test Stabilization
2. Security, Document Storage & Database Rule Hardening
3. Unified Account Architecture, Roles & Mode Switching
4. Service Taxonomy & Skill Catalog System (125 Services)
5. Client Onboarding & Location Management
6. Worker KYC Verification & Guarantor Engine
7. Verification Lifecycle, Changes-Required & 48h SLA Tracking
8. Worker Training Tracking & Activation Gating
9. Admin Operations Dashboard & Verification Queue
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

## License

Proprietary - All rights reserved
