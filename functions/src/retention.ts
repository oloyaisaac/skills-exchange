// R09-P2-04: retention, defined per category and enforced by a sweep.
//
// The review found no retention job, no TTL policy and no purge function anywhere in `functions/src`:
// KYC documents, chats, SOS incidents, notifications and consent records were kept for ever because
// nobody had decided how long they were needed. "We keep everything" is not a neutral default under
// the DPPA — it is a failure of storage limitation.
//
// The policy lives here as data, so the schedule, the report and the privacy documents can never
// disagree about what happens to a category. Legal holds (financial and KYC records) are listed
// separately and are never swept: retention obligations and erasure duties point in opposite
// directions, and the honest answer is to name which one wins for which category.

export type RetentionAction = "PURGE" | "ANONYMISE";

export interface RetentionCategory {
  id: string;
  /** Firestore collection name. */
  collection: string;
  /** True for a `collectionGroup` sweep (e.g. chat messages under every job). */
  collectionGroup?: boolean;
  /** Timestamp field the cutoff is applied to (a number of epoch millis or a Timestamp). */
  timestampField: string;
  retentionDays: number;
  action: RetentionAction;
  /** Fields blanked when `action` is ANONYMISE. */
  blankFields?: string[];
  /** Why this window — the honest reason, not a legal slogan. */
  basis: string;
}

export interface LegalHold {
  id: string;
  collection: string;
  retentionDays: number;
  reason: string;
}

/** How long each sweeper category is kept, and what happens when the window closes. */
export const RETENTION_POLICY: readonly RetentionCategory[] = [
  {
    id: "notifications",
    collection: "notifications",
    timestampField: "createdAt",
    retentionDays: 90,
    action: "PURGE",
    basis: "Transactional notices are useful for a season, not for ever; the underlying job keeps the record.",
  },
  {
    id: "chat_messages",
    collection: "messages",
    collectionGroup: true,
    timestampField: "timestamp",
    retentionDays: 365,
    action: "ANONYMISE",
    blankFields: ["body", "text", "senderName", "attachmentUrl"],
    basis: "Chat is dispute evidence for a year; after that the thread is kept for context but the words are removed.",
  },
  {
    id: "sos_incidents",
    collection: "sosIncidents",
    timestampField: "createdAt",
    retentionDays: 365,
    action: "ANONYMISE",
    blankFields: ["latitude", "longitude", "address", "landmark", "notes"],
    basis: "Safety incidents stay countable; precise location and free text are minimised once the year is out.",
  },
  {
    id: "rate_limits",
    collection: "otp_rate_limits",
    timestampField: "updatedAt",
    retentionDays: 2,
    action: "PURGE",
    basis: "Abuse counters have no value after the window they protect.",
  },
  {
    id: "webhook_rate_limits",
    collection: "webhook_rate_limits",
    timestampField: "updatedAt",
    retentionDays: 2,
    action: "PURGE",
    basis: "Same as the OTP counters: the limit is per minute, the record is per day.",
  },
  {
    id: "reconciliation_reports",
    collection: "reconciliation_reports",
    timestampField: "generatedAt",
    retentionDays: 400,
    action: "PURGE",
    basis: "A sweep report outlives its usefulness after a year; the payouts it describes are held separately.",
  },
  {
    id: "retention_reports",
    collection: "retention_reports",
    timestampField: "generatedAt",
    retentionDays: 400,
    action: "PURGE",
    basis: "Evidence of enforcement, kept a year and a month.",
  },
  {
    id: "account_deletions",
    collection: "account_deletions",
    timestampField: "at",
    retentionDays: 1095,
    action: "PURGE",
    basis: "Proof of erasure for three years, which is long enough to answer a regulator's question.",
  },
] as const;

/** Categories that are deliberately NOT swept, and why. Sweeping these would break the law. */
export const LEGAL_HOLDS: readonly LegalHold[] = [
  {
    id: "payments",
    collection: "momo_transactions, payouts, momo_payouts, refunds, platform_fees, wallet_entries",
    retentionDays: 2555,
    reason: "Financial records are kept seven years for AML/tax; they are pseudonymised, not deleted.",
  },
  {
    id: "kyc",
    collection: "worker_kyc, guarantor_submissions, guarantors, users/*/verification",
    retentionDays: 1825,
    reason: "Identity verification evidence is held five years after the relationship ends (AML), then reviewed.",
  },
  {
    id: "consent_proof",
    collection: "users/*/consents, users/*/guarantor_consents",
    retentionDays: 2190,
    reason: "Consent must be demonstrable for as long as processing relies on it.",
  },
] as const;

export function categoryById(id: string): RetentionCategory | undefined {
  return RETENTION_POLICY.find((category) => category.id === id);
}

const DAY_MS = 24 * 60 * 60 * 1000;

