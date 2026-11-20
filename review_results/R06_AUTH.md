# R06 — Authentication & Authorization Engineer

| Field | Value |
|---|---|
| Reviewer | R06 — Authentication & Authorization (P0) |
| Assignment | `reviewers/AUTH_REVIEWER.md` (read in full) |
| Executed | 2026-11-19 |
| Source reviewed | HEAD `06ff649` |
| Areas | OTP, session restore, Firebase identity, admin claims, re-auth, linking |
| Verdict | **FAIL** |
| Issues | 4 (1 CRITICAL, 2 HIGH, 1 MEDIUM) |

## Areas examined
Every path that can set `isAuthenticated`, credential exchange, `SessionManager`/DataStore session,
`restoreSession`/`decideRestoreIdentity`, admin claim provisioning, destructive-action re-auth, logout.

## Source files inspected
`ui/viewmodel/SkillsExchangeViewModel.kt` (`sendOtp` 2312, `verifyOtp` 2354, `loginUser` 1096,
`loginWithGoogle` 1215, `restoreSession` 244, deletion 1336); `data/remote/FirebaseService.kt`
(phone auth 345–435, delete 275–315); `data/local/SessionManager.kt`; `firestore.rules` (`isAdmin`);
`ui/screens/MainScreen.kt` (admin unlock 832).

## Features examined
Phone onboarding, Google sign-in, session restore/expiry, admin queue unlock, account deletion,
logout, role switching.

## Tests performed
- Traced all auth call chains and grepped for `setCustomUserClaims` (0), `signInWithCredential`
  in the OTP path (0), production `signInAnonymously()` callers (0).
- Read the dead Firebase phone-auth implementation and its tests.
- No Firebase project, emulator or device; no live OTP.

## Verified working functionality
- `restoreSession` is genuinely fail-closed: cached DataStore identity is accepted only when it matches
  the Firebase UID; otherwise the session is cleared.
- Session has a 7-day expiry and device-id binding.
- Admin callables do check the `admin` claim.

## Problems

| ID | Severity | Description | Component | Evidence | Expected | Actual | Recommended correction | Required verification |
|---|---|---|---|---|---|---|---|---|
| R06-01 | CRITICAL | OTP verification creates a local session but no Firebase identity. | Onboarding/auth | `verifyOtp` VM `2354–2405` sets `SignedIn` from ATT response; `userId` falls back to request id; Firebase phone-auth code has no caller; `signInAnonymously` unused | Authenticated Firebase user after OTP | Local flag only; `loginUser` cannot resolve a UID | Verify OTP in a callable that mints a custom token, or use Firebase phone auth | Clean-install signup asserting a Firebase UID |
| R06-02 | HIGH | Admin role can never be provisioned and the UI gate is cosmetic. | Claims + admin UI | 0 `setCustomUserClaims`; `isAdminUnlocked = true` locally (`MainScreen.kt:832`) | Audited claim provisioning; server-verified UI gate | No admin can be created; unlock is a boolean | Provision claims via audited script; gate UI on server response | Callable admin test with/without claim |
| R06-03 | HIGH | Deletion and sensitive changes lack re-authentication and token revocation. | Auth lifecycle | Deletion calls `deleteCurrentUser()` without fresh auth; logout clears DataStore only; no `revokeRefreshTokens` | Recent-auth requirement; revocable sessions | Stale token remains valid; deletion can fail after local wipe | Require fresh auth; revoke tokens on logout/deletion | Re-auth + revocation tests |
| R06-04 | MEDIUM | Google and phone identities are never linked. | Auth | Separate flows; no `linkWithCredential`/account linking | One UID per person | Duplicate identities and histories | Implement linking and merge rules | Linked-account E2E |

## Outcome classification

- **Verified working:** see "Verified working functionality" above.
- **Verified failing:** R06-01, R06-02.
- **Not implemented:** R06-03, R06-04.
- **Partially implemented:** none.
- **Unable to verify:** runtime-only checks listed under "Limitations / unable to verify".

## Missing functionality
Admin bootstrap, re-authentication, token revocation, account linking, OTP abuse throttling.

## Security concerns
Authentication is client-asserted on the primary path; the ATT key used for that assertion ships in the
APK. If a Firebase session exists from another path, admin UI state can be toggled locally (data calls
still fail), so this is a UX/integrity issue rather than immediate escalation.

## Performance concerns
None material; OTP is a single network round-trip.

## Recommendations
1. Make OTP produce a real credential before any signed-in state.
2. Add an audited admin provisioning path and server-backed UI gating.
3. Require re-auth and revoke tokens for destructive actions.
4. Link identities.

## Limitations / unable to verify
No Firebase project, phone number, emulator or device. Token handling and ATT responses are **unable to verify**.

## Cross-references
R02 (onboarding), R07 (key), R09 (erasure), R10 (admin), MR-022/MR-023/MR-024/MR-025.
