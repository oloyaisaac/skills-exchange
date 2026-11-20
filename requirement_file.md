# Skills Exchange — Product Requirements Document (PRD)

## 1. Executive Summary & Product Model

### Product Purpose
Skills Exchange is a hyperlocal peer-to-peer services marketplace operating in East African urban centers (Uganda, Kenya, Tanzania, Rwanda). It connects nearby residents (clients) with verified local workers and service providers for home repairs, maintenance, technical tasks, personal services, and recurring contracts.

### Operational Surfaces
The platform consists of three distinct operational surfaces:
1. **Client Experience (Android App)**: Search nearby specialists, post jobs, receive and compare multiple quotes, hire workers, negotiate recurring contracts, fund Mobile Money escrow, verify doorstep arrivals, chat, and review workers.
2. **Worker Experience (Android App)**: Separate worker dashboard gated by identity verification and training activation. Allows workers to toggle availability, browse job requests, submit quotes, request material advances, submit ground proof photos, and receive Mobile Money payouts.
3. **Admin Experience (Separate Administrative Web Dashboard)**: Web-based administrative portal for platform operators to review worker KYC applications, verify Uganda National IDs, inspect two-guarantor submissions, review skill certificates/demo videos, issue "Changes Required" feedback, track 2-3 day worker training progress, toggle worker activation/deactivation, manage disputes, and handle support.

### Unified Account Model
A user creates a single account during registration and selects an initial mode (`Client` or `Worker`).
- A single person owns **one account**.
- A user registered as a Client can apply at any time to become a Worker.
- A user registered as a Worker can seamlessly switch to Client mode to hire other specialists.
- Two separate accounts are never created for the same individual.
- Worker functionality remains strictly gated behind verification approval and admin activation.

---

## 2. Product Goals

### MVP Goals (v1.0 Production Readiness)
1. **Unified Account & Role Switching**: Support single user profile with client/worker mode toggling and verification gating.
2. **Client GPS Onboarding**: Acquire location coordinates with manual neighborhood correction option (no National ID required for clients).
3. **Mandatory Worker Verification Pipeline**: Collect Uganda National ID (front & back), full legal name, DOB, gender, and exactly **two guarantors** with complete ID documents.
4. **Service Taxonomy (125 Services)**: Enforce a structured 125-service catalog categorized into 5 main hubs with related skill validation (1 Primary + up to 2 Related Additional Skills).
5. **Flexible Qualification Evidence**: Support formal certificates or alternative admin-approved evidence (demonstration video, work photos, in-person visit).
6. **Verification Lifecycle & 48-Hour SLA**: Enforce state transitions (`Application In Progress` -> `Submitted` -> `Under Review` -> `Changes Required`/`Rejected`/`Approved`), tracking timestamps to target 48-hour review decisions.
7. **Worker Training & Admin Activation Gate**: Track 2-3 day training completion before admin activates worker eligibility to bid.
8. **Secure Document Storage & Privacy**: Isolate National IDs, guarantor IDs, and certificates in secure Firebase Storage accessible only by admins. Obfuscate exact client addresses on public worker profiles (district-level job distribution stats only).
9. **Dual Marketplace Discovery**: Support both direct nearby worker selection and open job multi-quote bidding.
10. **One-Time & Recurring Contracts**: Support single jobs and recurring contract schedules (Daily, Weekly, Bi-weekly, Monthly, Custom) with negotiated pricing.
11. **Escrow Payments & Doorstep Security**: Mobile Money (MTN MoMo / Airtel Money) escrow lock, dynamic 4-digit doorstep PIN generation, and emergency SOS alerts.
12. **Admin Web Dashboard Specification**: Build administrative web portal specs for verification queue processing, document review, and activation management.

---

## 3. Account Model & User Roles

### Registration & Role Flow
```text
                       ┌─────────────────────────┐
                       │   Initial Registration  │
                       └────────────┬────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    ▼                               ▼
          ┌───────────────────┐           ┌───────────────────┐
          │   Client Mode     │           │   Worker Mode     │
          │ (Immediate Access)│           │ (Requires KYC)    │
          └─────────┬─────────┘           └─────────┬─────────┘
                    │                               │
                    │ Apply to become Worker        │ Immediate Client
                    ▼                               │ Access Included
          ┌───────────────────┐                     │
          │ Worker KYC Submit │<────────────────────┘
          └─────────┬─────────┘
                    │
                    ▼
          ┌───────────────────┐
          │   Under Review    │ (48h Operational SLA Target)
          └─────────┬─────────┘
                    │ Approved
                    ▼
          ┌───────────────────┐
          │  Training (2-3d)  │ (Covers platform rules)
          └─────────┬─────────┘
                    │ Completed & Admin Activated
                    ▼
          ┌───────────────────┐
          │ Active Worker Mode│
          └───────────────────┘
```

### Permission Matrix

| Operation / Feature | Client Mode | Unverified Worker | Active Worker | Admin (Web Portal) | Guest |
|---|---|---|---|---|---|
| Browse nearby workers | ✅ | ✅ | ✅ | ✅ | ✅ |
| Search 125-service catalog | ✅ | ✅ | ✅ | ✅ | ✅ |
| Post one-time / recurring job | ✅ | ✅ | ✅ | ❌ | ❌ |
| Fund Mobile Money Escrow | ✅ | ✅ | ✅ | ❌ | ❌ |
| Submit worker application | N/A | ✅ | ✅ | ❌ | ❌ |
| Upload National ID & 2 Guarantors | N/A | ✅ | ✅ | ❌ | ❌ |
| Submit quote on open job | ❌ | ❌ (Gated) | ✅ | ❌ | ❌ |
| Access Worker Dashboard | ❌ | ❌ (Gated) | ✅ | ❌ | ❌ |
| Receive MoMo Escrow Payouts | ❌ | ❌ | ✅ | ❌ | ❌ |
| Review KYC Docs & Guarantor IDs | ❌ | ❌ | ❌ | ✅ (Strict) | ❌ |
| Approve / Reject / Request Changes | ❌ | ❌ | ❌ | ✅ | ❌ |
| Activate / Deactivate Worker | ❌ | ❌ | ❌ | ✅ | ❌ |

---

## 4. Client Onboarding & Location Management

### Information Collected
- Full Legal Name
- Verified Mobile Phone Number (SMS OTP)
- Optional Profile Photo
- Current District (e.g. Kampala, Wakiso, Mukono)
- Current Location Address / Landmark
- Device GPS Coordinates (Latitude / Longitude) when permission granted
- Manual Location Correction: Client can drag map pin or select neighborhood if GPS/geocoding is inaccurate.

### Privacy & Identity Policy
- Client National ID verification is **NOT required**.
- Client exact street coordinates are never displayed publicly.

---

## 5. Worker Verification & KYC Engine

### 5.1 Mandatory Application Data
- **Identity**: Full legal name as shown on National ID, National ID Number, Phone Number, Date of Birth, Gender.
- **Primary Identification**: Uganda National ID (Front & Back photos). *Note: Uganda National ID is default/primary. Without an eligible accepted ID, worker cannot qualify for activation.*
- **Location**: District, Address, GPS coordinates, Human-readable location. (Editable later).
- **Professional Details**: Primary Skill, up to 2 Related Additional Skills, Years of Experience, Bio/Description, Profile Photo.

### 5.2 Mandatory Two-Guarantor Requirement
Every worker application **MUST** include exactly **two distinct guarantors**. One guarantor cannot substitute for two.

For EACH Guarantor:
- Full Name
- Phone Number
- Relationship to Worker (e.g., Former Employer, Community Leader, Relative)
- District / Location
- Guarantor National ID Front photo
- Guarantor National ID Back photo

Guarantor data models and verification statuses are stored separately from worker identity fields.

