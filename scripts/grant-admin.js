#!/usr/bin/env node
/**
 * REM-023: the only supported way to provision or revoke the `admin` custom claim.
 *
 * Usage (run `cd functions && npm run build` first so lib/admin.js exists):
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/service-account.json \
 *   ADMIN_ACTOR=ops@example.com \
 *   node scripts/grant-admin.js --email operator@example.com
 *   node scripts/grant-admin.js --uid <uid> --revoke
 *
 * The script preserves any other custom claims, revokes refresh tokens so the new claim takes
 * effect, and writes an immutable `admin_audit` record naming the actor.
 */
function load(name, fallback) {
  try {
    return require(name);
  } catch {
    return require(fallback);
  }
}

const admin = load("firebase-admin", "../functions/node_modules/firebase-admin");
let buildAdminAuditRecord;
try {
  ({ buildAdminAuditRecord } = require("../functions/lib/admin.js"));
} catch {
  console.error("Build the functions first: cd functions && npm run build");
  process.exit(1);
}

function argument(name) {
  const index = process.argv.indexOf(name);
  return index > 0 ? process.argv[index + 1] : undefined;
}

async function main() {
  admin.initializeApp({ credential: admin.credential.applicationDefault() });

  const action = process.argv.includes("--revoke") ? "REVOKE" : "GRANT";
  let uid = argument("--uid");
  const email = argument("--email");
  if (!uid && email) {
    uid = (await admin.auth().getUserByEmail(email)).uid;
  }

  // buildAdminAuditRecord validates the uid, the action and that an actor is present.
  const record = buildAdminAuditRecord(
    { action, targetUid: uid, actor: process.env.ADMIN_ACTOR },
    Date.now()
  );

  const user = await admin.auth().getUser(record.targetUid);
  const claims = { ...(user.customClaims || {}), admin: record.claims.admin };
  await admin.auth().setCustomUserClaims(record.targetUid, claims);
  await admin.auth().revokeRefreshTokens(record.targetUid);

  await admin.firestore().collection("admin_audit").add(record);
  console.log(`${record.action} admin claim for ${record.targetUid}; audit record written.`);
}

main().catch((error) => {
  console.error(`Admin provisioning failed: ${error.message}`);
  process.exit(1);
});
