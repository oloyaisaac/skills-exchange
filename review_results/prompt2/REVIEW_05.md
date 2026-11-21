# R05 — API & External Integrations Engineer

| Field | Value |
|---|---|
| Reviewer | R05 — API & External Integrations (P1) |
| Assignment | `reviewers/API_INTEGRATION_REVIEWER.md` (read in full) |
| Executed | 2026-11-20 |
| Source reviewed | `arena/0570a462-skills-exchange` @ `aa4a0bc` |
| Mode | Review only — no integration code changed |
| Provider sandbox calls | **UNVERIFIED** — no MTN/Africa's Talking/Gemini credentials or device |
| Verdict | **FAIL** |

## 1. Integration matrix

| Integration | Side | Auth | Timeout / retry | Failure UX | Test |
|---|---|---|---|---|---|
| Africa's Talking OTP | **server** (`sendOtp`/`verifyOtp`) | server env key | provider call, no explicit axios timeout in OTP helper (inherits axios default) | typed callable error; client shows message | pure `otp.test.mjs` |
| Africa's Talking SMS (broadcast/SOS) | server (`sendSmsAlert`, SOS trigger) | server env key | 15 s token / 20 s send, no retry | outcome recorded SENT/FAILED | `sms.test.mjs`, `sosDispatch.test.mjs` |
| MTN MoMo collection | server callable | OAuth token + subscription key | 15–20 s, no automatic retry | PENDING only; webhook re-query | `momoCollection/Confirmation` tests |
| MTN MoMo payout | server callable | same | 20 s | QUEUED + provider status query | `momoDisbursement` tests |
| MTN refund (new) | server callable | same | 15–20 s | FAILED record, no job state change | `refund.test.mjs` |
| Gemini transcription | server callable | server env key | not inspected in provider helper | `null` → UI error | none |
| Gemini Maps grounding | **device** | `BuildConfig.GEMINI_API_KEY` | OkHttp defaults | error string inline | `MapsApiKeyFallbackTest.kt` (Robolectric) |
| FCM receive | device | google-services | n/a | local notification | none |
| FCM token registration | device | n/a | **not implemented** | none | none |
| Google Maps SDK | device | manifest placeholder `MAPS_API_KEY` | n/a | fallback UI (`MapsApiKeyGuard`) | `MapEmptyProvidersOverlayTest.kt` |

## 2. Fabricated success (explicit FAIL condition)

`SmsNotificationService` still reports delivery without a provider call:

- `sendSmsAlert` builds `SMS-AFR-…`, logs the body, waits `delay(300)` and returns
  `status = "SENT"` (`SmsNotificationService.kt:19–37`).
- `notifyOfflineWorkersInRadius` counts every call as sent (`:43–56`).
- `dispatchEmergencySosAlert` does the same for SOS (`:58–80`).

The class is a leftover client abstraction; the real SMS path is server-side (`onJobCreated`,
`onSosIncidentCreated`). Nothing calls this class in production paths found by grep, but it remains in
the release source set and its API invites a caller to display a fake SMS/SOS delivery. R05's passing
conditions fail immediately on a stub that reports SMS delivery.

## 3. Credentials and key placement

- Africa's Talking key is no longer in the device code: `AfricaTalkingService.kt` calls the
  `sendOtp`/`verifyOtp` callables and holds no `AFRICASTALKING_API_KEY` (grep = 0 in `app/src`).
- Gemini transcription key moved server-side; missing config yields `null`, not a fake job.
- **Gemini Maps grounding still ships a key in the APK** (`GeminiMapsGroundingService.kt:36` reads
  `BuildConfig.GEMINI_API_KEY` and calls `generativelanguage.googleapis.com` directly). No evidence of
  an application/quota restriction; the key guard only covers the "missing key" case.