### 5.3 Primary Skill Qualification & Alternative Evidence
- **Primary Skill**: Requires qualification evidence.
- **Formal Evidence**: Certificate / Diploma from a recognized institute or trade school.
- **Alternative Evidence (Informal Trade Workers)**: Demonstration video, portfolio photo evidence, in-person trade evaluation visit, or admin-approved trade reference.
- **Additional Skills**: Do not require formal certificate uploads if related to primary skill.

### 5.4 Verification Lifecycle State Machine
```text
NOT_STARTED -> APPLICATION_IN_PROGRESS -> SUBMITTED -> UNDER_REVIEW
                                                            │
                     ┌──────────────────────────────────────┴──────────────────────────────────────┐
                     ▼                                                                             ▼
             CHANGES_REQUIRED                                                                  APPROVED
                     │                                                                             │
                     ▼ (Worker updates docs & resubmits)                                           ▼
                RESUBMITTED ───> UNDER_REVIEW                                               TRAINING_PENDING
                                                                                                   │
                                                                                                   ▼ (2-3 Days Completed)
                                                                                            TRAINING_COMPLETED
                                                                                                   │
                                                                                                   ▼ (Admin Toggles Activation)
                                                                                                ACTIVATED
```

- **48-Hour SLA Target**: `submittedAt` and `reviewedAt` timestamps are recorded. System tracks review turnaround against the 48-hour operational target.
- **Changes Required Screen**: If admin flags blurry IDs or invalid certificates, worker is presented with a restricted screen displaying the admin's feedback reason and an "Update Documents" button. Normal worker dashboard access remains blocked.
- **Customer Support Contact**: Direct Phone and WhatsApp customer support links displayed on verification screens.

---

## 6. Worker Training & Activation Gating

- **Sequence**: `Approved` -> `Training` -> `Activated`.
- **Training Duration**: 2 to 3 days covering platform quality standards, customer etiquette, safety protocols, and escrow rules.
- **Delivery Formats**: Video modules, WhatsApp training groups, online reference materials.
- **Completion Tracking**: Progress status (`NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`) tracked per worker.
- **Activation Gate**: Worker cannot receive job broadcasts, appear in nearby searches, or submit quotes until Admin verifies `KYC Approved` + `Training Completed` and explicitly toggles `isActivated = true`.

---

## 7. Service Taxonomy & 125-Service Catalog

Workers select **1 Primary Skill** + up to **2 Related Additional Skills**. Additional skills must belong to the same logical service family as the primary skill.

The platform enforces a structured **125-Service Taxonomy** divided into 5 core category hubs:

### Category 1: Construction & Building (30 Services)
| Service ID | Service Name | Skill Family | Qual. Evidence Expected | Primary Eligible | Additional Eligible |
|---|---|---|---|---|---|
| `CONST-01` | Domestic Plumbing & Pipe Leak Repair | Plumbing & Fluid Systems | Yes | Yes | Yes |
| `CONST-02` | Commercial Pipefitting & Drainage | Plumbing & Fluid Systems | Yes | Yes | Yes |
| `CONST-03` | Water Tank & Pump Installation | Plumbing & Fluid Systems | Yes | Yes | Yes |
| `CONST-04` | Sanitary Fixture & Septic Maintenance | Plumbing & Fluid Systems | No | Yes | Yes |
| `CONST-05` | Residential Electrical Wiring | Electrical & Power | Yes | Yes | Yes |
| `CONST-06` | Industrial Panel & Breaker Upgrades | Electrical & Power | Yes | Yes | Yes |
| `CONST-07` | Backup Generator Installation & Repair | Electrical & Power | Yes | Yes | Yes |
| `CONST-08` | Lighting Fixture & Fan Fitting | Electrical & Power | No | Yes | Yes |
| `CONST-09` | Rough Carpentry & Wood Framing | Carpentry & Joinery | No | Yes | Yes |
| `CONST-10` | Custom Cabinetry & Furniture Making | Carpentry & Joinery | No | Yes | Yes |
| `CONST-11` | Hardwood & Laminate Flooring | Carpentry & Joinery | No | Yes | Yes |
| `CONST-12` | Roof Truss & Timber Repair | Carpentry & Joinery | No | Yes | Yes |
| `CONST-13` | Masonry & Bricklaying | Masonry & Concrete | No | Yes | Yes |
| `CONST-14` | Concrete Foundation & Slab Pouring | Masonry & Concrete | Yes | Yes | Yes |
| `CONST-15` | Plastering & Rendering | Masonry & Concrete | No | Yes | Yes |
| `CONST-16` | Stone Paver & Driveway Laying | Masonry & Concrete | No | Yes | Yes |
| `CONST-17` | Interior & Exterior House Painting | Finishing & Decoration | No | Yes | Yes |
| `CONST-18` | Spray Painting & Wood Staining | Finishing & Decoration | No | Yes | Yes |
| `CONST-19` | Wall Tiling & Backsplash Fitting | Finishing & Decoration | No | Yes | Yes |
| `CONST-20` | POP & Gypsum Ceiling Installation | Finishing & Decoration | No | Yes | Yes |
| `CONST-21` | Structural Steel Welding | Metal Fabrication | Yes | Yes | Yes |
| `CONST-22` | Security Gate & Metal Railing Welding | Metal Fabrication | No | Yes | Yes |
| `CONST-23` | Iron Sheet Roofing & Gutters | Roofing & Weatherproofing | No | Yes | Yes |
| `CONST-24` | Waterproofing & Leak Proofing | Roofing & Weatherproofing | No | Yes | Yes |
| `CONST-25` | Glass Window & Door Fitting | Glass & Aluminum | No | Yes | Yes |
| `CONST-26` | Aluminum Partitioning & Shopfronts | Glass & Aluminum | No | Yes | Yes |
| `CONST-27` | Scaffolding Rigging & Erection | Site Logistics | Yes | Yes | Yes |
| `CONST-28` | Demolition & Debris Clearance | Site Logistics | No | Yes | Yes |
| `CONST-29` | Borehole & Water Well Repair | Heavy Trades | Yes | Yes | Yes |
| `CONST-30` | Fencing & Perimeter Wall Construction | Security Infrastructure | No | Yes | Yes |

### Category 2: Cleaning & Home Services (25 Services)
| Service ID | Service Name | Skill Family | Qual. Evidence Expected | Primary Eligible | Additional Eligible |
|---|---|---|---|---|---|
| `CLEAN-01` | Deep House Cleaning & Sanitation | Residential Cleaning | No | Yes | Yes |
| `CLEAN-02` | Post-Renovation Site Cleanup | Residential Cleaning | No | Yes | Yes |
| `CLEAN-03` | Move-In / Move-Out Deep Clean | Residential Cleaning | No | Yes | Yes |
| `CLEAN-04` | Office & Commercial Janitorial Clean | Commercial Cleaning | No | Yes | Yes |
| `CLEAN-05` | Carpet & Upholstery Steam Washing | Specialty Cleaning | No | Yes | Yes |
| `CLEAN-06` | Window Washing & Exterior Facade | Specialty Cleaning | No | Yes | Yes |
| `CLEAN-07` | Compound & Lawn Grass Mowing | Grounds & Gardening | No | Yes | Yes |
| `CLEAN-08` | Tree Trimming & Hedge Pruning | Grounds & Gardening | No | Yes | Yes |
| `CLEAN-09` | Landscape Design & Flower Bed Care | Grounds & Gardening | No | Yes | Yes |
| `CLEAN-10` | Agricultural Garden Weeding & Tilling | Grounds & Gardening | No | Yes | Yes |
| `CLEAN-11` | Residential Laundry & Ironing | Domestic Services | No | Yes | Yes |
| `CLEAN-12` | Fumigation & Vector Pest Control | Environmental Health | Yes | Yes | Yes |
| `CLEAN-13` | House Moving & Relocation Loading | Moving Logistics | No | Yes | Yes |
| `CLEAN-14` | Heavy Furniture Assembly & Rigging | Moving Logistics | No | Yes | Yes |
| `CLEAN-15` | Garbage & Waste Hauling | Waste Management | No | Yes | Yes |
| `CLEAN-16` | Pool Cleaning & Chemical Treatment | Aquatic Care | Yes | Yes | Yes |
| `CLEAN-17` | Septic Tank Pumping & Exhausting | Environmental Health | Yes | Yes | Yes |
| `CLEAN-18` | Curtain & Blind Installation | Interior Setup | No | Yes | Yes |
| `CLEAN-19` | Home Organization & Decluttering | Domestic Services | No | Yes | Yes |
| `CLEAN-20` | Pressure Washing Driveways & Patio | Specialty Cleaning | No | Yes | Yes |
| `CLEAN-21` | Water Tank Cleaning & Chlorination | Sanitation | No | Yes | Yes |
| `CLEAN-22` | Solar Panel Surface Cleaning | Maintenance | No | Yes | Yes |
| `CLEAN-23` | Shoe Repair & Cobbling | Leathercraft | No | Yes | Yes |
| `CLEAN-24` | Mattress Sanitization & Stain Extraction | Specialty Cleaning | No | Yes | Yes |
| `CLEAN-25` | Commercial Kitchen Grease Trap Clean | Commercial Cleaning | Yes | Yes | Yes |

