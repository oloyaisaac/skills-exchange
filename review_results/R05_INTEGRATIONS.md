# R05 — API & External Integrations Engineer

| Field | Value |
|---|---|
| Reviewer | R05 — Integrations (P1) |
| Assignment | `reviewers/API_INTEGRATION_REVIEWER.md` (read in full) |
| Executed | 2026-11-19 |
| Source reviewed | HEAD `06ff649` |
| Areas | Africa's Talking, MTN MoMo, Gemini, Maps, FCM, channel claims |
| Verdict | **FAIL** |
| Issues | 5 (2 HIGH, 3 MEDIUM) |

## Areas examined
Every outbound integration from call site to transport, credential handling, failure behaviour, and
whether UI/product claims match implemented providers.

## Source files inspected
`data/remote/AfricaTalkingService.kt`, `SmsNotificationService.kt`, `MoMoPaymentService.kt`,
`GeminiAudioTranscriptionService.kt`, `FirebaseService.kt`, `util/MapsApiKeyGuard.kt`, `NetworkConnectivityObserver.kt`;
`functions/src/momo*.ts`; `app/build.gradle.kts` (Maps key); manifest permissions.

## Features examined
OTP send/verify, SOS SMS, collection/disbursement, audio transcription, map rendering, push receive,
Airtel/card payment channels.

## Tests performed
- Grep/trace for credentials, endpoints and success returns.
- Compared UI channel labels to provider code.
- No network calls to any provider; no sandbox credentials.

## Verified working functionality
- MoMo collection/disbursement callables use server-held env credentials, trusted-document amounts and
  provider re-query confirmation.
- FCM receive plumbing exists for token registration and local notification display.

## Problems

| ID | Severity | Description | Component | Evidence | Expected | Actual | Recommended correction | Required verification |
|---|---|---|---|---|---|---|---|---|
| R05-01 | HIGH | A live-looking Africa's Talking key ships in the APK. | OTP/SMS client | `AfricaTalkingService.kt:15` hardcoded key (redacted); `isMinifyEnabled=false` | Secret server-side only | Key extractable by anyone | Rotate key, move OTP/SMS to a callable | Secret scan clean; old key revoked |
| R05-02 | HIGH | Transcription fabricates output when unconfigured. | Gemini service | Missing/placeholder key returns a canned result (`GeminiAudioTranscriptionService.kt:35–74`); direct Google call with client key | Explicit error state | Fabricated text indistinguishable from real | Remove fallback; surface error; move key server-side | Missing-key test shows error, not content |
| R05-03 | MEDIUM | Integration calls run in unmanaged coroutine scopes. | Africa's Talking client | `CoroutineScope(Dispatchers.IO).async` per call | Lifecycle-scoped suspend calls | Work leaks/uncancellable; errors lost | Use injected scope / `suspend` with caller scope | Cancellation test |
| R05-04 | MEDIUM | No contract tests and Maps uses a placeholder key. | Integrations | No provider fixtures/contract tests; `MAPS_API_KEY=DEFAULT_MAPS_API_KEY` (`build.gradle.kts:28`) | Tested contracts; real map or graceful absence | Payload drift and blank maps undetected | Add sandbox contract tests; inject Maps key per env | Recorded-fixture tests + map render |
| R05-05 | MEDIUM | Airtel Money and card are offered but not integrated. | Payment UI | `EscrowAndProofSheet.kt:972` lists `AIRTEL_MONEY`/`CARD`; no provider code; README advertises Airtel APIs | Only working channels offered | Users can select non-functional payment | Integrate or remove from UI/copy | Channel selection E2E per provider |

## Outcome classification

- **Verified working:** see "Verified working functionality" above.
- **Verified failing:** R05-01, R05-02, R05-03.
- **Not implemented:** R05-04, R05-05.
- **Partially implemented:** none.
- **Unable to verify:** runtime-only checks listed under "Limitations / unable to verify".

## Missing functionality
- Airtel/card processor, real Maps key, server-side OTP/SMS, transcription error surface, contract tests.

## Security concerns
The committed key is the most severe integration issue. Client-side transcription also exposes an API
key and sends safety-relevant audio directly to Google (R07/R09).

## Performance concerns
Per-call scopes and no timeout budget beyond OkHttp defaults; synchronous OTP path blocks onboarding UX.

## Recommendations
1. Rotate the AT key and move OTP/SMS behind an authenticated callable.
2. Delete the fabricated transcription path.
3. Integrate or remove Airtel/card.
4. Add provider contract tests and a real Maps key per environment.

## Limitations / unable to verify
No provider account, sandbox, SMS delivery or map render was available (**unable to verify**).

## Cross-references
R03, R06, R07, R08, MR-011/MR-051/MR-052/MR-053/MR-033.
