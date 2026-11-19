# R12 — UI/UX & Accessibility Specialist

## 1. Reviewer Number
R12 (PRD story `RVW-012`, Priority P1). Depends on R01 for compilable UI.

## 2. Professional Title
Senior Product Designer & Accessibility Specialist (WCAG 2.2 AA, Android accessibility, low-literacy and low-bandwidth markets).

## 3. Required Expertise
- Material 3 / Compose design systems, typography, colour and contrast.
- Android accessibility: TalkBack, semantics, touch targets, focus order, font scaling, reduced motion.
- Localisation for East Africa (English, Luganda, Swahili, Kinyarwanda) and RTL readiness.
- Low-end devices, small screens, offline/degraded states.
- Usability for users with low digital literacy and feature-phone-adjacent habits.
- Copy quality for money, safety and legal consent.

## 4. Responsibilities
1. Audit every screen for hierarchy, clarity and task completion.
2. Audit accessibility semantics, labels, contrast and touch targets.
3. Audit string externalisation and localisation readiness.
4. Audit error, empty, loading and offline states for every major flow.
5. Audit safety-critical copy (SOS, PIN, escrow, consent) for accuracy and non-deception.
6. Audit the dual-role (client/worker) information architecture and mode-switch clarity.
7. Verify admin operator UX supports high-stakes decisions without ambiguity.

## 5. Exact Features to Examine
- Onboarding and OTP.
- Nearby discovery, filters, provider detail, map and fallback.
- Quick request, quoting, hiring, job pipeline.
- Escrow/proof flow, payment channel selection, doorstep PIN.
- Chat, notifications, account center, settings, backup contact.
- Worker dashboard, KYC/guarantor, training.
- Admin verification and dispute sheets.

## 6. Relevant Source Files and Components
| Path | Why |
|---|---|
| `app/src/main/java/com/example/ui/screens/MainScreen.kt` | primary shell and tabs |
| `app/src/main/java/com/example/ui/screens/OnboardingScreen.kt` | first-run experience |
| `app/src/main/java/com/example/ui/components/*` (26 files) | all feature UI |
| `app/src/main/java/com/example/ui/theme/{Color,Theme,Type}.kt` | palette, dark mode, typography |
| `app/src/main/res/values/strings.xml` | only one string resource; 178 inline `Text("…")` |
| `app/src/main/res/values/themes.xml`, `colors.xml` | theme duplication |
| `app/src/main/AndroidManifest.xml` | `supportsRtl="true"`, label, orientation |
| `app/src/main/java/com/example/ui/components/PermissionRationaleDialog.kt` | permission copy |
| `app/src/main/java/com/example/ui/components/AccountComplianceDialog.kt` | legal/consent copy |
| `app/src/main/java/com/example/ui/components/EscrowAndProofSheet.kt` | money/safety copy |

## 7. Functional Requirements
- Every action has a visible state: idle, loading, success, error, retry.
- Forms explain validation before submission and preserve input on error.
- Offline state is visible and tells the user what will sync.
- Critical actions require explicit confirmation with consequences stated.
- Mode switching (client/worker) is unambiguous and reversible.
- Navigation/back behaviour is predictable; no dead ends.

## 8. Security Requirements
- Sensitive screens (National ID, PIN, phone numbers) must not be exposed in recents screenshots.
- Masking must be visually evident (phone numbers, NIN).
- Permission requests must explain purpose and never coerce.
- Legal/consent copy must be accurate; no unverified security claims.

## 9. Testing Requirements
- Accessibility Scanner / TalkBack pass on critical flows.
- Contrast checks for light and dark themes.
- Font-scale 200% and small-screen layout checks.
- Localisation smoke test with pseudo-locale and RTL.
- Usability test with 5+ target users per persona where feasible.

## 10. Expected Evidence
- Screen inventory with issues, severity and screenshots.
- Accessibility findings with node/semantics evidence.
- String inventory: hardcoded vs resources, with migration plan.
- Contrast measurements and touch-target sizes.
- Copy review for money/safety/consent with recommended wording.

## 11. Passing Conditions
- PASS requires: WCAG 2.2 AA for critical flows, all user-facing copy externalised and translatable, complete state coverage, verified contrast/touch targets, and user testing on target devices.
- FAIL now: zero `stringResource` usage, one string resource total, 59 icons with `contentDescription = null`, and English-only copy for a four-country market.
