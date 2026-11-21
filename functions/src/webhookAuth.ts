// REM-021: authentication, freshness and throttling policy for the MoMo callback.
//
// MTN does not sign callback bodies, so the endpoint is protected by a shared secret configured
// on the subscription (sent in a header), a bounded timestamp window, and a per-reference rate
// limit. Everything here is pure so the policy is unit-tested without a provider.

import * as crypto from "crypto";

export const WEBHOOK_SECRET_HEADER = "x-momo-webhook-secret";
export const WEBHOOK_ALTERNATE_SECRET_HEADER = "x-callback-secret";
export const WEBHOOK_MAX_SKEW_MS = 5 * 60_000;
export const WEBHOOK_WINDOW_MS = 60_000;
export const WEBHOOK_MAX_PER_WINDOW = 10;

export interface WebhookRateRecord {
  windowStart?: number;
  count?: number;
}

export interface WebhookRateDecision {
  allowed: boolean;
  reason?: "throttled";
  next: WebhookRateRecord;
}

export type WebhookAuthFailure =
  | "not_configured"
  | "missing_secret"
  | "bad_secret"
  | "missing_timestamp"
  | "stale_timestamp";

export type WebhookAuthResult = { ok: true } | { ok: false; reason: WebhookAuthFailure };

/** `MOMO_WEBHOOK_SECRET` may hold a comma-separated rotation list. */
export function loadWebhookSecrets(env: Record<string, string | undefined>): string[] {
  return (env.MOMO_WEBHOOK_SECRET || "")
    .split(",")
    .map((value) => value.trim())
    .filter((value) => value.length >= 16);
}

export function timingSafeEquals(a: string, b: string): boolean {
  const left = Buffer.from(a, "utf8");
  const right = Buffer.from(b, "utf8");
  if (left.length !== right.length) return false;
  return crypto.timingSafeEqual(left, right);
}

function headerValue(headers: Record<string, unknown>, name: string): string {
  const raw = headers[name] ?? headers[name.toLowerCase()];
  if (Array.isArray(raw)) return typeof raw[0] === "string" ? raw[0] : "";
  return typeof raw === "string" ? raw : "";
}

/** Accept epoch milliseconds, epoch seconds or an ISO-8601 string. */
export function parseWebhookTimestamp(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value > 1e12 ? value : value * 1000;
  }
  if (typeof value === "string") {
    const numeric = Number(value);
    if (Number.isFinite(numeric) && value.trim() !== "") {
      return numeric > 1e12 ? numeric : numeric * 1000;
    }
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

export function authorizeWebhook(input: {
  headers: Record<string, unknown>;
  body: Record<string, unknown> | undefined;
  secrets: string[];
  now: number;
  maxSkewMs?: number;
}): WebhookAuthResult {
  if (input.secrets.length === 0) {
    // Fail closed: an endpoint with no configured secret cannot authenticate anything.
    return { ok: false, reason: "not_configured" };
  }

  const presented =
    headerValue(input.headers, WEBHOOK_SECRET_HEADER) ||
    headerValue(input.headers, WEBHOOK_ALTERNATE_SECRET_HEADER);
  if (!presented) return { ok: false, reason: "missing_secret" };
  if (!input.secrets.some((secret) => timingSafeEquals(secret, presented))) {
    return { ok: false, reason: "bad_secret" };
  }

  const body = input.body ?? {};
  const stamp = parseWebhookTimestamp(body.timestamp ?? body.createdAt ?? body.created_at);
  if (stamp === null) return { ok: false, reason: "missing_timestamp" };
  const skew = Math.abs(input.now - stamp);
  if (skew > (input.maxSkewMs ?? WEBHOOK_MAX_SKEW_MS)) {
    return { ok: false, reason: "stale_timestamp" };
  }

  return { ok: true };
}

export function evaluateWebhookRate(record: WebhookRateRecord | undefined, now: number): WebhookRateDecision {
  const current = record ?? {};
  const inWindow = now - (current.windowStart ?? 0) < WEBHOOK_WINDOW_MS;
  const count = inWindow ? current.count ?? 0 : 0;
  if (count >= WEBHOOK_MAX_PER_WINDOW) {
    return { allowed: false, reason: "throttled", next: current };
  }
  return {
    allowed: true,
    next: {
      windowStart: inWindow ? current.windowStart ?? now : now,
      count: count + 1,
    },
  };
}
