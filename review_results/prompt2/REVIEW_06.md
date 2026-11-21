# R06 — Authentication & Identity Engineer

| Field | Value |
|---|---|
| Reviewer | R06 — Authentication & Identity (P0) |
| Assignment | `reviewers/AUTH_REVIEWER.md` (read in full) |
| Executed | 2026-11-20 |
| Source reviewed | `arena/0570a462-skills-exchange` @ `aa4a0bc` |
| Mode | Review only — no auth code changed |
| Auth execution | **UNVERIFIED** — no Firebase project/device; static + unit-test inspection |
| Verdict | **FAIL** |

## 1. Sign-in and restore paths

| Flow | Implementation | Assessment |
|---|---|---|
| Phone OTP | `sendOtp`/`verifyOtp` callables; OTP verified server-side; server mints a custom token; client `signInWithCustomToken` (`AfricaTalkingService.kt:13–60`) | Correct: local session is marked authenticated only after a real Firebase user exists |
| Google | `signInWithGoogle(idToken)` → Firebase credential; UID required non-blank; profile restored from Room then Firestore (`ViewModel.kt:1237–1281`) | Works, but creates a **separate identity** from the phone account (see F2) |
| Session restore | 7-day expiry, device-id binding, `decideRestoreIdentity(cachedUid, firebaseUid)` requires an exact match and discards stale DataStore identity (`ViewModel.kt:246–286`) | Fail-closed; strong |
| Logout | mirror identity → `revokeMyTokens` → Room wipe → `signOut` → DataStore clear (`ViewModel.kt:2473–2500`) | Good, except FCM token (F3) |
| Deletion | fresh-auth check → Firestore deletes → `deleteCurrentUser` → local wipe (`ViewModel.kt:1358–1395`) | Ordering/partial-failure defect (F1) |

## 2. Identity authority

- `resolveAuthoritativeUid` is the only accepted identity source and rejects blank/synthetic values;
  `postedByUid`, job ownership and cashout all resolve through it.
- The legacy "newest Room row" query has no production consumer; security tests keep it as a trap.
- No UI-supplied uid/phone/role is used to authorize a write in the inspected paths.
- Admin: `getAdminAccess` returns the verified claim and `MainScreen` unlocks from
  `isAdminVerified`; no client toggle remains. Server calls route through `checkAdminCaller`.

## 3. Deletion correctness (fail condition)

`permanentlyDeleteUserAccountAndData` computes:

```
firestoreDeleted = deleteUserFirestoreData(userId, jobIds)
authDeleted      = deleteCurrentUser()          // runs regardless of firestoreDeleted
```

If Firestore deletion fails (permissions, network, a single failing document), the Firebase Auth user
is still deleted. The account can no longer sign in, but its `users`, `worker_kyc`,
`guarantor_submissions`, `disputes` and other documents remain — orphaned personal data with no owner
able to delete it. R06's passing condition explicitly forbids deleting Auth while data remains.

Additionally, `deleteUserFirestoreData` (`FirebaseService.kt:311–380`) performs **no Storage
deletion**: National ID/guarantor photos and job photos remain in Cloud Storage after "permanent"
deletion. The UI reports success unless a Firestore/Auth call throws.

## 4. Identity linking (collision)

Phone sign-in produces a custom-token UID derived from the phone lookup; Google sign-in produces the
Google UID. There is no `linkWithCredential`, no merge path and no conflict UI. The same person who
registers by phone and later taps "Continue with Google" gets a second account, second wallet and
second KYC state. `GoogleSignInHelper` requests an ID token only; it never links.

## 5. Session and token lifecycle

- Refresh tokens are revoked on logout (`revokeMyTokens` → Admin SDK) — good.
- FCM: `logoutUser` sets `fcmToken.value = null` locally but never calls
  `SkillsExchangeMessagingService.deleteToken()` (helper exists, unused) and the token is not
  registered server-side at all (R05). A signed-out device can keep receiving topic pushes.
- Account deletion also never deletes the FCM token.
- Re-authentication: deletion is blocked when `authTime` is older than five minutes, but there is no
  re-auth flow to satisfy the check; the user is told to "sign in again" and must log out manually.
  The server-side freshness window is enforced in rules, so the control is real but the UX is a dead end.

## 6. Admin claim lifecycle

Provisioning exists (`scripts/grant-admin.js`: resolve by uid/email, preserve other claims, revoke
refresh tokens, write `admin_audit`). Server gates every admin callable; rules make `admin_audit`
client-immutable. Not executed (no service account) — **UNVERIFIED** end to end, but the code path is
complete and documented.

## 7. Tests

41 unit tests include identity-confusion suites (`PhoneAuthIdentitySecurityTest`,
`DeviceLocalProfileRecoveryF3SecurityTest`, `LegacyCurrentUserSweepSecurityTest`,
`ChatSenderIdentityF5SecurityTest`, `SosRaiseAuthorizationSecurityTest`, etc.). No test covers
deletion ordering, Storage cleanup, FCM token deletion or Google/phone linking. None runs here.

## 8. Findings

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| R06-P2-01 | **CRITICAL** | Auth user deleted even when Firestore deletion failed → orphaned personal data | `ViewModel.kt:1372–1373` |
| R06-P2-02 | **CRITICAL** | Permanent deletion leaves all Storage photos (KYC, guarantor, jobs) | `FirebaseService.kt:311–380`; no Storage delete call |
| R06-P2-03 | HIGH | Phone and Google identities are never linked; one person can hold two accounts | `GoogleSignInHelper.kt`; `ViewModel.kt:1237` |
| R06-P2-04 | MEDIUM | Logout/deletion do not delete the FCM token; helper unused | `ViewModel.kt:2490`; `SkillsExchangeMessagingService.deleteToken` |
| R06-P2-05 | MEDIUM | Fresh-auth gate has no in-app re-authentication path | `ViewModel.kt:1366–1369` |
| R06-P2-06 | LOW | Google web client id hardcoded with a stale "replace" comment | `GoogleSignInHelper.kt:10` |

## 9. Verdict

**FAIL.** Restore, authoritative UID resolution, server-side OTP and admin claim gating are solid, but
deletion violates R06's explicit fail condition (Auth removed while data/Storage remains) and there is
no identity linking. All live auth behaviour is **UNVERIFIED**.
