/** REM-023: admin claim and audit-record rules. */
import test from "node:test";
import assert from "node:assert/strict";

import { hasAdminClaim, buildAdminAuditRecord } from "../lib/admin.js";

test("only a boolean true admin claim grants access", () => {
  assert.equal(hasAdminClaim({ admin: true }), true);
  assert.equal(hasAdminClaim({ admin: "true" }), false);
  assert.equal(hasAdminClaim({ admin: false }), false);
  assert.equal(hasAdminClaim({}), false);
  assert.equal(hasAdminClaim(undefined), false);
});

test("audit records bind action, target, actor and resulting claim", () => {
  assert.deepEqual(
    buildAdminAuditRecord({ action: "GRANT", targetUid: "uid_123456", actor: "ops@example.com" }, 42),
    {
      action: "GRANT",
      targetUid: "uid_123456",
      actor: "ops@example.com",
      at: 42,
      claims: { admin: true },
    }
  );
  assert.equal(
    buildAdminAuditRecord({ action: "REVOKE", targetUid: "uid_123456", actor: "ops" }, 42).claims.admin,
    false
  );
});

test("malformed provisioning requests are rejected before any write", () => {
  assert.throws(() => buildAdminAuditRecord({ action: "MAKE_ADMIN", targetUid: "uid_123456", actor: "ops" }, 1));
  assert.throws(() => buildAdminAuditRecord({ action: "GRANT", targetUid: "x", actor: "ops" }, 1));
  assert.throws(() => buildAdminAuditRecord({ action: "GRANT", targetUid: "uid_123456", actor: "" }, 1));
});
