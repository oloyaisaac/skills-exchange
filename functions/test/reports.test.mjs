/**
 * R10-P2-02 / R10-P2-07: reports that reach an operator, and safety metrics with a definition.
 *
 * A report used to exist only in the reporter's own Room table — no collection, no rule, no queue —
 * and there were no trust & safety numbers at all. These tests pin the intake validation, the
 * priority ladder, the triage state machine (attributed, append-only) and the metrics arithmetic.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  REPORT_CATEGORIES,
  REPORT_STATUS,
  FIRST_RESPONSE_HOURS,
  MAX_DETAILS_LENGTH,
  MAX_OPEN_REPORTS_PER_SUBJECT,
  buildReportRecord,
  reportPriority,
  decideTriage,
  safetyMetrics,
  triageMessage,
  metricsLine,
} from "../lib/reports.js";

const NOW = 1_800_000_000_000;
const HOUR = 60 * 60 * 1000;
const MINUTE = 60 * 1000;

function build(overrides = {}, context = {}) {
  return buildReportRecord(
    {
      subjectUid: "worker_2",
      subjectName: "Provider Two",
      jobId: "job_1",
      category: "SAFETY",
      details: "They threatened me at the gate and would not leave.",
      ...overrides,
    },
    { id: "report_1", reporterUid: "resident_1", now: NOW, priorOpenReportsAgainstSubject: 0, ...context }
  );
}

test("reports: a valid report is stored with a priority and an SLA deadline", () => {
  const report = build();
  assert.equal(report.status, REPORT_STATUS.open);
  assert.equal(report.priority, "P0");
  assert.equal(report.slaDueAt, NOW + FIRST_RESPONSE_HOURS.SAFETY * HOUR);
  assert.equal(report.reportedBy, "resident_1");
  assert.equal(report.triagedBy, "");
  assert.equal(report.triagedAt, 0);
});

test("reports: unknown categories, self-reports and vague reports are refused", () => {
  assert.throws(() => build({ category: "I_DONT_LIKE_THEM" }), /category must be one of/);
  assert.throws(() => build({ category: "" }), /category/);
  assert.throws(() => build({ subjectUid: "resident_1" }), /cannot report itself/);
  assert.throws(() => build({ subjectUid: "" }), /account it is about/);
  assert.throws(() => build({ details: "bad" }), /describe what happened/);
  assert.throws(() => build({ details: 42 }), /describe what happened/);
  assert.throws(
    () => build({}, { reporterUid: "" }),
    /signed-in reporter/
  );
  assert.equal(REPORT_CATEGORIES.length, 6);
});

test("reports: details are capped, and a flood against one subject is stopped", () => {
  const long = build({ details: "x".repeat(MAX_DETAILS_LENGTH + 500) });
  assert.equal(long.details.length, MAX_DETAILS_LENGTH);
  assert.throws(
    () => build({}, { priorOpenReportsAgainstSubject: MAX_OPEN_REPORTS_PER_SUBJECT }),
    /already several open reports/
  );
});

test("reports: priority escalates with category and with repeat reports", () => {
  assert.equal(reportPriority("SAFETY", 0), "P0");
  assert.equal(reportPriority("FRAUD", 0), "P1");
  assert.equal(reportPriority("FRAUD", 1), "P0");
  assert.equal(reportPriority("HARASSMENT", 1), "P0");
  assert.equal(reportPriority("NO_SHOW", 0), "P2");
  assert.equal(reportPriority("NO_SHOW", 2), "P1");
  assert.equal(reportPriority("OTHER", 0), "P2");
});

test("reports: triage is attributed, forward-only and needs an outcome", () => {
  const report = build();
  const triaged = decideTriage({
    report,
    status: REPORT_STATUS.triaging,
    outcome: "reviewing evidence",
    adminUid: "admin_1",
    now: NOW + HOUR,
  });
  assert.equal(triaged.status, REPORT_STATUS.triaging);
  assert.equal(triaged.triagedBy, "admin_1");
  assert.equal(triaged.triagedAt, NOW + HOUR);

  const closed = decideTriage({
    report: triaged,
    status: REPORT_STATUS.actioned,
    outcome: "worker suspended pending investigation",
    notes: "two prior reports",
    adminUid: "admin_1",
    now: NOW + 2 * HOUR,
  });
  assert.equal(closed.status, REPORT_STATUS.actioned);
  assert.equal(closed.outcome, "worker suspended pending investigation");
  assert.equal(closed.adminNotes, "two prior reports");

  assert.throws(
    () => decideTriage({ report: closed, status: REPORT_STATUS.triaging, outcome: "reopen", adminUid: "admin_1", now: NOW }),
    /already closed/
  );
  assert.throws(
    () => decideTriage({ report, status: REPORT_STATUS.actioned, outcome: "no", adminUid: "admin_1", now: NOW }),
    /outcome an auditor can read/
  );
  assert.throws(
    () => decideTriage({ report, status: REPORT_STATUS.open, outcome: "x", adminUid: "admin_1", now: NOW }),
    /move the report forward/
  );
  assert.throws(
    () => decideTriage({ report, status: REPORT_STATUS.triaging, outcome: "x", adminUid: "", now: NOW }),
    /attributed to an operator/
  );
});

test("metrics: acknowledgement latency, unacknowledged incidents and dispatch failures", () => {
  const metrics = safetyMetrics({
    now: NOW,
    windowDays: 30,
    incidents: [
      { id: "a", status: "ACKNOWLEDGED", raisedAt: NOW - 10 * HOUR, acknowledgedAt: NOW - 10 * HOUR + 120_000, dispatchStatus: "SENT" },
      { id: "b", status: "ACKNOWLEDGED", raisedAt: NOW - 5 * HOUR, acknowledgedAt: NOW - 5 * HOUR + 300_000, dispatchStatus: "SENT" },
      { id: "c", status: "ACTIVE", raisedAt: NOW - 2 * HOUR, dispatchStatus: "NO_RECIPIENTS" },
      { id: "d", status: "ACTIVE", raisedAt: NOW - 90 * 24 * HOUR, dispatchStatus: "SENT" }, // outside the window
    ],
    reports: [],
  });
  assert.equal(metrics.sosRaised, 3, "only the window is counted");
  assert.equal(metrics.sosAcknowledged, 2);
  assert.equal(metrics.medianAckSeconds, 210, "median of 120s and 300s");
  assert.equal(metrics.sosUnacknowledged, 1);
  assert.equal(metrics.oldestUnacknowledgedSeconds, 2 * 3600);
  assert.equal(metrics.sosDispatchFailed, 1);
});

test("metrics: report backlog, SLA breaches and first response", () => {
  const metrics = safetyMetrics({
    now: NOW,
    windowDays: 30,
    incidents: [],
    reports: [
      { id: "r1", category: "SAFETY", priority: "P0", status: "OPEN", createdAt: NOW - 3 * HOUR, slaDueAt: NOW - 2 * HOUR },
      { id: "r2", category: "FRAUD", priority: "P1", status: "TRIAGING", createdAt: NOW - 1 * HOUR, slaDueAt: NOW + 3 * HOUR },
      { id: "r3", category: "SAFETY", priority: "P0", status: "ACTIONED", createdAt: NOW - 20 * HOUR, slaDueAt: NOW - 19 * HOUR, triagedAt: NOW - 19 * HOUR },
      { id: "r4", category: "OTHER", priority: "P2", status: "DISMISSED", createdAt: NOW - 10 * HOUR, slaDueAt: NOW - 8 * HOUR, triagedAt: NOW - 9 * HOUR },
    ],
  });
  assert.equal(metrics.reportsOpen, 2);
  assert.equal(metrics.reportsBreachingSla, 1);
  assert.equal(metrics.reportsClosed, 2);
  assert.equal(metrics.medianReportFirstResponseSeconds, 3600, "median of 3600s and 3600s");
  assert.deepEqual(metrics.byCategory, { SAFETY: 2, FRAUD: 1, OTHER: 1 });

  const empty = safetyMetrics({ now: NOW, incidents: [], reports: [] });
  assert.equal(empty.medianAckSeconds, null);
  assert.equal(empty.medianReportFirstResponseSeconds, null);
  assert.equal(empty.oldestUnacknowledgedSeconds, null);
});

test("metrics: the operator line carries counts only", () => {
  const metrics = safetyMetrics({ now: NOW, incidents: [{ id: "a", status: "ACTIVE", raisedAt: NOW }], reports: [] });
  const line = metricsLine(metrics);
  assert.match(line, /sos=1/);
  assert.match(line, /unacked=1/);
  assert.doesNotMatch(line, /admin_1|resident_1|worker_2/);
});

test("triage: the sentence the console shows is the server's, and it names the SLA verdict", () => {
  const base = {
    id: "rep_1",
    reportedBy: "resident_1",
    subjectUid: "worker_2",
    subjectName: "Worker",
    jobId: "job_1",
    category: "SAFETY",
    details: "Threatened me at the gate after the job.",
    priority: "P0",
    status: REPORT_STATUS.open,
    createdAt: NOW - 30 * MINUTE,
    updatedAt: NOW - 30 * MINUTE,
    slaDueAt: NOW + 30 * MINUTE,
    triagedBy: "",
    triagedAt: 0,
    outcome: "",
    adminNotes: "",
  };

  // Within the one-hour SAFETY target.
  const within = decideTriage({
    report: base,
    status: REPORT_STATUS.triaging,
    outcome: "called the reporter",
    adminUid: "admin_1",
    now: NOW,
  });
  assert.match(triageMessage(within), /moved to triage/);
  assert.match(triageMessage(within), /within the 1-hour target/);
  // The operator's identity and the reporter's words never enter the operator-facing sentence.
  assert.doesNotMatch(triageMessage(within), /admin_1|resident_1|Threatened/);

  // Past it — the sentence must not claim the SLA was met.
  const late = decideTriage({
    report: { ...base, createdAt: NOW - 3 * HOUR, slaDueAt: NOW - 2 * HOUR },
    status: REPORT_STATUS.actioned,
    outcome: "reporter supported; account restricted",
    adminUid: "admin_1",
    now: NOW,
  });
  assert.match(triageMessage(late), /closed/);
  assert.match(triageMessage(late), /past the 1-hour target/);
});
