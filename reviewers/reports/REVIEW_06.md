# REVIEW-06 — Authentication & Authorization Review

| | |
|---|---|
| **Reviewer** | R06 — Authentication & Authorization Reviewer (P0) |
| **Wave** | 2 |
| **Date** | 2026-11-19 |
| **Commit reviewed** | `d448372` |
| **Verdict** | **FAIL** |

## 1. Scope and evidence

Traced every path that can set `isAuthenticated`, every credential exchange, `SessionManager`,
`restoreSession()`, `decideRestoreIdentity`, the four admin-gated callables and the admin UI gate.
Firestore's `isAdmin()` helper was checked against claim provisioning.

## 2. Authentication flows as implemented

| Flow | Implementation | Firebase credential created? | Verdict |
|---|---|---|---|
| Phone onboarding | `sendOtp` (VM `2312`) → `verifyOtp` (`2354`) → Africa's Talking HTTP API, key in APK | **No** — sets `AuthState.SignedIn` and `isAuthenticated=true` only | FAIL |
| Return visit | `restoreSession()` (`244`) requires a matching Firebase UID; otherwise fails closed | Yes (correctly) | PASS (static) |
| Google | `loginWithGoogle` (VM `1215`) | Yes | PARTIAL — no linking with phone identity |
| Firebase phone auth | `FirebaseService.kt:345–435`, `pendingAutoPhoneCredential` | Would, but **no production caller** | Dead code |
| Admin | `isAdmin()` custom claim | **Never provisioned** — no `setCustomUserClaims` anywhere | FAIL |

## 3. Confirmed findings

| ID | Severity | Evidence | Impact |
|---|---|---|---|
| R06-F1 | Critical | `verifyOtp` accepts `VerificationResponse.Valid` from the device's own ATT call and mints a session; `userId` falls back to `result.userId ?: africaStoredRequestId ?: phone`. No `signInWithCredential`, no custom token, no server verification. | Authentication is decided by client-side code with a key the user holds. The UID may literally be an ATT request id — not a Firebase identity. |
| R06-F2 | Critical | Because no Firebase user exists after OTP, `loginUser` (which needs `auth.currentUser.uid`) fails on a clean install; `signInAnonymously()` has zero production callers. | New phone users cannot complete registration, and the well-hardened `restoreSession` can never restore them. |
| R06-F3 | High | `setCustomUserClaims` does not exist; `authorizeAdmin` and `isAdmin()` depend on `request.auth.token.admin`. | The guarantor review, admin queue, dispute arbitration and `/reports` reads can never be exercised by a legitimate admin. Admin functionality is unprovisionable. |
| R06-F4 | High | Admin console "unlock" is `onUnlockOperator = { isAdminUnlocked = true }` (`MainScreen.kt:832`) — a local boolean with no credential or server check. | The admin surface is presentation-gated only. Data calls still fail for non-admins, so this is not privilege escalation, but it is not an authorization control either. |
| R06-F5 | Medium | Google and phone identities are never linked; two Firebase users can represent one person. | Split history, duplicate ratings, escrow owned by the "wrong" identity. |
| R06-F6 | Medium | Session has a 7-day expiry and device-id binding but no refresh, revocation, or "sign out all devices"; logout clears DataStore only. | Lost/stolen device retains a valid Firebase refresh token for up to 7 days. |
| R06-F7 | Info | `decideRestoreIdentity` and `resolveAuthenticatedProfile` are genuinely fail-closed and UID-keyed. | Positive — do not re-flag session restoration as fake. |

## 4. Requirement checks

| Requirement | Result |
|---|---|
| Credentials verified server-side | FAIL |
| Firebase identity established before `isAuthenticated` | FAIL |
| Admin claim provisionable via an audited path | FAIL |
| Session restore is fail-closed | PASS (static) |
| Re-authentication before destructive actions | FAIL (absent) |
| Account linking | FAIL |

## 5. What could not be verified

ATT response tampering on a rooted device, Firebase App Check enforcement, and token revocation
behaviour. **UNVERIFIED**; none is credited.

## 6. Passing conditions

1. OTP is either Firebase phone auth or an ATT check performed inside a callable that mints a
   custom token; the client never sets `isAuthenticated` without a credential.
2. A documented, audited admin-provisioning script sets `admin: true`; admin callables tested with
   and without the claim.
3. Deletion/role change requires fresh re-auth.
4. Google and phone identities link to one UID.
5. Session revocation on logout plus server-side token revocation.

## 7. Residual risk

As written, the only working identity path is Google, and even it has no admin story. Phone-first —
the product's primary persona — is non-functional and insecurely designed at the same time.

## Recommended remediation (not implemented)

1. Exchange the OTP for a Firebase credential (or mint a custom token server-side) before setting `isAuthenticated`.
2. Create an audited admin-claim provisioning path and test callables with/without the claim.
3. Require re-authentication for deletion and payout-destination changes.
4. Link Google and phone identities to one UID; revoke tokens on logout.

## 8. Cross-references

F-002, F-004, F-008; R02-F1, R05-F1, R07-F2, R09-F2, R10-F5.