- Maps SDK placeholder is `DEFAULT_MAPS_API_KEY` (`app/build.gradle.kts:28`); a real key comes from
  `.env` via the Secrets Gradle plugin. Restriction configuration is not in the repository —
  **UNVERIFIED**.
- `secret_scan.mjs` passes; it allow-lists the Firebase client key as designed.

## 4. FCM contract

- `SkillsExchangeMessagingService.onNewToken` logs the full token (`:58`) and
  `sendTokenToServer` only logs it again (`:81–85`); no Firestore/callable write exists. The backend
  therefore has no per-user device token, which is consistent with R03's finding that only the SOS
  topic push exists.
- `onMessageReceived` reads `data.type/title/body/jobId` and posts a local notification; no typed
  message schema and no validation of `jobId` before it is put in an Intent extra.
- Tokens in logcat are a sensitive-data exposure on shared/log-collecting devices.

## 5. Currency and unit consistency

- `CurrencyUtils.format` treats its input as **USD** and multiplies by 3,700 for UGX
  (`CurrencyUtils.kt:38–45`), and the app default region is Uganda.
- Job creation builds `laborTotal = laborHours * laborRate` with `laborRate` defaults like 60
  (`Repository.kt:562–566`) and stores that number as `budget`/`grandTotal`.
- The server treats `grandTotal` as **UGX** and requires a whole number
  (`momoCollection.ts:217–226`), while quotes such as `123.75` are producible from the client math.
- Consequence: displayed amounts can be 3,700× the stored value, and non-integer stored totals are
  refused by the collection callable. No single documented money unit exists end to end.

## 6. Contracts and validation

- 16 callable payloads in `FirebaseService.kt` are `Map<String, Any>` with unchecked casts; response
  fields are read with `as?` and silently default. No versioned schema or contract test exists.
- Server callables validate inputs (R03), and provider status strings are mapped by allow-lists
  (`mapProviderTransactionStatus`), not trusted verbatim — good.
- Gemini Maps grounding asks the model to "explicitly list 3 real/grounded" places; if grounding is
  unavailable the model can still return invented names, and the UI has no "ungrounded" marker.

## 7. Network hygiene

- No `usesCleartextTraffic` and no `http://` provider endpoint found in the inspected remote clients;
  all endpoints are HTTPS.
- OkHttp clients use defaults in the Gemini service; no certificate pinning or explicit timeouts.
- No retry/backoff on any provider call; failures surface as typed errors or `null`.

## 8. Findings

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| R05-P2-01 | **CRITICAL** | SMS/SOS stub returns `SENT` with no provider call | `SmsNotificationService.kt:19–80` |
| R05-P2-02 | HIGH | FCM token never registered server-side; token logged in plaintext | `SkillsExchangeMessagingService.kt:58,81–85` |
| R05-P2-03 | HIGH | Currency unit mismatch (USD display multiplier vs UGX server; non-integer totals rejected) | `CurrencyUtils.kt:38–45`; `Repository.kt:562–566`; `momoCollection.ts:217–226` |
| R05-P2-04 | MEDIUM | Gemini Maps key in APK with no restriction evidence | `GeminiMapsGroundingService.kt:36`; `build.gradle.kts:28` |
| R05-P2-05 | MEDIUM | Ungrounded model output presented as real places | `GeminiMapsGroundingService.kt:44–46` |
| R05-P2-06 | LOW | Untyped `Map<String, Any>` callable contracts, no contract tests | `FirebaseService.kt` (16 occurrences) |
| R05-P2-07 | LOW | No retry/backoff and default OkHttp timeouts on Gemini | `GeminiMapsGroundingService.kt` client builder |

## 9. Verdict

**FAIL.** The hardened server integrations (OTP, MoMo, SMS, refund) are materially sound and no
Africa's Talking secret remains in the APK, but the device SMS stub still reports success, FCM tokens
never reach the backend, and money units are inconsistent between display and provider payloads.
Every live provider interaction is **UNVERIFIED**.
