import { APP_CHECK, withAppCheck } from "./appCheck";

/**
 * R13-P2-05: the runtime a function is given, stated instead of inherited.
 *
 * The review's point was that every function declared nothing, so all of them ran on the platform
 * defaults (60 s, 256 MB, one instance per request). For a handler that makes one provider call that
 * is fine; for one that makes a token exchange *and* a payout *and* a receipt write, 60 s is a cliff
 * a slow provider pushes the user over — the call fails after the money has moved.
 *
 * Two presets, so the choice is a decision in review rather than a default nobody looked at:
 *
 * - [CALLABLE_RUNTIME] — the platform default, written down. Firestore reads and writes only.
 * - [PROVIDER_CALL] — a third-party round trip (MTN MoMo, Africa's Talking, Gemini). Longer timeout
 *   and more memory, because the cost of timing out mid-transaction is a stuck payment, not a retry.
 *
 * `scripts/check_function_runtime.mjs` fails the build if a callable or trigger declares neither.
 */
export const CALLABLE_RUNTIME = {
  timeoutSeconds: 60,
  memory: "256MB" as const,
} as const;

export const PROVIDER_CALL = {
  timeoutSeconds: 120,
  memory: "512MB" as const,
} as const;

/** App Check + runtime in one place, since every callable wants both. */
export function callable(options: Record<string, unknown> = {}) {
  return withAppCheck({ ...CALLABLE_RUNTIME, ...options });
}

/** App Check + the provider preset; `options` can add secrets or narrow the timeout. */
export function providerCallable(options: Record<string, unknown> = {}) {
  return withAppCheck({ ...PROVIDER_CALL, ...options });
}

export { APP_CHECK };
