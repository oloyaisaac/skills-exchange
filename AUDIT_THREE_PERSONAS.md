# Multi-Perspective Platform Audit: Worker, Client, and Administrator

**Audit Target:** Skills Exchange Codebase (`app/src/main/java/com/example/`, `functions/src/`, `firestore.rules`)  
**Methodology:** Direct inspection of Kotlin Composables, Room entities, ViewModels, Repository logic, Cloud Functions, and Firebase Security Rules without reliance on PRD or Markdown planning documents.  
**Date:** October 15, 2026  

---

## Executive Summary

The platform has established foundational infrastructure: strict Firestore authorization rules, tamper-resistant MoMo collection pull-to-verify routines, Room DB schema definitions for jobs/quotes/disputes, and an onboarding verification pipeline. 

However, inspecting the actual user journeys reveals a stark divide between what the backend models in code and what users can actually execute on their devices. Most notably:
1. **Workers** have no UI modal or screen to submit custom price quotes or withdraw wallet balances, even though `submitQuote()` and payout models exist in the data layer.
2. **Clients** cannot attach photos when posting a job, cannot pick a scheduled calendar slot, cannot enter a specific street address, and have no UI button to cancel a request.
3. **Administrators** possess zero UI interfaces whatsoever (no web portal, mobile screen, or operational console) to review KYC/guarantor photos, arbitrate disputes, monitor SOS emergencies, or audit escrow balances.

---

## 1. Worker Persona Audit: Skilled Neighbor / Tradesperson

> *"I am a verified electrician/plumber in Sunnyside. I rely on this app for daily income, fair quotes, and secure payment once my work is done."*

