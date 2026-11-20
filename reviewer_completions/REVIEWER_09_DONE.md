# REVIEWER_09 — Privacy, KYC & Regulatory Compliance: DONE

I finished the work for this reviewer.

- Review: `review_results/prompt2/REVIEW_09.md` (verdict FAIL, 7 findings). Standard:
  `reviewers/PRIVACY_COMPLIANCE_REVIEWER.md`.
- Branch: `arena/e11de41e-skills-exchange`, based on `main` @ `8651122`.
- Pull request: #24.

## Findings

| ID | Severity | Outcome |
|---|---|---|
| R09-P2-01 | CRITICAL | **Fixed.** Erasure now also purges the account's messages in jobs it worked on; each run writes an `account_deletions` record with counts, retained categories and a notice; `ERASURE_STEPS` is the published order; Auth is still last and only when nothing is pending. |
| R09-P2-02 | HIGH | **Fixed.** The privacy sheet records consent through `recordConsent` and cannot claim agreement without a receipt; third-party guarantor consent is a separate dated grant (`recordGuarantorConsent`) required by the rules; `withdrawConsent` records revocation without deleting the proof. |
| R09-P2-03 | HIGH | **Fixed.** In-app "Download my data (JSON)" plus server-side redaction of third-party guarantor data, consent receipts and the retention schedule in the envelope. |
| R09-P2-04 | HIGH | **Fixed (pure core + schedule).** `RETENTION_POLICY`, `LEGAL_HOLDS`, `decideRetention`, `planRetentionSweep`, `anonymisePatch` and the daily `applyRetentionPolicy` sweep writing `retention_reports`. |
| R09-P2-05 | MEDIUM | **Fixed.** Encryption claims replaced with what the code does; `pii.ts` masks NINs, fingerprints them under a salt and scrubs logs; the reviewer's verification stores a masked NIN and a fingerprint rather than a second plaintext copy. |
| R09-P2-06 | MEDIUM | **Fixed.** `docs/privacy/DSAR.md` rewritten to the real flow; new `docs/privacy/RETENTION.md`. |
| R09-P2-07 | LOW | **Already closed in R07** (`storage.rules` requires `isAuthenticated()`). Recorded, not re-implemented. |

## Evidence

- CI (run 44767996592 / 44767996563): **7/7 checks pass** — Compile and unit tests (2m29s), Backend
  build and tests, Backend handler tests, Firestore rules tests (36s), Documentation checks, Secret
  scan, CodeRabbit.
- Backend `npm run build && npm test`: **272/272** (retention 8, pii 7, compliance +4, accountDeletion
  +3).
- Firestore rules: **24** emulator tests (4 new).
- Guards: document map, README claims, doc links, traceability, architecture, query limits, secret
  scan, and the new `check_consent_version.mjs`.

## Not claimed

- Nothing is deployed; the daily sweep has never been observed firing.
- The Storage purge is not executed against a real bucket, and there is no Storage lifecycle rule for
  abandoned KYC uploads (stated as unhandled in `docs/privacy/RETENTION.md`).
- Firestore backups are outside the sweep's reach.
- Regulator filings, DPAs, and processor sandbox behaviour remain **UNVERIFIED**.
- `REM-037` (preserve a complaint while erasing the complainant) stays `IN_PROGRESS`: it needs a
  product decision about who may read a preserved complaint, and this round does not pretend to have
  made it.