export interface RetentionDecision {
  categoryId: string;
  action: RetentionAction | "RETAIN";
  ageDays: number;
  /** Epoch millis at which the item becomes due, or null when the timestamp is unreadable. */
  dueAt: number | null;
}

/**
 * Decide one document's fate.
 *
 * An unreadable or missing timestamp is retained, never purged: a sweep must not delete something it
 * cannot date, and the fact that the item could not be dated is itself reportable.
 */
export function decideRetention(
  categoryId: string,
  timestampValue: unknown,
  now: number
): RetentionDecision {
  const category = categoryById(categoryId);
  const at = toMillis(timestampValue);
  if (!category) {
    return { categoryId, action: "RETAIN", ageDays: 0, dueAt: null };
  }
  if (at === null) {
    return { categoryId, action: "RETAIN", ageDays: 0, dueAt: null };
  }
  const ageMs = Math.max(0, now - at);
  const ageDays = Math.floor(ageMs / DAY_MS);
  const dueAt = at + category.retentionDays * DAY_MS;
  if (ageDays < category.retentionDays) {
    return { categoryId, action: "RETAIN", ageDays, dueAt };
  }
  return { categoryId, action: category.action, ageDays, dueAt };
}

/** Firestore Timestamps, epoch millis and ISO strings all arrive here; anything else is undated. */
export function toMillis(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value) && value > 0) return value;
  if (typeof value === "string") {
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }
  const candidate = value as { toMillis?: () => number; seconds?: number } | null;
  if (candidate && typeof candidate.toMillis === "function") {
    try {
      const millis = candidate.toMillis();
      return Number.isFinite(millis) && millis > 0 ? millis : null;
    } catch {
      return null;
    }
  }
  if (candidate && typeof candidate.seconds === "number") {
    const millis = candidate.seconds * 1000;
    return Number.isFinite(millis) && millis > 0 ? millis : null;
  }
  return null;
}

export interface SweepDoc {
  id: string;
  data: Record<string, unknown>;
}

export interface SweepPlan {
  categoryId: string;
  action: RetentionAction;
  purgeIds: string[];
  anonymiseIds: string[];
  retained: number;
  undatedIds: string[];
}

/**
 * Plan one category's sweep over the documents that were read.
 *
 * `maxActions` bounds a single run so a backlog cannot turn into an unbounded write burst; whatever
 * is left over is planned again on the next run.
 */
export function planRetentionSweep(input: {
  categoryId: string;
  now: number;
  docs: SweepDoc[];
  maxActions?: number;
}): SweepPlan {
  const category = categoryById(input.categoryId);
  if (!category) throw new Error(`unknown retention category '${input.categoryId}'`);
  const maxActions = Math.max(1, Math.floor(input.maxActions ?? 200));

  const plan: SweepPlan = {
    categoryId: category.id,
    action: category.action,
    purgeIds: [],
    anonymiseIds: [],
    retained: 0,
    undatedIds: [],
  };

  for (const doc of input.docs) {
    const decision = decideRetention(category.id, doc.data[category.timestampField], input.now);
    if (decision.dueAt === null) {
      plan.undatedIds.push(doc.id);
      continue;
    }
    if (decision.action === "RETAIN") {
      plan.retained += 1;
      continue;
    }
    if (plan.purgeIds.length + plan.anonymiseIds.length >= maxActions) {
      plan.retained += 1;
      continue;
    }
    if (decision.action === "PURGE") plan.purgeIds.push(doc.id);
    else plan.anonymiseIds.push(doc.id);
  }

  return plan;
}

/** The fields an anonymise step should blank, as a Firestore patch. */
export function anonymisePatch(categoryId: string): Record<string, unknown> {
  const category = categoryById(categoryId);
  if (!category || category.action !== "ANONYMISE") return {};
  const patch: Record<string, unknown> = { redactedBy: "retention_policy", redactedAt: Date.now() };
  for (const field of category.blankFields ?? []) patch[field] = "";
  return patch;
}

export interface RetentionReport {
  generatedAt: number;
  categories: Array<{
    id: string;
    action: RetentionAction;
    retentionDays: number;
    purged: number;
    anonymised: number;
    retained: number;
    undated: number;
    error?: string;
  }>;
  totalPurged: number;
  totalAnonymised: number;
}

export function summariseRetention(
  generatedAt: number,
  categories: RetentionReport["categories"]
): RetentionReport {
  return {
    generatedAt,
    categories,
    totalPurged: categories.reduce((total, entry) => total + entry.purged, 0),
    totalAnonymised: categories.reduce((total, entry) => total + entry.anonymised, 0),
  };
}

/** One line for the operator log: counts only, never a document id or a personal value. */
export function retentionReportLine(report: RetentionReport): string {
  return (
    `retention: purged=${report.totalPurged} anonymised=${report.totalAnonymised} ` +
    `categories=${report.categories.length}`
  );
}