### Category 3: Technology & Electronics (25 Services)
| Service ID | Service Name | Skill Family | Qual. Evidence Expected | Primary Eligible | Additional Eligible |
|---|---|---|---|---|---|
| `TECH-01` | Smartphone Hardware Screen & Battery Fix | Mobile Repair | No | Yes | Yes |
| `TECH-02` | Mobile Motherboard Soldering & Micro-repair | Mobile Repair | Yes | Yes | Yes |
| `TECH-03` | Laptop Hardware Repair & Upgrades | Computer Hardware | Yes | Yes | Yes |
| `TECH-04` | PC Desktop Building & Diagnostics | Computer Hardware | No | Yes | Yes |
| `TECH-05` | CCTV Camera Wiring & DVR Config | Security Tech | Yes | Yes | Yes |
| `TECH-06` | Biometric Access Control & Smart Locks | Security Tech | Yes | Yes | Yes |
| `TECH-07` | Solar PV System Sizing & Inverter Install | Solar & Renewable | Yes | Yes | Yes |
| `TECH-08` | Solar Water Heater Setup & Service | Solar & Renewable | Yes | Yes | Yes |
| `TECH-09` | Refrigerator & Freezer Compressor Fix | Appliance Repair | Yes | Yes | Yes |
| `TECH-10` | Washing Machine & Dryer Service | Appliance Repair | Yes | Yes | Yes |
| `TECH-11` | Microwave & Kitchen Oven Repair | Appliance Repair | No | Yes | Yes |
| `TECH-12` | TV & Home Theater Decoder Setup | Consumer Electronics | No | Yes | Yes |
| `TECH-13` | Air Conditioning (AC) Sizing & Gas Refill | HVAC & Cooling | Yes | Yes | Yes |
| `TECH-14` | Office Wi-Fi Router & Mesh Networking | Networking | Yes | Yes | Yes |
| `TECH-15` | Fiber Optic Cable Splicing & Trunking | Networking | Yes | Yes | Yes |
| `TECH-16` | POS Machine & Receipt Printer Config | Commercial Hardware | Yes | Yes | Yes |
| `TECH-17` | Audio System & Speaker Installation | Consumer Electronics | No | Yes | Yes |
| `TECH-18` | Inverter & Battery Bank Wiring | Electrical Power | Yes | Yes | Yes |
| `TECH-19` | WordPress & Local Website Design | Digital Services | No | Yes | Yes |
| `TECH-20` | Graphic Design & Logo Branding | Digital Services | No | Yes | Yes |
| `TECH-21` | Software OS Installation & Virus Removal | Computer Software | No | Yes | Yes |
| `TECH-22` | Printer & Copier Maintenance | Office Tech | Yes | Yes | Yes |
| `TECH-23` | Intercom & PABX Phone System Setup | Networking | Yes | Yes | Yes |
| `TECH-24` | Vehicle GPS Tracker Installation | Telematics | Yes | Yes | Yes |
| `TECH-25` | Smart Home Automation Hub Config | Security Tech | Yes | Yes | Yes |

### Category 4: Automotive Services (20 Services)
| Service ID | Service Name | Skill Family | Qual. Evidence Expected | Primary Eligible | Additional Eligible |
|---|---|---|---|---|---|
| `AUTO-01` | General Car Engine Mechanical Service | Auto Mechanics | Yes | Yes | Yes |
| `AUTO-02` | Mobile Driveway Oil & Filter Change | Auto Mechanics | No | Yes | Yes |
| `AUTO-03` | Brake Pad Replacement & Rotor Polish | Auto Mechanics | No | Yes | Yes |
| `AUTO-04` | Auto Electrical Rewiring & Alternator Fix | Auto Electronics | Yes | Yes | Yes |
| `AUTO-05` | Car Battery Jumpstart & Replacement | Auto Electronics | No | Yes | Yes |
| `AUTO-06` | Motorcycle (Boda Boda) Engine Overhaul | Motorcycle Repair | No | Yes | Yes |
| `AUTO-07` | Motorcycle Tire Patching & Wheel Alignment | Motorcycle Repair | No | Yes | Yes |
| `AUTO-08` | Car AC Gas Refill & Leak Fix | HVAC Auto | Yes | Yes | Yes |
| `AUTO-09` | Auto Body Panel Beating & Dent Fix | Bodywork | No | Yes | Yes |
| `AUTO-10` | Car Spray Painting & Scratch Touch-up | Bodywork | No | Yes | Yes |
| `AUTO-11` | Mobile Car Detailing & Interior Wash | Car Wash | No | Yes | Yes |
| `AUTO-12` | Tire Mounting, Balancing & Vulcanizing | Tire Services | No | Yes | Yes |
| `AUTO-13` | OBD-II Computer Diagnostics Scan | Auto Electronics | Yes | Yes | Yes |
| `AUTO-14` | Suspension Bushing & Shock Replacement | Auto Mechanics | Yes | Yes | Yes |
| `AUTO-15` | Car Key Programming & Locksmithing | Locksmithing | Yes | Yes | Yes |
| `AUTO-16` | Clutch & Transmission Service | Auto Mechanics | Yes | Yes | Yes |
| `AUTO-17` | Windshield Glass Replacement | Auto Glass | No | Yes | Yes |
| `AUTO-18` | Car Audio & Alarm System Fitting | Auto Electronics | No | Yes | Yes |
| `AUTO-19` | Radiator & Engine Cooling System Fix | Auto Mechanics | No | Yes | Yes |
| `AUTO-20` | Tow Truck & Emergency Breakdown Assist | Breakdown Services | Yes | Yes | Yes |

