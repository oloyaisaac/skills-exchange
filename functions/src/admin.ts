// REM-023: admin-claim provisioning and server-verified operator gating.
//
// The Android client used to flip a local boolean (`isAdminUnlocked = true`) to reveal the admin
// console. That was never an authorization boundary — it still is not; every admin callable checks
// the `admin` custom claim — but it let the UI pretend an unprovisioned device was an operator
// device. The callable below lets the client ask the server whether the signed-in account actually
// holds the claim, and the provisioning script leaves an audit record.

export interface AdminAuditRecord {
  action: "GRANT" | "REVOKE";
  targetUid: string;
  actor: string;
  at: number;
  claims: { admin: boolean };
}

export interface AdminAuditInput {
  action?: unknown;
  income?: unknown;
  targetUid?: unknown;
  actor?: unknown;
}

export function hasAdminClaim(token: unknown): boolean {
  return typeof token === "object" && token !== null && (token as Record<string, unknown>).admin === true;
}

export function buildAdminAuditRecord(input: AdminAuditInput, now: number): AdminAuditRecord {
  const action = input.action;
  if (action !== "GRANT" && action !== "REVOKE") {
    throw new Error("action must be GRANT or REVOKE");
  }
  const targetUid = typeof input.targetUid === "string" ? input.targetUid.trim() : "";
  if (!/^[A-Za-z0-9_-]{6,128}$/.test(targetUid)) {
    throw new Error("targetUid is not a valid Firebase uid");
  }
  const actor = typeof input.actor === "string" ? input.actor.trim() : "";
  if (actor.length < 3) {
    throw new Error("actor is required for the audit trail");
  }
  return {
    action,
    targetUid,
    actor,
    at: now,
    claims: { admin: action === "GRANT" },
  };
}
