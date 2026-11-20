// R07-P2-04: App Check on callables.
//
// Before this, a callable only needed a valid ID token. A token can be obtained by any client that
// can reach `verifyOtp` or by replaying a stolen session, so the backend had no way to tell the real
// app from a script. App Check attests the *app instance* (Play Integrity on Android) in addition to
// the user.
//
// This constant is spread into `runWith(...)` for every `onCall` handler, so the policy is visible in
// one place and testable in one place. `firestore` triggers and the HTTPS webhook are deliberately
// excluded: App Check does not apply to them (the webhook authenticates with an HMAC instead).

export const APP_CHECK = { enforceAppCheck: true } as const;

/**
 * Merges App Check enforcement into the runtime options of a callable.
 *
 * Used by handlers that already need `runWith` options (secrets, timeouts).
 */
export function withAppCheck<T extends Record<string, unknown>>(options: T): T & typeof APP_CHECK {
  return { ...options, ...APP_CHECK };
}