### Category 5: Personal, Creative & Educational (25 Services)
| Service ID | Service Name | Skill Family | Qual. Evidence Expected | Primary Eligible | Additional Eligible |
|---|---|---|---|---|---|
| `PERS-01` | Mobile Men's Hair Barbering | Beauty & Grooming | No | Yes | Yes |
| `PERS-02` | Women's Hair Braiding & Styling | Beauty & Grooming | No | Yes | Yes |
| `PERS-03` | Bridal Makeup & Cosmetology | Beauty & Grooming | No | Yes | Yes |
| `PERS-04` | Manicure & Pedicure Nail Care | Beauty & Grooming | No | Yes | Yes |
| `PERS-05` | Bespoke Clothing Tailoring & Alteration | Fashion & Textiles | No | Yes | Yes |
| `PERS-06` | Primary School Math & Science Tutoring | Academic Tutoring | Yes | Yes | Yes |
| `PERS-07` | High School Physics & Chemistry Prep | Academic Tutoring | Yes | Yes | Yes |
| `PERS-08` | English & Swahili Language Instruction | Languages | Yes | Yes | Yes |
| `PERS-09` | Music Instrument Tutoring (Guitar/Piano) | Arts & Music | No | Yes | Yes |
| `PERS-10` | Event Portrait & Outdoor Photography | Media Production | No | Yes | Yes |
| `PERS-11` | Video Editing & Event Coverage | Media Production | No | Yes | Yes |
| `PERS-12` | Event Sound Equipment & DJ Setup | Event Services | No | Yes | Yes |
| `PERS-13` | Party Catering & Private Chef Services | Culinary | Yes | Yes | Yes |
| `PERS-14` | Event Decoration & Flower Arrangement | Event Services | No | Yes | Yes |
| `PERS-15` | Document Translation (Swahili/French/Luganda) | Languages | Yes | Yes | Yes |
| `PERS-16` | Home Fitness Training & Aerobics | Wellness | Yes | Yes | Yes |
| `PERS-17` | Massage & Physiotherapy Wellness | Wellness | Yes | Yes | Yes |
| `PERS-18` | Dog Training & Pet Grooming | Pet Care | No | Yes | Yes |
| `PERS-19` | Veterinary Vaccination & Pet Health | Animal Care | Yes | Yes | Yes |
| `PERS-20` | Commercial Signboard & Poster Printing | Print & Graphic | No | Yes | Yes |
| `PERS-21` | Cake Baking & Pastry Decoration | Culinary | No | Yes | Yes |
| `PERS-22` | Driver Instruction & Driving Lessons | Education | Yes | Yes | Yes |
| `PERS-23` | Childcare & Nanny Babysitting | Domestic Services | No | Yes | Yes |
| `PERS-24` | Elderly Caregiving & Assistance | Wellness | Yes | Yes | Yes |
| `PERS-25` | Legal Document Preparation Assistant | Professional Services | Yes | Yes | Yes |

---

## 8. Document Storage & Public Location Privacy

### 8.1 Firebase Storage Architecture
- All sensitive verification documents (National ID Front/Back, Guarantor ID Front/Back, Skill Certificates, Demo Videos) are stored in isolated Firebase Storage buckets under `gs://skills-exchange-private-docs/`.
- Strict Firebase Storage security rules enforce that document objects can ONLY be read or written by authorized Admin UIDs or the document owner during submission.
- Clients are NEVER granted read access to raw verification document URLs or National ID numbers.

### 8.2 Public Profile Privacy & Location Obfuscation
Client-facing worker profiles display public trust indicators without leaking sensitive PII or exact addresses:

```text
┌─────────────────────────────────────────────────────────┐
│  John Kateregga  ✓ VERIFIED WORKER                     │
│  ★ 4.8 (38 reviews) • Electrician                        │
│  Kampala District • 6 Years Experience                   │
│                                                         │
│  Primary Skill: Residential Electrical Wiring           │
│  Additional: Solar PV Setup, CCTV Wiring               │
│                                                         │
│  District Job History:                                  │
│  • Kampala Central: 42 Jobs Completed                   │
│  • Wakiso District: 18 Jobs Completed                   │
│  • Mukono District: 7 Jobs Completed                    │
└─────────────────────────────────────────────────────────┘
```
- **Reviews**: Public worker reviews hide reviewer names/identities to preserve client privacy.
- **Location**: Exact coordinates of past jobs are never exposed; only aggregated district-level statistics are shown.

---

## 9. Marketplace Discovery & Job Types

### 9.1 Discovery Modes
1. **Nearby Specialist Discovery**: Clients filter nearby verified workers by skill category, distance radius, rating, and hourly rate. Clients can directly contact or request a quote from a chosen worker.
2. **Open Job Marketplace**: Clients post a job request (title, description, budget, urgency, labor vs material breakdown). Relevant verified workers in the radius receive notifications and submit quotes. The client compares quotes and selects a worker.

### 9.2 Job Types & Recurring Contracts
- **One-Time Jobs (Default)**: Single-execution micro-jobs (e.g. leak repair, single tutoring session).
- **Recurring Contracts**: Ongoing service arrangements (e.g. weekly compound cleaning, bi-weekly catering, monthly AC maintenance).
- **Recurring Schedules**:
  - `DAILY`
  - `WEEKLY`
  - `BI_WEEKLY`
  - `MONTHLY`
  - `CUSTOM_DAYS` (e.g. Every Monday & Thursday)
- **Pricing & Negotiation**: Initial contract pricing is negotiated between client and worker via in-app quote proposals before escrow funding.

---

## 10. Administrative Web Dashboard Specification

A dedicated web-based operational portal (`admin.skillsexchange.app`) is specified for platform operators:

```text
Admin Web Dashboard
  ├── Verification Queue Module
  │     ├── List pending worker applications sorted by submission timestamp (48h SLA timer)
  │     ├── Inspect worker Uganda National ID images (Front & Back)
  │     ├── Inspect 2 Guarantor records & Guarantor ID images
  │     ├── Inspect Primary Skill Certificate / Demo Video evidence
  │     └── Trigger Decisions: [Approve], [Reject (Reason)], [Request Changes (Reason)]
  │
  ├── Training & Activation Module
  │     ├── Track 2-3 day worker rule training progress
  │     └── Action: [Activate Worker] (Gates bidding eligibility) / [Deactivate / Suspend]
  │
  ├── Dispute Resolution Module
  │     ├── Inspect job chat logs, before/after photos, and escrow transaction trail
  │     └── Action: [Release Escrow to Worker] / [Refund to Client] / [Split Payout]
  │
  └── Support & Oversight Module
        ├── View transaction ledgers, SMS dispatch logs, and platform health telemetry
        └── Direct WhatsApp / Call support integration
```

---

## 11. Known Defect & Requirement Gap Inventory

### Critical Priority (P0)
- **SEC-01 (CONFIRMED)**: Firestore rules allow public read/write access to `/providers/{providerId}` without user ownership verification (`firestore.rules` line 34-36).
- **SEC-02 (CONFIRMED)**: Serverless webhook `processMoMoWebhook` (`functions/src/index.ts` line 63) lacks HMAC signature verification.
- **SEC-03 (NEW REQUIREMENT)**: Firebase Storage security rules missing for private KYC document buckets.
- **TEST-01 (CONFIRMED)**: Automated test suite fails build execution due to `ExampleRobolectricTest` string assertion mismatch ("My Application" vs "Skills Exchange") and `GreetingScreenshotTest` missing class references.
- **AI-01 (CONFIRMED)**: Gemini API calls specify non-existent model name `gemini-3.5-flash` in `GeminiAudioTranscriptionService.kt` and `GeminiMapsGroundingService.kt`.

### High Priority (P1)
- **ROLE-01 (NEW REQUIREMENT)**: Account role model missing unified Client/Worker schema with mode switching and verification gating.
- **KYC-01 (NEW REQUIREMENT)**: Missing 2-guarantor data model and National ID photo upload pipeline.
- **KYC-02 (NEW REQUIREMENT)**: Missing verification state machine (`UNDER_REVIEW`, `CHANGES_REQUIRED`, `APPROVED`) and 48h SLA tracking timestamps.
- **TRAIN-01 (NEW REQUIREMENT)**: Missing 2-3 day training tracking and admin activation gate.
- **ADMIN-01 (NEW REQUIREMENT)**: Administrative web dashboard architecture missing for verification queue management.
- **SKILL-01 (NEW REQUIREMENT)**: 125-service taxonomy data catalog and related-skill validation logic missing.
- **AUTH-01 (CONFIRMED)**: Phone authentication and Google Sign-In are simulated with local state flags.
- **PAY-01 (CONFIRMED)**: Mobile Money collection relies on coroutine `delay()` calls instead of live gateway API.
- **MAP-01 (CONFIRMED)**: Map view in `InteractiveMapView.kt` uses custom Canvas drawing instead of Google Maps SDK.

