// R10-P2-02 / R10-P2-07: reports that reach an operator, and safety metrics that can be read.
//
// `reportUser` wrote to a Room table and nothing else: no Firestore collection, no rule, no admin
// queue. A report filed on a phone was invisible to every operator on every other device, and a
// reinstall erased it. The DSAR export even read a `reports` collection that nothing wrote.
//
// This module owns the shape, the priority, the triage state machine and the metrics. The callables
// in `index.ts` own identity and storage.

export const REPORT_CATEGORIES = [
  "SAFETY",
  "FRAUD",
  "HARASSMENT",
  "NO_SHOW",
  "PAYMENT_ABUSE",
  "OTHER",
] as const;

export type ReportCategory = (typeof REPORT_CATEGORIES)[number];

export const REPORT_STATUS = {
  open: "OPEN",
  triaging: "TRIAGING",
  actioned: "ACTIONED",
  dismissed: "DISMISSED",
} as const;

export type ReportStatus = (typeof REPORT_STATUS)[keyof typeof REPORT_STATUS];

/** How long an operator has to first contact the reporter, by category. */
export const FIRST_RESPONSE_HOURS: Record<ReportCategory, number> = {
  SAFETY: 1,
  FRAUD: 4,
  HARASSMENT: 4,
  NO_SHOW: 24,
  PAYMENT_ABUSE: 24,
  OTHER: 48,
};

export const MAX_DETAILS_LENGTH = 2000;
/** A reporter cannot open an unlimited number of reports against the same person. */
export const MAX_OPEN_REPORTS_PER_SUBJECT = 5;

export interface ReportRecord {
  id: string;
  reportedBy: string;
  subjectUid: string;
  subjectName: string;
  jobId: string;
  category: ReportCategory;
  details: string;
  priority: "P0" | "P1" | "P2";
  status: ReportStatus;
  createdAt: number;
  updatedAt: number;
  slaDueAt: number;
  triagedBy: string;
  triagedAt: number;
  outcome: string;
  adminNotes: string;
}

export interface ReportDraftInput {
  subjectUid?: unknown;
  subjectName?: unknown;
  jobId?: unknown;
  category?: unknown;
  details?: unknown;
}

/**
 * Validate and normalise a report a client is asking to file.
 *
 * Fail closed: an unknown category, a missing subject, a self-report or an empty explanation is
 * refused rather than stored as a vague row nobody can action.
 */
export function buildReportRecord(
  input: ReportDraftInput,
  context: { id: string; reporterUid: string; now: number; priorOpenReportsAgainstSubject: number }
): ReportRecord {
  const subjectUid = typeof input.subjectUid === "string" ? input.subjectUid.trim() : "";
  if (!subjectUid) throw new Error("a report needs the account it is about");
  if (!context.reporterUid) throw new Error("a report needs a signed-in reporter");
  if (subjectUid === context.reporterUid) throw new Error("an account cannot report itself");

  const category = String(input.category ?? "").trim().toUpperCase() as ReportCategory;
  if (!REPORT_CATEGORIES.includes(category)) {
    throw new Error(`category must be one of ${REPORT_CATEGORIES.join(", ")}`);
  }

  const details = typeof input.details === "string" ? input.details.trim().slice(0, MAX_DETAILS_LENGTH) : "";
  if (details.length < 10) {
    throw new Error("describe what happened, in at least a sentence, so an operator can act on it");
  }

  if (context.priorOpenReportsAgainstSubject >= MAX_OPEN_REPORTS_PER_SUBJECT) {
    throw new Error("there are already several open reports about this account; support will follow up");
  }

  const jobId = typeof input.jobId === "string" ? input.jobId.trim() : "";
  const subjectName = typeof input.subjectName === "string" ? input.subjectName.trim().slice(0, 120) : "";
  const priority = reportPriority(category, context.priorOpenReportsAgainstSubject);

  return {
    id: context.id,
    reportedBy: context.reporterUid,
    subjectUid,
    subjectName,
    jobId,
    category,
    details,
    priority,
    status: REPORT_STATUS.open,
    createdAt: context.now,
    updatedAt: context.now,
    slaDueAt: context.now + FIRST_RESPONSE_HOURS[category] * 60 * 60 * 1000,
    triagedBy: "",
    triagedAt: 0,
    outcome: "",
    adminNotes: "",
  };
}

/**
 * Priority is a function of what the report is about and how often this subject has been reported.
 * A safety report is always P0; a repeat subject escalates one step even for a low-severity category.
 */
export function reportPriority(
  category: ReportCategory,
  priorOpenReportsAgainstSubject: number
): "P0" | "P1" | "P2" {
  if (category === "SAFETY") return "P0";
  if (category === "FRAUD" || category === "HARASSMENT") {
    return priorOpenReportsAgainstSubject >= 1 ? "P0" : "P1";
  }
  return priorOpenReportsAgainstSubject >= 2 ? "P1" : "P2";
}

export interface TriageCommand {
  report: ReportRecord;
  status: ReportStatus;
  outcome: string;
  notes?: string;
  adminUid: string;
  now: number;
}

/**
 * The triage state machine.
 *
 * Every transition names the operator and the moment, and a closed report cannot be re-opened by the
 * same call (a new report is filed instead) — an audit trail that can be rewritten is not one.
 */
