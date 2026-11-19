# Data processors

REM-044: the processors below receive personal data in the course of running the service. This list
is published so consent is informed; it must be updated before a new processor is enabled.

| Processor | Purpose | Data categories | Location / transfer | Contract status |
|---|---|---|---|---|
| Google Firebase (Authentication, Firestore, Cloud Storage, Cloud Functions, FCM) | Account identity, application data, document storage, notifications | Phone number, UID, profile, jobs, messages, KYC documents, device tokens | Google Cloud regions configured for the project | Google Cloud Data Processing Addendum |
| Africa's Talking | OTP and SMS delivery | Phone number, one-time code | Uganda / Kenya | Processor agreement required before production |
| MTN MoMo | Mobile money collection, payout and settlement | Phone number, amount, transaction reference, job reference | Uganda | MTN merchant/API agreement |
| Google Gemini API | Voice transcription and map grounding | Audio recording, short text prompt | Google Cloud (API) | Google API terms; no training on submitted content per paid tier |
| Google Maps Platform | Address search and map display | Approximate location, search text | Google Cloud | Google Maps Platform terms |

## Not processors

- Reviewers and administrators are staff acting under the controller, not separate processors.
- Guarantors are data subjects in their own right; their NIN and photographs are not shared with
  any processor beyond private Cloud Storage.

## Rules

1. A processor must not be enabled in production until this table names it, its data categories and
   its transfer basis.
2. Secrets are held in Cloud Functions environment/Secret Manager, never in the client.
3. Data-subject requests are handled per `DSAR.md`.