---

## 12. Implementation Phases Overview

The project backlog is organized into **15 Logical Implementation Phases**:
1. Phase 1: Baseline, Infrastructure & Test Stabilization
2. Phase 2: Security, Document Storage & Database Rule Hardening
3. Phase 3: Unified Account Architecture, Roles & Mode Switching
4. Phase 4: Service Taxonomy & Skill Catalog System (125 Services)
5. Phase 5: Client Onboarding & Location Management
6. Phase 6: Worker KYC Verification & Guarantor Engine (2 Guarantors + Document Upload)
7. Phase 7: Verification Lifecycle, Changes-Required & 48h SLA Tracking
8. Phase 8: Worker Training Tracking & Activation Gating
9. Phase 9: Admin Operations Dashboard & Verification Queue
10. Phase 10: Core Marketplace, Quotes & Job Types (One-Time & Recurring)
11. Phase 11: Mobile Money Payments & Escrow Engine
12. Phase 12: Location Privacy, Google Maps & AI Services
13. Phase 13: Communication, Safety & Doorstep Safeguards
14. Phase 14: Offline Synchronization & Reliability
15. Phase 15: Production QA & Release Readiness

---

## 13. Future Requirements & Post-Launch Enhancements

**Status: FUTURE / PLANNED / NOT IMPLEMENTED**

The following requirements are documented for future implementation. They are NOT yet implemented and should not be considered part of the current production-ready codebase.

---

### 13.1 Job Payment Fees / Platform Deductions (PAY-005)

For every successfully completed job:

- **Worker-side commission**: 5%
- **App/platform fee**: 5%
- **Total platform deductions**: 10% when both are charged from the same transaction.

The two 5% charges must remain conceptually distinct in the requirements and future UI/business logic.

The eventual implementation must clearly show:
- Job/payment amount
- 5% commission
- 5% app/platform fee
- Total deductions
- Resulting amount received by the relevant party

**Open Questions:**
- How exactly will the 5% commission and 5% app fee be calculated/charged in edge cases such as refunds, cancellations, partial payments, or disputes?

---

### 13.2 First-Time Worker Registration Fee (REG-001)

A first-time worker registration requires:

- **UGX 20,000 registration fee.**

The worker registration UI must clearly communicate that the applicant should prepare/pay UGX 20,000 for registration.

**IMPORTANT:** The exact internal breakdown/purpose of the UGX 20,000 has NOT yet been specified. Do not invent a breakdown.

**Open Questions:**
- What exactly does the UGX 20,000 worker registration fee cover?
- What exact payment mechanism will be used for the UGX 20,000 registration fee?

---

### 13.3 Worker Registration Guarantor Verification (REG-002)

Worker registration must include **two guarantors**.

Each guarantor must provide:
- Full name
- NIN number
- Contact number
- Guarantor photograph
- Photograph showing the worker together with that guarantor

The second guarantor must provide the same information.

There must also be a **group photograph** containing:
- Worker
- Guarantor 1
- Guarantor 2

The worker-registration flow should eventually be organized into clear steps so the applicant can complete these requirements progressively.

**Note:** This augments but does not replace the existing KYC-003 two-guarantor data model (which covers National ID uploads).

---

### 13.4 Worker Backup Contact (REG-003)

Worker registration/profile must contain:
- Primary contact number
- Backup contact number

The backup number is intended to provide an alternative way to reach the worker when the primary contact is unavailable.

**IMPORTANT:** Do not invent whether the backup number must belong to the worker or another person. That detail remains to be clarified later.

**Open Questions:**
- Should the backup contact number belong to the worker or an alternate/emergency contact person?

---

### 13.5 Worker Profile Photo (REG-004)

During worker registration/profile setup:
- The worker selects a profile picture.
- This is the picture that clients will see when viewing the worker.

---

### 13.6 Worker Location Using Device GPS (LOC-005)

Remove the old concept of manually relying on outdated district/location fields such as "Sunside/Sun-side district."

The intended future behavior is:
- Worker enables location/GPS permission.
- The app obtains the worker's device location.
- The worker's location can be used for relevant Skills Exchange functionality.

**IMPORTANT:**
- The implementation must respect Android location permissions and device accuracy limitations.
- Do not claim that GPS is always perfectly exact.

---

### 13.7 Google Maps / Map Usage Minimization (MAP-002)

The general map should **not** be permanently displayed throughout the normal app experience.

Maps should be used only when relevant to an active client-worker interaction, particularly when:
- A client accepts/hires a worker.
- There is an active job.
- Location/map information is actually needed for that job.

This requirement is intended to minimize unnecessary Google Maps usage/cost.

---
### 13.8 Google Maps Fallback for the Active-Job Screen (MAP-003)

**Status: FUTURE / PLANNED / NOT IMPLEMENTED** (documentation only — do not implement now).

Google Maps must **NOT** be a hard dependency for the Skills Exchange job lifecycle. Maps may only
appear on the **active-job screen** — that is, only after a client has accepted/hired a worker and a
job is active. Maps must not appear during ordinary worker browsing or normal app navigation.

If Maps is unavailable for **ANY** reason — missing API key, billing not enabled, SDK initialization
failure, network failure, location/map load failure, invalid configuration, location permission
denied, or any other Maps-related failure — the app must **NOT** crash, freeze, or show an infinite
loading screen. Instead the active-job screen must fall back gracefully to a clear message such as
**"Map location is currently unavailable."** and must provide appropriate actions:

- **Contact Worker** — reusing the project's existing **COMM-001** masked-communication /
  Firestore real-time chat mechanism. This must **not** expose a worker's private phone number, and
  no second communication system may be created if COMM-001 already exists.
- **Continue Without Map**.

Worker GPS location collection must remain **independent** from Google Maps.

Treat Google Maps as a **visualization layer only**. All of the following must remain fully
functional even when Maps is unavailable: accepting a job, starting a job, updating job status,
worker acceptance, client/worker communication, arrival PIN, job completion, notifications, reviews,
payment/escrow, and the dispute flow.

**Acceptance criteria to be satisfied when this is eventually implemented:**

1. Maps are NOT shown during ordinary worker browsing or normal app navigation.
2. Maps are only attempted when a job is active (client has accepted/hired a worker).
3. When Maps fails for any reason the app does NOT crash, freeze, or show an infinite loading screen.
4. Fallback UI displays a clear message (e.g. "Map location is currently unavailable.").
5. Fallback UI provides a Contact Worker action that reuses the existing COMM-001
   masked-chat/phone-masking mechanism.
6. Fallback UI provides a Continue Without Map action.
7. Worker GPS location collection is independent from the Google Maps SDK.
8. All existing job lifecycle features (acceptance, start, status, arrival PIN, completion, payment,
   dispute) work without Maps.
9. API key is managed via secure configuration (manifestPlaceholders / .env), never hardcoded in
   source.
10. Maps initialization is wrapped in a try/catch or equivalent so any SDK exception produces the
    fallback, not a crash.
11. Location permission denial produces the fallback, not a crash or infinite loading.
12. A feature flag or configuration abstraction allows Maps to be enabled/disabled without
    redesigning the active-job system.
13. No new communication system is created if COMM-001 masked-chat already exists.

**IMPORTANT:** This is a future requirement. Nothing above has been implemented. MAP-003 depends on
MAP-002 (the maps-usage-minimization strategy) being understood first.

**Open Questions:**
- MAP-001 already has an unguarded `map.onCreate()` that crashes without an API key (device defect
  D1: `IllegalStateException: API key not found` at `InteractiveMapView.kt:98`). How should that
  crash be reconciled with the active-job screen design?
