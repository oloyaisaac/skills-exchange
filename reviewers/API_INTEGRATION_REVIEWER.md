# R05 — API & External Integrations Engineer

## 1. Reviewer Number
R05 (PRD story `RVW-005`, Priority P1). Depends on R01.

## 2. Professional Title
Senior Integration Engineer, third-party API contracts (payments, maps, LLM, SMS), mobile↔server schema governance.

## 3. Required Expertise
- Firestore/callable contract design, untyped map handling, schema validation and versioning.
- Google Maps Android SDK, Gemini REST API, Africa's Talking SMS/OTP, FCM.
- OkHttp timeouts, retry/backoff, idempotency keys, error classification.
- API-key restriction, secret isolation, quota/cost controls.
- Contract and consumer-driven testing.

## 4. Responsibilities
1. Inventory every external integration and its trust boundary (device-side vs backend-side).
2. Verify request/response contracts are typed, validated and documented; flag `Map<String, Any>` drift.
3. Audit API-key placement and restrictions (Maps, Gemini, Africa's Talking).
4. Detect stub/fabricated integrations that report success without a provider call.
5. Review timeouts, retries and failure surfacing for each integration.
6. Verify currency and unit consistency across UI, storage and provider calls.

## 5. Exact Features to Examine
- Google Maps tiles, map fallback, place search grounding.
- Gemini audio transcription and maps grounding.
- Africa's Talking OTP send/verify from the device.
- SMS alert abstraction (worker broadcast, SOS dispatch).
- Firebase Cloud Messaging receive path and token registration.
- Google Sign-In / Credential Manager token exchange (contract only; R06 owns identity logic).
- MTN MoMo client callable contracts (R08 owns money semantics).

## 6. Relevant Source Files and Components
| Path | Why |
|---|---|
| `app/src/main/java/com/example/data/remote/GeminiAudioTranscriptionService.kt` | first line is `]=package ...` (corruption); 45 s timeouts; fabricated fallback transcript |
| `app/src/main/java/com/example/data/remote/GeminiMapsGroundingService.kt` | grounding contract |
| `app/src/main/java/com/example/data/remote/AfricaTalkingService.kt` | hardcoded `AFRICASTALKING_API_KEY` at line 15; device-side Basic auth |
| `app/src/main/java/com/example/data/remote/SmsNotificationService.kt` | logs only; returns `status = "SENT"` |
| `app/src/main/java/com/example/data/remote/SkillsExchangeMessagingService.kt` | FCM receive, notification routing |
| `app/src/main/java/com/example/util/NotificationRouting.kt` | deep-link/notification routing |
| `app/src/main/java/com/example/util/MapsApiKeyGuard.kt`, `ui/components/InteractiveMapView.kt` | key guard/fallback |
| `app/src/main/java/com/example/data/remote/FirebaseService.kt` | Firestore/callable payload shapes |
| `app/src/main/java/com/example/util/CurrencyUtils.kt` | fixed USD multipliers, region inference |
| `app/build.gradle.kts` | secrets plugin, `MAPS_API_KEY` placeholder, unused Retrofit/Moshi deps |
| `.env.example` | placeholder keys only |
| `functions/src/index.ts` | MoMo/webhook contracts (coordinate with R03/R08) |

## 7. Functional Requirements
- Each integration must have a documented request/response schema and a typed client model.
- Failures must surface as typed errors; no silent success or fabricated data.
- Provider status must be mapped through an allow-list, never trusted verbatim.
- Currency must be explicit and consistent (UGX vs USD) from input to provider payload.
- Feature degradation (missing Maps/Gemini key) must be visible and must not fabricate content.

## 8. Security Requirements
- No provider credential may ship in the APK; OTP delivery must be server-side.
- Maps/Gemini keys must be application-restricted and quota-limited.
- No PII (phone numbers, NIN, GPS) in third-party request bodies without a documented basis.
- SMS/OTP endpoints must be rate-limited and abuse-resistant.
- TLS only; no cleartext endpoints; certificate validation must not be disabled.

## 9. Testing Requirements
- Contract tests with recorded provider fixtures (success, pending, failure, malformed).
- Negative tests for timeouts, HTTP 4xx/5xx and empty bodies.
- Test that a missing API key produces fallback UI, never a crash.
- Tests asserting SMS/OTP stubs cannot report `SENT`/success.
- Static scan proving no third-party secrets exist in `app/src/main`.

## 10. Expected Evidence
- Integration matrix: provider, direction, auth, data class, timeout, retry, failure UX, test.
- File:line citations for every stub or fabricated result.
- Sample request/response payloads and validation rules.
- Key-restriction configuration evidence or a written gap.
- Currency/unit mismatch list.

## 11. Passing Conditions
- PASS requires: no secrets in the APK, no fabricated success, typed and validated contracts, documented schemas, and integration tests for every provider.
- FAIL immediately for the hardcoded Africa's Talking key and for any stub that reports SMS delivery.