### What Currently Works for Workers
- **Dual-Role Onboarding:** Seamless transition between Client and Worker modes via [MainScreen.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/screens/MainScreen.kt) and [UserProfileEntity](file:///d:/skills%20exchange/app/src/main/java/com/example/data/db/Entities.kt#L373-L400).
- **Compliance & Training Gate:** Mandatory 3-module training enforcement via [WorkerTrainingScreen](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/WorkerVerificationStatusView.kt) before account activation.
- **Availability Toggle:** Online/offline toggle banner in [FreelancerDashboardView.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/FreelancerDashboardView.kt#L98-L140).
- **Physical Handshake Protection:** Doorstep PIN verification in [EscrowAndProofSheet.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/EscrowAndProofSheet.kt#L81-L83) prevents clients from claiming work never started.
- **Operational Safeguards:** Support for material advance requests and emergency add-on budget approvals.

### Critical Missing Features & Blockers

#### 1. Inability to Submit Bids / Custom Quotes in UI
- **The Code Gap:** While [SkillsExchangeRepository.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt#L1066) and [SkillsExchangeViewModel.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt#L1747) implement `submitQuote(jobId, workerId, proposedRate, proposedHours, notes)`, there is **no Composable dialog or input sheet** in the app.
- **The Worker Reality:** When a worker taps the **"Submit Quote"** CTA on [WorkerJobFeedCard](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/FreelancerDashboardView.kt#L422-L450), it invokes `onSelectJob()`, which immediately opens [EscrowAndProofSheet.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/EscrowAndProofSheet.kt) instead of a quoting form. A worker cannot specify their hourly rate, estimated duration, or proposal terms.

#### 2. Read-Only Wallet with No Cashout / Withdrawal Trigger
- **The Code Gap:** [EscrowWalletSheet](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/AccountCenterSheets.kt#L129-L219) only filters and renders static lists of held and released funds.
- **The Worker Reality:** Once escrow is released, workers have no *"Withdraw to MoMo / Airtel Money"* button to disburse wallet balances to their SIM cards. They cannot change their payout destination number or view transaction fee deductions.

#### 3. No Route Navigation to Job Site
- **The Code Gap:** Job entities in [Entities.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/data/db/Entities.kt#L174-L242) store general `district`, but lack geo-intent triggers.
- **The Worker Reality:** Workers accepted for a job cannot launch Google Maps/turn-by-turn navigation directly from the active job card to travel to the client's home.

#### 4. Lack of Dispute Defense / Counter-Evidence Submission
- **The Code Gap:** [EscrowAndProofSheet.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/EscrowAndProofSheet.kt#L61) provides `onSubmitDispute` for clients, but when a dispute is lodged against a worker, there is no interface for the worker to upload rebuttal photos, receipts, or explanations.

#### 5. Missing Portfolio, Tooling, & Tiered Pricing Customization
- **The Code Gap:** [ServiceProviderEntity](file:///d:/skills%20exchange/app/src/main/java/com/example/data/db/Entities.kt#L106-L135) contains static fields (`skillsCsv`, `hourlyRate`), but no support for project galleries, trade certificates, or tiered rates (standard vs. emergency callout).

---

## 2. Client Persona Audit: Homeowner / Resident

> *"I am a resident looking for a trusted neighborhood worker. I need to clearly describe my problem, know when they will arrive, and ensure my money is safe until the job is done."*

### What Currently Works for Clients
- **Discovery Experience:** Map ([InteractiveMapView.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/InteractiveMapView.kt)) and filtered list views with verified neighborhood badges.
- **AI Voice Job Creation:** Voice-to-job drafting via Gemini transcription in [QuickRequestDrawer.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/QuickRequestDrawer.kt#L150).
- **Kanban Lifecycle Tracking:** Five-stage visual pipeline (`Requested` $\rightarrow$ `Accepted` $\rightarrow$ `In Progress` $\rightarrow$ `Review` $\rightarrow$ `Completed`) in [JobPipelineKanban.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/JobPipelineKanban.kt).
- **Protected Escrow & Verification:** USSD push MoMo collection, Doorstep PIN generation, and star ratings with review feedback.

### Critical Missing Features & Blockers

#### 1. Cannot Attach Photos During Job Posting
- **The Code Gap:** [QuickRequestDrawer.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/QuickRequestDrawer.kt#L137-L150) parameters only take text, numbers, and enum types. Photo URLs (`beforePhotoUrl`) only become accessible in [EscrowAndProofSheet.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/EscrowAndProofSheet.kt) after a job is already in progress.
- **The Client Reality:** A homeowner with a leaking pipe, burnt breaker box, or cracked wall cannot take a photo when posting the request. Workers must bid blind without seeing the scope of damage.

#### 2. No Specific Date/Time Appointment Scheduling
- **The Code Gap:** The drawer only allows toggling `URGENT` vs `SCHEDULED` in [QuickRequestDrawer.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/QuickRequestDrawer.kt).
- **The Client Reality:** Clients cannot book a worker for a specific window (e.g., *"Tomorrow at 2:00 PM"* or *"Saturday morning"*). Scheduling must be negotiated informally in chat.

#### 3. No Exact Address or Landmark Field
- **The Code Gap:** [JobPostEntity](file:///d:/skills%20exchange/app/src/main/java/com/example/data/db/Entities.kt#L198) only captures `district` (e.g., "Sunnyside"), lacking `streetAddress`, `apartmentNumber`, or `landmarkDirections`.
- **The Client Reality:** Because phone numbers are masked in chat ([ChatDrawer.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/ChatDrawer.kt#L26-L33)), clients struggle to provide precise doorstep navigation without triggering mask warnings.

#### 4. Missing Job Cancellation & Edit Controls
- **The Code Gap:** [firestore.rules](file:///d:/skills%20exchange/firestore.rules#L244-L250) permits transitioning a job to `CANCELLED`, but neither [JobPipelineKanban.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/JobPipelineKanban.kt) nor [MainScreen.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/screens/MainScreen.kt) provides a *"Cancel Job"* button.
- **The Client Reality:** If a client posts a job by mistake, solves the issue themselves, or receives no acceptable quotes, they cannot cancel or delete the post; it remains indefinitely in their pipeline.

#### 5. Real-Time Worker En-Route Tracking
- **The Code Gap:** [JobStatus.WORKER_EN_ROUTE](file:///d:/skills%20exchange/app/src/main/java/com/example/data/db/Entities.kt#L15) exists in the state machine, but there is no live worker location streaming on the map for clients.

#### 6. Re-Hire Favorites & Voluntary Gratuity
- **The Code Gap:** No mechanism to bookmark or directly re-engage a previously hired provider from [JobHistorySheet](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/AccountCenterSheets.kt#L224), and no option to add a tip when releasing escrow.

---

## 3. Platform Administrator Persona Audit: Trust, Safety & Operations

> *"I am responsible for regulatory compliance, KYC identity validation, dispute arbitration, fraud prevention, and platform solvency."*

### What Currently Works for Admins (Backend Layer)
- **Role Enforcement & Security Rules:** [firestore.rules](file:///d:/skills%20exchange/firestore.rules) strictly gates privileged fields (`workerKycStatus`, `isWorkerActivated`, `ninVerificationStatus`) to `isAdmin()`.
- **Callable Cloud Functions:** Functions exist in [index.ts](file:///d:/skills%20exchange/functions/src/index.ts) for:
  - `getVerificationQueue` (aggregates KYC and guarantor submissions)
  - `reviewWorkerApplication` & `reviewGuarantorSubmission`
  - `setWorkerActivationStatus`
  - `getDisputesQueue` & `resolveDispute`

### Critical Missing Features & Blockers

#### 1. Complete Absence of an Admin Dashboard / Frontend
- **The Code Gap:** In [SkillsExchangeViewModel.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt#L1666-L1688), `adminVerificationQueue` and `submitAdminDecision()` exist, but are **never collected or rendered by any Composable in the entire repository**.
- **The Admin Reality:** There is no web admin portal, no protected admin drawer in the app, and no desktop backoffice. Platform operators currently have no screen to perform their core job responsibilities.

#### 2. Blind Dispute Arbitration
- **The Code Gap:** While [functions/src/index.ts:1188](file:///d:/skills%20exchange/functions/src/index.ts#L1188) exposes `resolveDispute`, admins have no viewer to examine both parties' statements, inspect uploaded photo evidence, or read chat history before issuing refunds or penalties.

#### 3. No Live SOS Emergency Monitoring Console
- **The Code Gap:** [JobPostEntity.isSosTriggered](file:///d:/skills%20exchange/app/src/main/java/com/example/data/db/Entities.kt#L234) allows flagging emergencies, but there is no dispatch dashboard, SMS emergency broadcast trigger, or real-time admin incident feed.

#### 4. Lack of Financial Reconciliation & Escrow Ledger
- **The Code Gap:** There is no unified view showing total platform fees collected, escrow amounts held, failed MoMo disbursements, or reconciliation between telecom transaction IDs and platform records.

#### 5. Hardcoded Platform Configuration
- **The Code Gap:** Platform fee rates (10%) and callout fees ($5.00) are hardcoded directly into data entities and calculations rather than controlled dynamically via an administrative remote config.

---

## Consolidated Persona Gap Matrix

| Capability / Flow | Worker Perspective | Client Perspective | Administrator Perspective | Current Code Status |
|---|---|---|---|---|
| **Custom Quote / Bidding** | ❌ Cannot enter rate/hours | ⚠️ Compares quotes, but none can be sent | ❌ No visibility into bid spreads | Backend exists; UI missing |
| **Wallet Disbursement** | ❌ No cashout/withdrawal button | N/A | ❌ No disbursement queue management | Read-only Composable |
| **Job Photo Attachment** | ⚠️ Can submit proof photos | ❌ Cannot attach photos when posting | ❌ No content moderation queue | Post drawer lacks photo picker |
| **Appointment Scheduling** | ❌ No calendar/availability control | ❌ Urgent/Normal toggle only | N/A | Missing date/time picker |
| **Cancellation & Revisions** | N/A | ❌ No cancel/edit buttons on live jobs | ❌ Cannot cancel stagnant jobs | Rules allow; UI missing |
| **Job Site Navigation** | ❌ No map/turn-by-turn route link | ❌ No live worker ETA on map | N/A | No geo-intent routing |
| **Dispute Resolution** | ❌ Cannot submit counter-evidence | ✅ Can file dispute with reason | ❌ No UI dashboard to arbitrate | Cloud function exists; UI missing |
| **Identity & Guarantor Review**| ⚠️ Submits data, waits blind | N/A | ❌ Must invoke functions via CLI/cURL | ViewModel unrendered |
| **SOS & Incident Response** | ⚠️ Can trigger alert | ⚠️ Can trigger alert | ❌ No incident response desk | Boolean flag only |

---

## Recommended Priority Roadmap

### Phase 1: Close Worker & Client Operational Loops (Mobile App)
1. **Quote Proposal Modal:** Create a dedicated sheet in [FreelancerDashboardView.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/FreelancerDashboardView.kt) allowing workers to input `proposedRate`, `proposedHours`, and `notes`, wiring directly to [submitQuote()](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt#L1747).
2. **Job Post Enhancements:** Add photo attachment picker and date/time selector to [QuickRequestDrawer.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/QuickRequestDrawer.kt).
3. **Cancel & Navigation Controls:** Add a *"Cancel Job"* dialog and external Maps route launcher in [JobPipelineKanban.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/JobPipelineKanban.kt).
4. **Mobile Money Cashout CTA:** Add a *"Withdraw Balance"* button to [EscrowWalletSheet](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/AccountCenterSheets.kt#L129) triggering backend disbursement.

### Phase 2: Operations & Admin Console (Internal / Admin Portal)
1. **Admin Backoffice UI:** Implement an Admin screen/portal connecting to [getVerificationQueue](file:///d:/skills%20exchange/functions/src/index.ts#L807) and [getDisputesQueue](file:///d:/skills%20exchange/functions/src/index.ts#L1302).
2. **Dispute Arbitration Screen:** Build an evidence review and escrow distribution console.
3. **Live Safety Monitor:** Create an SOS and reporting alert center for community safety oversight.