- Is the COMM-001 masked-chat mechanism already reachable from the active-job context?

---

### 13.9 Community Feature Removal (COMM-002)

The Community feature should eventually be removed permanently.

When implementation begins, the agent must audit and safely remove all relevant:
- Navigation entries
- Screens
- Routes
- ViewModels
- Repository logic
- Entities/data structures where applicable
- PRD/requirements references
- Other obsolete Community-related code

**IMPORTANT:** Do not remove Community during this documentation task.

**Related existing code:** NeighborhoodFeedView, NeighborhoodPostEntity, and related MainScreen navigation.

---

### 13.10 Worker Dashboard Security / Access Control (SEC-009)

A normal Client must NOT be able to access the Worker dashboard.

Worker dashboard access must require that the account:
- Has been registered as a Worker
- Has completed the required worker verification/KYC process
- Has been approved/activated by administrators

**IMPORTANT:** UI hiding alone is NOT sufficient. The eventual implementation must enforce this at the appropriate business/security/data-access layer as well as the UI.

Existing worker KYC, training, approval, and activation requirements must be preserved.

---

### 13.11 Liability Disclaimer (LEGAL-001)

Skills Exchange should include a short liability disclaimer communicating that Skills Exchange is not responsible for work performed by workers or damages caused by workers/clients, subject to applicable law.

The requirement should eventually appear appropriately in:
- Terms & Conditions
- Relevant registration/onboarding areas where appropriate
- Potentially relevant job acceptance/transaction flows where appropriate

**IMPORTANT:**
- Do not invent legal guarantees or claim that the disclaimer eliminates legal responsibility.
- The final legal wording should be reviewed for applicable Ugandan legal requirements before public launch.

**Open Questions:**
- What exact legal wording should be used after Ugandan legal review?

---

### 13.12 Privacy Policy / Terms & Conditions / User Consent (LEGAL-002)

The project will eventually need proper:
- Privacy Policy
- Terms & Conditions
- User consent/acceptance tracking

The legal/product requirements should cover relevant areas such as:
- Data collection and use
- Location data
- Worker KYC/NIN information
- Mobile money/payment/escrow information
- User rights
- Account deletion
- Marketplace rules
- Worker verification
- Jobs
- Messaging
- Ratings
- Disputes
- Prohibited activities
- Account termination
- Liability

**IMPORTANT:**
- Do not invent legal requirements that have not been decided.
- Do not implement these pages or consent tracking now.

---

### 13.13 Preserve Current Authentication/Onboarding Requirements (AUTH-012)

**Do NOT replace or undo the existing authentication/onboarding requirements.**

The existing intended flow remains:

**NEW USER:**
Role Selection → Authentication → Role-specific onboarding → Main App

**EXISTING USER:**
Restore session → Main App

Worker activation/KYC/approval gates must remain intact.

These new requirements are additions to the existing project, not replacements.

---

### Open Questions Summary

**2026-09-17 Feature Connection Audit & 2026-10-15 Multi-Perspective Platform Audit:** Comprehensive audits identified disconnects between backend capabilities and user journey executability. See AUDIT_THREE_PERSONAS.md, AGENT_STATE.md, and TASKS.md for full registers.

The following questions remain unresolved and should be answered before implementation:

1. **What exactly does the UGX 20,000 worker registration fee cover?**
2. **Is the worker backup contact number another number belonging to the worker, or an alternate/emergency contact person?**
3. **What exact payment mechanism will be used for the UGX 20,000 registration fee?**
4. **How exactly will the 5% commission and 5% app fee be calculated/charged in edge cases such as refunds, cancellations, partial payments, or disputes?**
5. **What exact legal wording should be used after Ugandan legal review?**
6. **MAP-003:** How should the unguarded `map.onCreate()` crash from MAP-001 (device defect D1) be reconciled with the active-job screen design?
7. **MAP-003:** Is the COMM-001 masked-chat mechanism already reachable from the active-job context?
8. **Worker Bidding UX:** When a worker taps "Submit Quote", should the UI present a bottom sheet modal capturing hourly rate, estimated hours, and notes, and update the existing JobQuoteEntity immediately?
9. **Cancellation Policy:** When a client cancels a job after worker selection but before work starts, should there be an automated callout fee deduction ($5.00/UGX equivalent) to compensate the worker for travel?
10. **Admin Portal Architecture:** Should the Admin Operations Console be deployed as an internal Android debug drawer/screen for on-the-go ops or as a separate web portal (Next.js/React) connecting to existing Cloud Functions?

---

## 14. Implementation Phases Overview

The project backlog is organized into **17 Logical Implementation Phases**:
1. Phase 1: Baseline, Infrastructure & Test Stabilization
2. Phase 2: Security, Document Storage & Database Rule Hardening
3. Phase 3: Unified Account Architecture, Roles & Mode Switching
4. Phase 4: Service Taxonomy & Skill Catalog System (125 Services)
5. Phase 5: Client Onboarding & Location Management
6. Phase 6: Worker KYC Verification & Guarantor Engine (2 Guarantors + Document Upload)
7. Phase 7: Verification Lifecycle, Changes-Required & 48h SLA Tracking
8. Phase 8: Worker Training Tracking & Activation Gating
9. Phase 9: Admin Operations Dashboard & Verification Queue
10. Phase 10: Core Marketplace, Quotes & Job Types (One-Time & Recurring)
11. Phase 11: Mobile Money Payments & Escrow Engine
12. Phase 12: Location Privacy, Google Maps & AI Services
13. Phase 13: Communication, Safety & Doorstep Safeguards
14. Phase 14: Offline Synchronization & Reliability
15. Phase 15: Production QA & Release Readiness
16. Phase 16: Authentication Hardening & Session Management
17. Phase 17: Future Requirements & Post-Launch Enhancements

---

## 15. Multi-Perspective Operational Requirements (Worker, Client, Administrator)

*(Derived from the 2026-10-15 Codebase & Persona Audit)*

### 15.1 Worker Experience Requirements
1. **Quote Proposal Submission UI**:
   - Tapping "Submit Quote" on `WorkerJobFeedCard` must open a dedicated `QuoteSubmissionSheet` Composable.
   - Form fields: `proposedRate` (number, UGX/hr), `proposedHours` (number), `notes` (text, optional).
   - Wires to `SkillsExchangeViewModel.submitQuote(jobId, workerId, proposedRate, proposedHours, notes)`.
   - Prevents duplicate quote submissions by checking existing quotes for `(jobId, workerId)`.
2. **Mobile Money Wallet Cashout & Disbursement**:
   - In `EscrowWalletSheet`, provide an actionable "Withdraw to Mobile Money" CTA when `releasedBalance > 0`.
   - Confirmation dialog displaying destination phone number (defaulting to profile MoMo number), telecom provider (MTN/Airtel), platform deduction breakdown, and net amount.
   - Triggers backend disbursement callable with fail-closed state tracking.
3. **Turn-by-Turn Job Site Navigation Intent**:
   - Active job card on the worker dashboard must render a "Navigate to Site" button when a job is `ACCEPTED` or `WORKER_EN_ROUTE`.
   - Launches Android `geo:` intent or Google Maps URI with the client's destination coordinates/district.
4. **Worker Dispute Defense & Counter-Evidence**:
   - When a job status transitions to `DISPUTED`, the worker view in `EscrowAndProofSheet` must show a "Submit Dispute Defense" section.
   - Allows uploading counter-evidence photos (before/after work, receipts) and explanatory statement.
5. **Portfolio & Tiered Pricing Customization**:
   - Worker profile settings must support showcasing up to 5 portfolio photos and configuring standard vs. emergency callout rates.

### 15.2 Client Experience Requirements
1. **Job Posting Photo & Video Attachments**:
   - `QuickRequestDrawer` must include an optional media picker (up to 3 photos) before posting a job.
   - Uploads photos to Firebase Storage under `jobs/{jobId}/initial_photos/` and persists URLs to `JobPostEntity`.
