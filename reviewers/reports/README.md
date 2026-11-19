# Reviewer Reports — Output Convention

This directory holds the evidence output of the 15-reviewer programme. It is empty until a reviewer submits.

## Naming
```
reviewers/reports/REVIEW_01.md   ← R01 Architecture & Codebase Integrity
reviewers/reports/REVIEW_07.md   ← R07 Application Security
...
reviewers/reports/REVIEW_15.md   ← R15 Documentation & Traceability
```

## Required structure
Each report must follow the template in `REVIEW_PLAN.md` §7 and cover all 11 sections mandated by its instruction file in `reviewers/`.

## Evidence rules
1. Every claim cites `path:line` or a command transcript.
2. Label each statement `OBSERVED`, `READ`, `INFERRED` or `UNVERIFIED`.
3. Redact secrets and PII; cite the location, never the value.
4. Record the commit SHA and tool versions.
5. Do not modify application source, rules, build files or tests.

## Verdicts
`PASS` · `PASS WITH CONDITIONS` · `FAIL` · `BLOCKED` · `UNVERIFIED`

A reviewer who could not execute the required checks must not issue `PASS`.

## After submission
1. Add a row/status update in `REVIEWER_REGISTER.md`.
2. Cross-reference findings in `ASSESSMENT_REPORT.md` and re-score `PRODUCTION_READINESS.md`.
3. R15 reconciles into the traceability matrix at programme close.

## Prohibited content
- Real credentials, tokens, NINs, phone numbers or customer data.
- Unredacted exploit payloads against a live environment.
- Claims of production readiness.
- Remediation code (link to a separate authorised change instead).
