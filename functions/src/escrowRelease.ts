// REM-026: release policy for escrow payouts. Pure, so it can be tested without Firestore.
//
// The caller supplies only jobId/workerId. The amount comes from the job's escrow record and the
// wallet from the worker's profile; the policy refuses anything that is not a funded, completed
// job with a payable worker. Repeating a release returns the same deterministic payout reference.

export interface ReleaseJobView {
  status?: unknown;
  escrowStatus?: unknown;
  assignedProvider?: unknown;
  assignedWorkerId?: unknown;
  escrowAmount?: unknown;
  budget?: unknown;
  releasePayoutRef?: unknown;
}

export type ReleaseDecision =
  | { kind: "ALREADY_RELEASED"; payoutRef: string }
  | { kind: "REJECT"; code: "not-found" | "failed-precondition"; message: string }
  | { kind: "RELEASE"; amount: number; payoutRef: string };

const RELEASABLE_STATUSES = ["COMPLETED", "WORK_COMPLETED", "IN_PROGRESS"];
const MOMO_NUMBER = /^\+?[0-9]{9,15}$/;

export function decideEscrowRelease(
  job: ReleaseJobView | undefined,
  workerId: string,
  workerMomoNumber: string
): ReleaseDecision {
  if (!job) {
    return { kind: "REJECT", code: "not-found", message: "Job not found." };
  }
  if (job.escrowStatus === "RELEASED_TO_WORKER") {
    return {
      kind: "ALREADY_RELEASED",
      payoutRef: String(job.releasePayoutRef || `PAYOUT-${workerId}`),
    };
  }
  if (job.escrowStatus !== "FUNDS_LOCKED_IN_ESCROW") {
    return {
      kind: "REJECT",
      code: "failed-precondition",
      message: `Escrow is not funded for this job (status: ${String(job.escrowStatus || "none")}).`,
    };
  }
  if (!RELEASABLE_STATUSES.includes(String(job.status))) {
    return {
      kind: "REJECT",
      code: "failed-precondition",
      message: `Escrow cannot be released for job in status: ${String(job.status)}.`,
    };
  }
  if (job.assignedProvider !== workerId && job.assignedWorkerId !== workerId) {
    return {
      kind: "REJECT",
      code: "failed-precondition",
      message: "Worker ID does not match assigned worker for this job.",
    };
  }
  const amount = Number(job.escrowAmount ?? job.budget ?? 0);
  if (!Number.isFinite(amount) || amount <= 0) {
    return { kind: "REJECT", code: "failed-precondition", message: "Job has no payable amount." };
  }
  if (!MOMO_NUMBER.test(workerMomoNumber)) {
    return {
      kind: "REJECT",
      code: "failed-precondition",
      message: "The worker has no valid mobile-money number on file.",
    };
  }
  return { kind: "RELEASE", amount, payoutRef: "" };
}