2. **Specific Date & Time Appointment Scheduling**:
   - Replace the binary `URGENT` vs `SCHEDULED` toggle with a scheduled date/time picker when `SCHEDULED` is selected.
   - Stores `scheduledDate` (epoch ms) and displays clear appointment windows (e.g., "Tomorrow, 2:00 PM - 4:00 PM").
3. **Street Address, Landmark, & Navigation Notes**:
   - Expand `JobPostEntity` and `QuickRequestDrawer` to accept `streetAddress`, `apartmentSuite`, and `landmarkDirections` in addition to district.
   - Displays doorstep landmark instructions to the assigned worker upon job acceptance.
4. **Self-Service Job Cancellation & Refund Controls**:
   - In `JobPipelineKanban` and `MainScreen`, active jobs in `POSTED`, `QUOTING`, or `ACCEPTED` states must display a "Cancel Job" button.
   - If escrow funds are already locked, triggers the backend refund flow to return funds to the client's MoMo account.
5. **Real-Time Worker Arrival & En-Route Tracking**:
   - Surface `WORKER_EN_ROUTE` status on the client's job tracking screen with estimated arrival status.
6. **Re-Hire Favorites & Voluntary Gratuity**:
   - Add a "Re-Hire Specialist" shortcut on `JobHistorySheet`.
   - Provide an optional tip field (e.g. 5%, 10%, or custom UGX) during final escrow release.

### 15.3 Platform Administrator Experience Requirements
1. **Administrative Operations Dashboard UI**:
   - Implement an operational console (accessible via authorized admin role) that connects to `getVerificationQueue`, `reviewWorkerApplication`, and `setWorkerActivationStatus`.
   - Enables operators to view applicant details, inspect high-resolution National ID photos, check guarantor verification cards, and toggle approval/rejection/changes-required.
2. **Dispute Arbitration Console**:
   - Interface for reviewing disputed jobs, inspecting client complaint vs. worker defense, viewing uploaded before/after photos and masked chat logs.
   - Action controls to execute `resolveDispute` with split, full refund, or worker release decisions.
3. **Live SOS & Incident Response Desk**:
   - Real-time incident monitor for any job where `isSosTriggered == true`.
   - Displays worker & client contact numbers, GPS coordinates, doorstep address, and rapid emergency escalation options (SMS dispatch / police notification).
4. **Financial Reconciliation & Escrow Ledger**:
   - Financial audit view reconciling telecom transaction IDs, pending escrow float, platform commission balances, and completed payouts.
5. **Dynamic Remote Platform Configuration**:
   - Migrate platform fee percentage (currently hardcoded 10%) and callout fees ($5.00) to Firestore remote config documents.

---

## 16. Independent Review Programme Addendum (2026-11-19)

This addendum does not replace or amend the requirements above. It records the review-only artefacts created on 2026-11-19 and the requirements that evidence shows are **not yet demonstrably met**. Each item must be verified by the named reviewer before it may be treated as delivered.

**Review artefacts:** `ASSESSMENT_REPORT.md`, `REVIEWER_REGISTER.md`, `REVIEW_PLAN.md`, `PRODUCTION_READINESS.md`, `reviewers/*.md`, and PRD phase 18 (`RVW-001`…`RVW-015`).

**Requirements requiring verification (do not assume implemented):**

| Requirement (section) | Observed state | Reviewer |
|---|---|---|
| §5 Worker KYC document privacy and retention | Private storage rules exist; erasure of Storage objects on account deletion is absent | R09 |
| §5/§15.3 Admin review of National ID and guarantor evidence | In-app admin sheets and admin-gated callables exist; no claim-provisioning runbook; no web portal | R06/R15 |
| §9 Escrow lock and verified payout | Server collection/confirmation core exists; release/payout paths have no client caller; refunds absent | R08 |
| §9 Doorstep 4-digit PIN | Plaintext Room field with default `4829`, displayed in clear text, verification accepts a client-supplied expected value and has no attempt limit | R10/R07 |
| §9 Emergency SOS alerts | Incident record and authorization exist; SMS dispatch is a logging stub returning "SENT" | R10 |
| §10 Admin web dashboard | Not present; only in-app operator sheets | R15 |
| §14 Notifications (job/quote/payment) | Client receive path exists; no server-side FCM send found | R03 |
| §15.2 Scheduling, address details, cancellation/refund, re-hire/gratuity | Partial or absent; see TASKS/prd notes | R02/R15 |
| §15.3 Live SOS desk, financial reconciliation, remote fee configuration | Not implemented | R10/R08/R15 |
| Privacy notice, consent records, retention schedule | UI copy only; no DPIA/retention artefacts | R09 |
| Localisation for Uganda/Kenya/Tanzania/Rwanda | English-only inline strings | R12 |
| Accessibility (TalkBack, contrast, touch targets) | Not audited; 59 null content descriptions | R12 |

**Status rule:** until the relevant reviewer report is accepted, these requirements are `UNVERIFIED`, not complete. See `PRODUCTION_READINESS.md` for the release gate.

## 17. Remediation Requirements derived from Review Findings (2026-11-19)

This section adds requirements; it does not replace or relax any requirement above. Each `REM` task implements exactly one consolidated finding (`MR`) and carries its own tests, acceptance criteria and completion evidence in `IMPLEMENTATION_ROADMAP.md` §4. All tasks are `NOT_STARTED`; nothing here is delivered.

