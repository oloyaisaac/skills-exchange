// REM-034 / REM-035: SOS dispatch policy.
//
// Raising an SOS used to write a Firestore document that only an administrator could read; no SMS
// or push left the platform, and the local Android service invented "SENT" results. This module
// decides who is alerted and builds the message; the trigger performs the delivery at most once.

import { normalisePhoneList } from "./sms";

export interface SosIncidentData {
  jobId?: unknown;
  postedByUid?: unknown;
  district?: unknown;
  latitude?: unknown;
  longitude?: unknown;
  raisedAt?: unknown;
  status?: unknown;
  dispatch?: {
    attemptedAt?: unknown;
    status?: unknown;
    delivered?: unknown;
    recipients?: unknown;
  };
}

export interface SosDispatchPlan {
  shouldDispatch: boolean;
  reason?: "not_active" | "no_owner" | "already_attempted";
}

export function planSosDispatch(data: SosIncidentData | undefined): SosDispatchPlan {
  if (!data || data.status !== "ACTIVE") return { shouldDispatch: false, reason: "not_active" };
  if (typeof data.postedByUid !== "string" || data.postedByUid.length === 0) {
    return { shouldDispatch: false, reason: "no_owner" };
  }
  if (data.dispatch && typeof data.dispatch.attemptedAt === "number") {
    return { shouldDispatch: false, reason: "already_attempted" };
  }
  return { shouldDispatch: true };
}

/** Platform security numbers come from SOS_DISPATCH_NUMBERS; the user's emergency contact is added. */
export function selectSosRecipients(
  configuredNumbers: unknown,
  emergencyContact: unknown,
  max = 10
): string[] {
  const configured = normalisePhoneList(configuredNumbers, max);
  const personal = typeof emergencyContact === "string"
    ? normalisePhoneList(emergencyContact, 2)
    : [];
  const seen = new Set<string>();
  const recipients: string[] = [];
  for (const phone of [...personal, ...configured]) {
    if (seen.has(phone)) continue;
    seen.add(phone);
    recipients.push(phone);
    if (recipients.length >= max) break;
  }
  return recipients;
}

function coordinate(value: unknown): string {
  return typeof value === "number" && Number.isFinite(value) ? value.toFixed(4) : "unknown";
}

export function buildSosMessage(data: SosIncidentData): string {
  const district = typeof data.district === "string" && data.district.trim() ? data.district.trim() : "unknown district";
  const job = typeof data.jobId === "string" && data.jobId.trim() ? data.jobId.trim() : "unknown job";
  return [
    "Skills Exchange SOS: a participant raised an emergency.",
    `Job ${job}, ${district}.`,
    `Location ${coordinate(data.latitude)}, ${coordinate(data.longitude)}.`,
    "Open the dispatch desk now.",
  ].join(" ");
}
