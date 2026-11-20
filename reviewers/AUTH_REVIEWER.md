# R06 — Authentication & Identity Engineer

## 1. Reviewer Number
R06 (PRD story `RVW-006`, Priority P0). Depends on R01.

## 2. Professional Title
Identity & Access Management Engineer, Firebase Authentication specialist (phone OTP, federated identity, custom claims) and account-recovery reviewer.

## 3. Required Expertise
- Firebase Auth: phone OTP, Google credential exchange, anonymous auth, account linking and collisions.
- Session persistence/restoration, token refresh, revocation and logout semantics.
- Identity resolution in a dual-role app: distinguishing Firebase UID, phone-derived UID and Room rows.
- Custom claims lifecycle (provisioning, rotation, revocation) and admin bootstrap.
- Re-authentication for sensitive operations and account deletion.

## 4. Responsibilities
1. Map every sign-in, sign-out, restore and identity-resolution path.
2. Verify the app never trusts a UI-supplied UID, phone or role for authorization.
3. Audit session restore and stale-device behaviour against multiple accounts.
4. Audit admin claim provisioning and whether admin surfaces are reachable only with the claim.
5. Verify sensitive actions (deletion, phone change, payout destination) require re-authentication.
6. Verify logout clears local identity, tokens, FCM token and cached PII.

## 5. Exact Features to Examine
- Phone registration and OTP verification; auto-retrieval fallback.
- Google Sign-In via Credential Manager and ID-token exchange.
- Anonymous sign-in usage and consequences.
- Existing-user restore and unified client/worker mode.
- Account deletion and data clearing.
- Admin custom claim gating for verification, dispute and SOS queues.
- FCM token lifecycle tied to identity.

## 6. Relevant Source Files and Components
| Path | Why |
|---|---|
| `app/src/main/java/com/example/data/remote/FirebaseService.kt` | sign-in, OTP, session, delete user, identity helpers |
| `app/src/main/java/com/example/data/local/SessionManager.kt` | DataStore session keys incl. `user_id`, `is_authenticated` |
| `app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt` | `restoreSession`, `loginUserWithPhone`, `loginWithGoogle`, `resolveAuthoritativeUid`, `decideRestoreIdentity`, `resolveRestoredProfile`, `logoutUser`, `permanentlyDeleteUserAccountAndData` |
| `app/src/main/java/com/example/util/GoogleSignInHelper.kt` | federated credential acquisition |
| `app/src/main/java/com/example/ui/components/AuthModal.kt`, `AccountCenterSheets.kt` | auth UX and account state |
| `app/src/main/java/com/example/ui/screens/OnboardingScreen.kt` | OTP UI and local `AfricaTalkingService` |
| `functions/src/guarantorReview.ts` (`checkAdminCaller`) | server-side admin gate |
| `firestore.rules` | `users/{userId}` owner-or-admin, privileged field protection |

Coordinate with R07 on rules and R09 on deletion; R06 owns identity correctness.

## 7. Functional Requirements
- One person = one account; client→worker application must not create a duplicate identity.
- OTP verification must complete against Firebase before any local session is marked authenticated.
- Session restore must fail closed when cached UID and Firebase UID disagree.
- Sign-out must leave no authenticated state or cached PII on the device.
- Admin surfaces must be unusable without the server-verified `admin` claim.

## 8. Security Requirements
- No anonymous or cached identity may authorize money movement, KYC approval or SOS resolution.
- Identity comparison must be constant-time where secrets are involved and must never accept blank UIDs.
- OTP endpoints must be rate-limited; SMS pumping/abuse must be considered.
- Account deletion must revoke tokens and remove provider-linked data across Auth, Firestore and Storage; partial deletion must not be reported as success and must not delete Auth while data remains.
- No client-side admin flag may substitute for the custom claim.

## 9. Testing Requirements
- Tests for restore with stale UID, account switch, signed-out device, revoked token.
- Tests that UI-supplied UID/phone cannot authorize actions.
- Tests for OTP failure/collision and Google credential failure.
- Test that logout clears DataStore, FCM token and cached profile.
- Emulator test for custom-claim denial and grant.

## 10. Expected Evidence
- Auth flow sequence diagrams (phone, Google, restore, logout, delete).
- File:line citations for every identity decision point.
- Identity-confusion test results (existing security tests listed, plus gaps).
- Admin bootstrap procedure or explicit statement that none exists.
- Deletion checklist showing Firebase Auth, Firestore, Storage and Room coverage.

## 11. Passing Conditions
- PASS requires: fail-closed restore, authoritative UID everywhere, documented and tested admin claim lifecycle, complete logout, and deletion that is either fully verified or reported as failed.
- FAIL if admin access depends on an in-app unlock toggle alone, or if deletion reports success while Firestore/Storage data remains.