| Requirement group | Tasks | Requirement statement | Status |
|---|---|---|---|
| R01 | REM-001, REM-003, REM-004 | Compilation, modular boundaries and removal of dead/duplicate configuration | NOT_STARTED |
| R02 | REM-054, REM-073 | Android lifecycle, process-death survival, permissions and onboarding wiring | NOT_STARTED |
| R03 | REM-002, REM-009, REM-021 | Backend build integrity, deploy gating, webhook authentication and reconciliation | IMPLEMENTED (REM-002 complete; REM-009 predeploy done, deploys unverified; webhook auth/re-query in place, webhook throttle open) |
| R04 | REM-046, REM-047, REM-048, REM-049, REM-050 | Schema export, tested migrations, exact money representation and bounded reads | IMPLEMENTED (schemas exported, all migrations executed in CI, wallet money in minor units; job-pricing fields still Double; on-device upgrade unverified) |
| R05 | REM-051, REM-033, REM-052, REM-053 | Server-held integration secrets, non-fabricated AI output, contract tests and real channels | IMPLEMENTED (REM-051/033/052 complete: Gemini and Africa's Talking keys server-side, no fabricated AI output, Airtel/card no longer offered, suspend-only client, typed contracts for all 17 callables; REM-053 partial — contract tests exist, no real Maps key can be supplied here; deploy and live provider traffic unverified) |
| R06 | REM-022, REM-023, REM-024, REM-025 | Firebase-backed identity, admin claim lifecycle, re-authentication and account linking | IMPLEMENTED (REM-024 complete: server-authoritative deletion that purges Firestore and Cloud Storage before the Auth user, counted progress, in-app re-authentication, push-token cleanup; REM-025 code complete (linkWithCredential, collision shown as a conflict) but its E2E link log needs a Firebase project, so one-uid-per-person is unverified; REM-022/023 retain their earlier verification-pending state — no project, no deploy) |
| R07 | REM-010, REM-011, REM-012, REM-013, REM-014, REM-015, REM-016, REM-017, REM-018, REM-019, REM-020 | Closed public reads, least-privilege rules, bound storage writes, backups and App Check | NOT_STARTED |
| R08 | REM-026, REM-027, REM-028, REM-029, REM-030, REM-031, REM-032 | Server-authoritative release, refund, fee, escrow transitions, ledger and reconciliation | NOT_STARTED |
| R09 | REM-041, REM-037, REM-042, REM-043, REM-044, REM-045 | Atomic scoped erasure, truthful notice, retention, consent and NIN minimisation | NOT_STARTED |
| R10 | REM-034, REM-035, REM-036, REM-038, REM-039, REM-040 | Truthful SOS dispatch, secret PIN, moderator-visible reports and audited escalation | NOT_STARTED |
| R11 | REM-064, REM-065, REM-066 | Executable test suites, rules/migration/E2E coverage and reproducible evidence | NOT_STARTED |
| R12 | REM-055, REM-056, REM-057, REM-058 | Labelled controls, readable typography, localisation and consistent formatting | NOT_STARTED |
| R13 | REM-059, REM-060, REM-061, REM-062, REM-063 | Telemetry, offline replay, push, bounded resources and SLOs | NOT_STARTED |
| R14 | REM-005, REM-006, REM-007, REM-008 | CI, portable builds, signed releases, dependency hygiene and deploy safety | NOT_STARTED |
| R15 | REM-067, REM-068, REM-069, REM-070, REM-071, REM-072 | Evidence-backed status, portable documentation and requirement traceability | NOT_STARTED |

**Status rule:** a `REM` task may move to `COMPLETED` only when its roadmap acceptance criteria are met and completion evidence is attached. Runtime-only evidence that cannot be produced must leave the task at `VERIFICATION_PENDING`.

---

## 18. Remediation Implementation Status (2026-11-19)

This section records implementation evidence only; it does not change any requirement above and
does not declare the product production-ready.

| Task | Requirement area | Implementation | Verification |
|---|---|---|---|
| REM-011 / REM-022 | Phone identity (§3, §13.3) | OTP runs in `sendOtp`/`verifyOtp` callables; successful verification mints a Firebase custom token; no Africa's Talking credential in the client or repo | Server policy unit-tested (128 backend tests); real-project signup test and key revocation outstanding |
| REM-012–015 | Access control (§8, §10) | Single SOS rule with job guard; notifications/MoMo ledger client-create denied; storage authorized from the job document; quotes limited to participants | Requires rules emulator run |
| REM-016 | Privacy (§8) | Cloud backup and device transfer disabled/excluded for all data domains | Requires device restore test |
| REM-006 | Build reproducibility | No machine-local paths; one Java 11 toolchain; executable wrapper | Requires Gradle build |
| REM-051 | AI transcription (§9) | Server-side callable, validated output, explicit client error | Requires deployment and Android build |
| REM-069 / REM-072 | Documentation (§17) | Relative links; document precedence map | Checkers pass locally |

Open items from this slice: REM-023–025 (identity/admin), REM-026–032 (money), REM-034–040
(safety), REM-005/007/008 (CI, signing, dependency advisories) and the Android compilation task
REM-001, which still needs a JDK and Android SDK to verify.

### 18.1 Third wave-0 increment

- **REM-021:** callback authentication (shared secret header `x-momo-webhook-secret` or
  `x-callback-secret`), five-minute freshness window and per-reference rate limit. Configuration:
  `MOMO_WEBHOOK_SECRET` may hold a comma-separated rotation list; without it callbacks are refused.
- **REM-039:** guarantor reuse flags are returned by `getPendingVerificationQueue` under
  `riskFlags`; the admin UI still has to display them.
- **REM-070:** `docs/traceability.md` maps 18 requirement IDs to code and tests; four requirements
  remain without any automated test (`REQ-ACC-02`, `REQ-LOC-01`, `REQ-TRAIN-01`, `REQ-SEC-01`).
- **REM-005:** CI covers backend build/tests, documentation checkers and secret scanning; Android
  and rules jobs are not yet part of the pipeline.

### 18.2 Fourth wave-0 increment

- **REM-044:** account consent is recorded server-side with a dated policy version and three
  grants; the owner can read but never modify the receipt. `exportMyData` provides the DSAR export
  and strips third-party guarantor identifiers. Processors and the 30-day SLA are published in
  `docs/privacy/`. In-app consent capture and export UI remain outstanding.
- **REM-049:** Room list queries are capped and a static checker prevents new unbounded queries.

### 18.3 Wave 1a: emergency dispatch

- **REM-034:** SOS events are delivered by SMS through Africa's Talking to the user's emergency
  contact and to `SOS_DISPATCH_NUMBERS`; the incident records the real outcome. Required
  configuration: `AFRICASTALKING_API_KEY`, `AFRICASTALKING_USERNAME`, optional
  `AFRICASTALKING_SENDER_ID`, and `SOS_DISPATCH_NUMBERS`.
- **REM-035:** the same incident pushes to the FCM topic `sos_dispatch`.
- Neither channel is verified against a live provider in this environment.

### 18.4 Wave 1b: operator access

- **REM-023:** the admin UI gate is server-verified via `getAdminAccess`; the `admin` custom claim
  is granted/revoked only by `scripts/grant-admin.js`, which preserves other claims, revokes
  refresh tokens and writes an immutable `admin_audit` record. Requires a Firebase Admin service
  account and `ADMIN_ACTOR` to run.

### 18.5 Wave 1c: session security

- **REM-024:** destructive owner deletes require authentication within the last five minutes
  (`auth_time`), and logout revokes refresh tokens server-side. The client blocks account deletion
  on a stale session. Verification against a live project is outstanding.

### 18.6 Wave 1d: README accuracy

- **REM-071:** the README no longer claims an Airtel Money integration or a web admin dashboard; the
  Android console is described as server-claim gated. A checker enforces the caveat and links.

### 18.7 Wave 1e: money path

- **REM-026:** escrow release is invoked by the client completion flow, but the server decides the
  amount and wallet from its own records and pays once per job.
- **REM-027:** refunds are provider requests against a settled collection; a failed request is never
  presented as a refund. Verification against the MTN sandbox is outstanding.

### 18.8 Wave 1f: payout and pricing integrity

- **REM-028:** the device no longer fabricates a payout or a DISBURSED status; it reads the server's
  payout record. Provider confirmation remains unverified here.
- **REM-029:** the platform fee and grand total are computed and enforced server-side before any
  collection; the client keeps a display-only estimate.

### 18.9 Prompt-4 R01: architecture remediation

- **Single gateway:** the ViewModel owns one `FirebaseService`, injected into the repository; UI
  identity reads use `currentAuthenticatedUid()`.
- **Guards:** `scripts/check_architecture_guards.mjs` fails if a second gateway, a UI bypass or the
  duplicate root manifest returns.
- **Decomposition:** a five-slice roadmap is recorded; the split itself is still outstanding.
- **Limitation:** Android compilation and unit tests remain unexecuted here, so R01 is implemented
  only in part and is not verified.

### 18.10 Prompt-4 R01: build restored and reviewer completed

- The Android client now compiles and its JVM suite passes (355/355) on a GitHub runner; the
  remediation sandbox cannot reach Google Maven, so the workflow is the evidence source.
- 186 compile errors were fixed, including a spliced `QuickRequestDrawer.kt`, an invalid Room
  `converters` argument, a missing `JobPostEntity.apartmentSuite` column and duplicated onboarding
  state.
- The Java toolchain moved from 11 to 17 (compile) with a Java 21 test launcher, because Robolectric
  cannot run on Java 11.
- R01's decomposition roadmap is recorded; the code split remains outstanding work for later slices.

### 18.11 Prompt-4 R02: Android client completed

- Escrow release is server-confirmed; the client can no longer write `RELEASED_TO_WORKER` or show a
  payout success on its own. Cash settlement is recorded honestly as an acknowledgement.
- Money and SOS actions are single-flight; onboarding state survives recreation; SOS no longer
  fabricates a location; identity surfaces set `FLAG_SECURE`.
- Compose UI tests now cover onboarding (incl. state restoration), job posting, quoting and escrow
  gating and run in CI on the JVM.
