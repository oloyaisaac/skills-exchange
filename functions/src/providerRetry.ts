/**
 * R13-P2-07: bounded retry for provider *reads*.
 *
 * Every third-party call in this backend was one shot. A mobile-money status query that hit a 503 or a
 * dropped socket answered the user "we don't know" even though the network blip had nothing to do with
 * their payment, and they had to retry by hand.
 *
 * The rule this module encodes is deliberately narrow:
 *
 *   - Retried: idempotent reads (status lookups, token fetch) on transport errors, timeouts, 429 and
 *     5xx. Running them twice cannot move money.
 *   - Not retried: anything that creates a collection, payout, refund or SMS. Those carry a provider
 *     reference and are already guarded by `already_pending`; a blind retry there risks a second
 *     payment. The caller decides, by passing only reads in here.
 *   - Never retried: 4xx other than 429 — a malformed request stays malformed.
 *
 * Backoff is exponential with full jitter, capped, and the attempt count is small (3) so the whole
 * sequence fits inside the [PROVIDER_CALL] runtime in `runtime.ts`.
 */

export interface ProviderErrorShape {
  status?: number;
  code?: string;
  message?: string;
}

/** True when re-sending the request could plausibly succeed. */
export function isRetryableProviderError(error: unknown): boolean {
  const err = (error || {}) as ProviderErrorShape;
  const status = typeof err.status === "number" ? err.status : undefined;

  if (status !== undefined) {
    if (status === 429) return true;
    // 5xx is the provider having a bad time; 4xx is us having sent something wrong.
    return status >= 500 && status <= 599;
  }

  const code = (err.code || "").toUpperCase();
  if (["ECONNRESET", "ECONNREFUSED", "ETIMEDOUT", "ECONNABORTED", "EAI_AGAIN", "ENOTFOUND"].includes(code)) {
    return true;
  }
  return /timeout|socket hang up|network error/i.test(err.message || "");
}

export interface RetryOptions {
  /** Total attempts including the first one. Default 3. */
  attempts?: number;
  /** First backoff step; doubles each attempt. Default 250 ms. */
  baseDelayMs?: number;
  /** Ceiling for one backoff step. Default 2 000 ms. */
  maxDelayMs?: number;
  /** Injected so tests do not wait. */
  sleep?: (ms: number) => Promise<void>;
  /** Injected so a caller can narrow the rule (e.g. treat 409 as idempotent-safe). */
  isRetryable?: (error: unknown) => boolean;
  /** Called before each wait, so a retry is visible in the logs rather than silent. */
  onRetry?: (error: unknown, attempt: number, delayMs: number) => void;
}

const realSleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/** Delay before attempt `attempt + 1`, with full jitter so a fleet does not retry in lockstep. */
export function backoffDelay(attempt: number, baseDelayMs = 250, maxDelayMs = 2_000): number {
  const ceiling = Math.min(maxDelayMs, baseDelayMs * Math.pow(2, Math.max(0, attempt - 1)));
  // Deterministic floor keeps the smallest wait non-zero even when the jitter rolls 0.
  return Math.max(1, Math.floor(ceiling / 2 + Math.random() * (ceiling / 2)));
}

export async function withProviderRetry<T>(operation: () => Promise<T>, options: RetryOptions = {}): Promise<T> {
  const attempts = Math.max(1, options.attempts ?? 3);
  const sleep = options.sleep ?? realSleep;
  const isRetryable = options.isRetryable ?? isRetryableProviderError;

  let lastError: unknown;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      return await operation();
    } catch (error) {
      lastError = error;
      if (attempt === attempts || !isRetryable(error)) break;
      const delayMs = backoffDelay(attempt, options.baseDelayMs, options.maxDelayMs);
      options.onRetry?.(error, attempt, delayMs);
      await sleep(delayMs);
    }
  }
  throw lastError;
}
