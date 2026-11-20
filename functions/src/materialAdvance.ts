// R08-P2-03: the material advance, made authorization-first and arithmetically consistent.
//
// Before: the callable wrote a PENDING `materialAdvances/{jobId}` document *before* any ownership
// check (so a stranger could burn the job's only advance slot), and it then asked the payout
// validator for `amountUgx = 50% of a client-supplied materialsSubtotal`, while that validator
// required the amount to equal the job's labour total. Every honest advance failed; only a manipulated
// subtotal could pass.
//
// Now the authorization and the amount are decided here, from the job document, before anything is
// written. The payout core receives an explicit `expectedAmountUgx`, so it validates against the same
// number this module computed instead of against the release amount.

export interface AdvanceJobView {
  postedByUid?: unknown;
  assignedProviderId?: unknown;
  assignedProviderUid?: unknown;
  assignedWorkerId?: unknown;
  escrowStatus?: unknown;
  materialsSubtotal?: unknown;
  materialAdvanceStatus?: unknown;
  materialAdvanceAmount?: unknown;
}

export const ADVANCE_FRACTION = 0.5;

export type AdvanceDecision =
  | { kind: "ALLOW"; amountUgx: number; workerUid: string; materialsSubtotal: number }
  | {
      kind: "REJECT";
      code:
        | "not-found"
        | "permission-denied"
        | "failed-precondition"
        | "invalid-argument"
        | "already-exists";
      message: string;
    };

/** Whole-shilling half of the materials subtotal, never above it. */
export function advanceAmountFor(materialsSubtotal: unknown): number | null {
  // A string that merely looks numeric ("40000") is not a materials budget: only a stored number is.
  if (typeof materialsSubtotal !== "number") return null;
  const subtotal = materialsSubtotal;
  if (!Number.isFinite(subtotal) || subtotal <= 0) return null;
  if (!Number.isInteger(subtotal)) return null;
  return Math.max(1, Math.round(subtotal * ADVANCE_FRACTION));
}

/**
 * Decide whether this caller may request an advance, and for how much.
 *
 * The caller supplies only the job id: the materials subtotal, the worker and the escrow state all
 * come from the job document. A stranger is refused before anything is written.
 */
export function decideMaterialAdvance(
  job: AdvanceJobView | null | undefined,
  callerUid: string,
  existingAdvanceStatus?: string | null
): AdvanceDecision {
  if (!job) {
    return { kind: "REJECT", code: "not-found", message: "Job not found." };
  }
  if (!callerUid || job.postedByUid !== callerUid) {
    return { kind: "REJECT", code: "permission-denied", message: "Only the resident who posted the job can request a material advance." };
  }
  const escrow = String(job.escrowStatus || "").trim().toUpperCase();
  if (escrow !== "FUNDS_LOCKED_IN_ESCROW") {
    return {
      kind: "REJECT",
      code: "failed-precondition",
      message: `The advance needs funded escrow (status: ${escrow || "none"}).`,
    };
  }
  const workerUid = String(job.assignedProviderUid || job.assignedProviderId || job.assignedWorkerId || "").trim();
  if (!workerUid) {
    return { kind: "REJECT", code: "failed-precondition", message: "No worker is assigned to this job yet." };
  }
  const status = String(existingAdvanceStatus || job.materialAdvanceStatus || "").trim().toUpperCase();
  if (status === "PENDING" || status === "DISBURSED") {
    return {
      kind: "REJECT",
      code: "already-exists",
      message: status === "DISBURSED"
        ? "This material advance was already disbursed and cannot be re-run."
        : "A material advance for this job is already pending.",
    };
  }
  const subtotal = Number(job.materialsSubtotal);
  const amount = advanceAmountFor(subtotal);
  if (amount === null) {
    return {
      kind: "REJECT",
      code: "invalid-argument",
      message: "This job has no materials budget to advance against.",
    };
  }
  return { kind: "ALLOW", amountUgx: amount, workerUid, materialsSubtotal: subtotal };
}