export function decideTriage(command: TriageCommand): ReportRecord {
  const { report, status, outcome, adminUid, now } = command;
  if (!adminUid) throw new Error("a triage decision must be attributed to an operator");
  if (report.status === REPORT_STATUS.actioned || report.status === REPORT_STATUS.dismissed) {
    throw new Error("this report is already closed; file a new report rather than reopening it");
  }
  if (status === REPORT_STATUS.open) throw new Error("triage must move the report forward");
  if (status === REPORT_STATUS.actioned || status === REPORT_STATUS.dismissed) {
    if (!outcome || outcome.trim().length < 5) {
      throw new Error("closing a report needs an outcome an auditor can read");
    }
  }
  return {
    ...report,
    status,
    outcome: outcome.trim().slice(0, 500),
    adminNotes: (command.notes ?? report.adminNotes ?? "").trim().slice(0, 1000),
    triagedBy: adminUid,
    triagedAt: now,
    updatedAt: now,
  };
}

/**
 * The operator-facing sentence for a recorded triage decision.
 *
 * Composed on the server, from the stored record, so the console cannot show a success the database
 * did not take — and so the SLA verdict is the server's, not the device's clock.
 */
export function triageMessage(report: ReportRecord): string {
  const verdict =
    report.status === REPORT_STATUS.actioned || report.status === REPORT_STATUS.dismissed
      ? "closed"
      : "moved to triage";
  const metSla = report.triagedAt > 0 && report.triagedAt <= report.slaDueAt;
  return `Report ${report.id} ${verdict} (${report.status}, ${report.priority}); first response ${
    metSla ? "within" : "past"
  } the ${FIRST_RESPONSE_HOURS[report.category]}-hour target.`;
}

export interface SafetyIncidentSummary {
  id: string;
  status: string;
  raisedAt: number;
  acknowledgedAt?: number;
  dispatchStatus?: string;
}

export interface SafetyReportSummary {
  id: string;
  category: string;
  priority: string;
  status: string;
  createdAt: number;
  slaDueAt: number;
  triagedAt?: number;
}

export interface SafetyMetrics {
  generatedAt: number;
  windowDays: number;
  sosRaised: number;
  sosAcknowledged: number;
  /** Median seconds from raise to operator acknowledgement, over acknowledged incidents only. */
  medianAckSeconds: number | null;
  /** Incidents never acknowledged, and how old the oldest is. */
  sosUnacknowledged: number;
  oldestUnacknowledgedSeconds: number | null;
  /** Incidents whose SMS dispatch never succeeded (FAILED or NO_RECIPIENTS) — a duty-of-care signal. */
  sosDispatchFailed: number;
  reportsOpen: number;
  reportsBreachingSla: number;
  reportsClosed: number;
  medianReportFirstResponseSeconds: number | null;
  byCategory: Record<string, number>;
}

function median(values: number[]): number | null {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0 ? Math.round((sorted[middle - 1] + sorted[middle]) / 2) : sorted[middle];
}

/**
 * The trust & safety numbers the reviewer asked for: how fast operators acknowledge an emergency,
 * how many emergencies were never answered, and how many reports are past their first-response SLA.
 *
 * Deliberately computes from the records the platform already keeps, so the metric cannot drift from
 * the data. A metric nobody can compute is a slogan.
 */
export function safetyMetrics(input: {
  now: number;
  windowDays?: number;
  incidents: SafetyIncidentSummary[];
  reports: SafetyReportSummary[];
}): SafetyMetrics {
  const windowDays = input.windowDays ?? 30;
  const windowMs = windowDays * 24 * 60 * 60 * 1000;
  const from = input.now - windowMs;
  const incidents = input.incidents.filter((incident) => incident.raisedAt >= from);
  const reports = input.reports.filter((report) => report.createdAt >= from);

  const ackSeconds = incidents
    .filter((incident) => (incident.acknowledgedAt ?? 0) > incident.raisedAt)
    .map((incident) => Math.round(((incident.acknowledgedAt as number) - incident.raisedAt) / 1000));
  const unacknowledged = incidents.filter((incident) => !((incident.acknowledgedAt ?? 0) > incident.raisedAt));

  const openReports = reports.filter(
    (report) => report.status === "OPEN" || report.status === "TRIAGING"
  );
  const closed = reports.filter(
    (report) => report.status === "ACTIONED" || report.status === "DISMISSED"
  );
  const firstResponse = closed
    .filter((report) => (report.triagedAt ?? 0) > report.createdAt)
    .map((report) => Math.round(((report.triagedAt as number) - report.createdAt) / 1000));

  const byCategory: Record<string, number> = {};
  for (const report of reports) byCategory[report.category] = (byCategory[report.category] ?? 0) + 1;

  return {
    generatedAt: input.now,
    windowDays,
    sosRaised: incidents.length,
    sosAcknowledged: ackSeconds.length,
    medianAckSeconds: median(ackSeconds),
    sosUnacknowledged: unacknowledged.length,
    oldestUnacknowledgedSeconds:
      unacknowledged.length === 0
        ? null
        : Math.max(...unacknowledged.map((incident) => Math.round((input.now - incident.raisedAt) / 1000))),
    sosDispatchFailed: incidents.filter(
      (incident) => incident.dispatchStatus === "FAILED" || incident.dispatchStatus === "NO_RECIPIENTS"
    ).length,
    reportsOpen: openReports.length,
    reportsBreachingSla: openReports.filter((report) => input.now > report.slaDueAt).length,
    reportsClosed: closed.length,
    medianReportFirstResponseSeconds: median(firstResponse),
    byCategory,
  };
}

/** One line for the operator log and the admin console: counts only, never a person or a phone. */
export function metricsLine(metrics: SafetyMetrics): string {
  return (
    `safety: sos=${metrics.sosRaised} unacked=${metrics.sosUnacknowledged} ` +
    `medianAck=${metrics.medianAckSeconds ?? "n/a"}s dispatchFailed=${metrics.sosDispatchFailed} ` +
    `reportsOpen=${metrics.reportsOpen} breachingSla=${metrics.reportsBreachingSla}`
  );
}
