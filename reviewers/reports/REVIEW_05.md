# REVIEW-05 — Integrations Review

| | |
|---|---|
| **Reviewer** | R05 — Integrations Reviewer (P1) |
| **Wave** | 2 |
| **Date** | 2026-11-19 |
| **Commit reviewed** | `d448372` |
| **Verdict** | **FAIL** |

## 1. Scope and evidence

Every outbound integration was traced from call site to transport: Africa's Talking (SMS/OTP), MTN
MoMo (collection/disbursement), Google Gemini (audio transcription), Google Maps, Firebase Cloud
Messaging (receive side), and the Play Services auth stack. UI labels were compared with actual
provider support.

## 2. Integration matrix

| Integration | Configured | Real network call | Failure behaviour | Verdict |
|---|---|---|---|---|
| Africa's Talking OTP | Hardcoded key in source (`AfricaTalkingService.kt:15`) | Yes, from the device | Returns `OtpResponse`; caller may treat as success | FAIL (credential + trust model) |
| Africa's Talking SMS/SOS | Same client | **No** — `SmsNotificationService` logs only and returns `SENT` | Silent false success | FAIL (R10-F1) |
| MTN MoMo collection | Server env vars (`MTN_*`) | Yes (sandbox base URL) | Fails closed via provider re-query | PASS (static) |
| MTN MoMo disbursement | Server env vars | Callable exists | Not invoked by client for release | PARTIAL (R08-F2) |
| Airtel Money | **Nothing** | No | Tab is a UI label only (`EscrowAndProofSheet.kt:972`) | FAIL |
| Gemini transcription | `BuildConfig.GEMINI_API_KEY`, called directly from app | Yes if key present; otherwise a hardcoded placeholder transcription is returned | Fabricated output indistinguishable from real | FAIL |
| Google Maps | Placeholder `DEFAULT_MAPS_API_KEY` (`build.gradle.kts:28`) | No tiles | Map degrades; no error surfaced | FAIL (cosmetic only) |
| FCM | Receive-only service exists | No server send (R03-F4) | No push ever arrives | FAIL |
| Retrofit/OkHttp | Present | Unused for MoMo (functions own it) | Dead dependency surface | Info |

## 3. Confirmed findings

| ID | Severity | Evidence | Impact |
|---|---|---|---|
| R05-F1 | Critical | `AFRICASTALKING_API_KEY = "atsk_b3bc…aeb8"` committed at `AfricaTalkingService.kt:15`, used from the APK; no App Check. | Anyone with the APK can send SMS/OTP on the project's account at its cost, and the key requires immediate rotation (F-002). |
| R05-F2 | High | `GeminiAudioTranscriptionService.kt` returns a **fabricated** transcription when the key is missing (`:36` branch), and calls Google with an API key embedded in the app (`:74`). | Safety-relevant evidence (dispute audio) can be invented; the key is extractable. |
| R05-F3 | High | `SmsNotificationService` logs and returns `SENT`; `raiseSos` relies on it. | Emergency dispatch is a no-op that reports success (F-005). |
| R05-F4 | High | Airtel Money appears in feature copy, payment tabs and compliance text but has no integration. | Users can select a channel that cannot work; escrow claims are false. |
| R05-F5 | Medium | `sendOtp` and `sendSms` create `CoroutineScope(Dispatchers.IO).async` per call instead of a scoped suspend call. | Work outlives the caller, cancellation is ignored, and exceptions can be dropped. |
| R05-F6 | Medium | No contract tests, no recorded fixtures, no provider sandbox CI run. | Payload changes break silently; 111 Node tests are unit-level only. |
| R05-F7 | Low | Maps key placeholder means location features cannot render in a release build. | Product promise "nearby workers" has no map. |

## 4. Requirement checks

| Requirement | Result |
|---|---|
| No secrets in the client | FAIL |
| Every integration has failure handling that never fakes success | FAIL |
| Provider status is verified server-side | PASS (MoMo only) |
| Documented vs implemented channels match | FAIL (Airtel) |
| Contract/regression tests | FAIL |

## 5. Passing conditions

1. AT key removed from the APK, rotated, and OTP/SMS moved behind an authenticated callable with
   per-phone rate limits.
2. Transcription never fabricates: missing key ⇒ explicit error state.
3. Airtel either integrated or removed from all copy and UI.
4. Contract tests against provider sandboxes in CI.
5. Maps key injected per environment and absence surfaced to the user.

## 6. Residual risk

Three integrations currently report success without doing the work (SMS, transcription fallback,
Maps). This is the most dangerous failure mode in the product because it is invisible in logs.

## Recommended remediation (not implemented)

1. Rotate the Africa's Talking key and move OTP/SMS behind a server callable.
2. Remove the fabricated transcription fallback; surface a real error state.
3. Integrate Airtel/card or remove them from UI and copy; inject a real Maps key per environment.
4. Add provider contract tests against sandboxes in CI.

## 7. Cross-references

F-002, F-005; R06 (OTP), R10 (SOS), R07 (secret), R03-F4 (FCM), R15 (Airtel claims).
