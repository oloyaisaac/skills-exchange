// REM-039: guarantor collusion signals.
//
// A worker's two guarantors are supposed to be two different people with independent identities.
// Nothing stopped the same person (or the same NIN/phone pair) from guaranteeing many applications.
// This module derives review flags from the guarantor records that are already on file. It is pure
// and holds nothing: no new copy of a NIN or phone number is persisted.

export const RISK_DUPLICATE_GUARANTOR = "DUPLICATE_GUARANTOR";
export const RISK_REUSED_NIN = "REUSED_NIN";
export const RISK_REUSED_PHONE = "REUSED_PHONE";

export type GuarantorRiskFlag =
  | typeof RISK_DUPLICATE_GUARANTOR
  | typeof RISK_REUSED_NIN
  | typeof RISK_REUSED_PHONE;

export interface GuarantorIdentityRecord {
  workerId: string;
  nationalIdNumber?: unknown;
  phoneNumber?: unknown;
}

export interface WorkerRiskAssessment {
  flags: GuarantorRiskFlag[];
  /** How many other workers share a NIN with this worker (0 when unique). */
  sharedNinWorkers: number;
  /** How many other workers share a phone number with this worker. */
  sharedPhoneWorkers: number;
  guarantorCount: number;
}

/** Uppercase and strip separators so `UG-123 456` and `ug123456` compare equal. */
export function normaliseIdentity(value: unknown): string {
  if (typeof value !== "string") return "";
  return value.toUpperCase().replace(/[^A-Z0-9]/g, "");
}

/**
 * Reduce a Ugandan MSISDN to its nine-digit subscriber part so `+256772000001`, `256772000001`
 * and `0772 000 001` compare equal.
 */
export function normalisePhone(value: unknown): string {
  if (typeof value !== "string") return "";
  const digits = value.replace(/\D/g, "");
  if (digits.startsWith("256") && digits.length === 12) return digits.slice(3);
  if (digits.startsWith("0") && digits.length === 10) return digits.slice(1);
  return digits;
}

export function assessGuarantorRisk(
  records: GuarantorIdentityRecord[]
): Record<string, WorkerRiskAssessment> {
  const byWorker = new Map<string, GuarantorIdentityRecord[]>();
  for (const record of records) {
    if (!record || typeof record.workerId !== "string" || record.workerId.length === 0) continue;
    const list = byWorker.get(record.workerId) ?? [];
    list.push(record);
    byWorker.set(record.workerId, list);
  }

  const workersByNin = new Map<string, Set<string>>();
  const workersByPhone = new Map<string, Set<string>>();
  const remember = (index: Map<string, Set<string>>, key: string, workerId: string): void => {
    if (!key) return;
    const set = index.get(key) ?? new Set<string>();
    set.add(workerId);
    index.set(key, set);
  };
  for (const [workerId, guarantors] of byWorker) {
    for (const guarantor of guarantors) {
      remember(workersByNin, normaliseIdentity(guarantor.nationalIdNumber), workerId);
      remember(workersByPhone, normalisePhone(guarantor.phoneNumber), workerId);
    }
  }

  const assessments: Record<string, WorkerRiskAssessment> = Object.create(null);
  for (const [workerId, guarantors] of byWorker) {
    const flags = new Set<GuarantorRiskFlag>();
    const seenNin = new Set<string>();
    const seenPhone = new Set<string>();
    let sharedNinWorkers = 0;
    let sharedPhoneWorkers = 0;

    for (const guarantor of guarantors) {
      const nin = normaliseIdentity(guarantor.nationalIdNumber);
      const phone = normalisePhone(guarantor.phoneNumber);

      if (nin) {
        if (seenNin.has(nin)) flags.add(RISK_DUPLICATE_GUARANTOR);
        seenNin.add(nin);
        const owners = workersByNin.get(nin);
        if (owners) {
          const others = owners.size - (owners.has(workerId) ? 1 : 0);
          sharedNinWorkers = Math.max(sharedNinWorkers, others);
        }
      }
      if (phone) {
        if (seenPhone.has(phone)) flags.add(RISK_DUPLICATE_GUARANTOR);
        seenPhone.add(phone);
        const owners = workersByPhone.get(phone);
        if (owners) {
          const others = owners.size - (owners.has(workerId) ? 1 : 0);
          sharedPhoneWorkers = Math.max(sharedPhoneWorkers, others);
        }
      }
    }

    if (sharedNinWorkers > 0) flags.add(RISK_REUSED_NIN);
    if (sharedPhoneWorkers > 0) flags.add(RISK_REUSED_PHONE);

    assessments[workerId] = {
      flags: Array.from(flags).sort(),
      sharedNinWorkers,
      sharedPhoneWorkers,
      guarantorCount: guarantors.length,
    };
  }

  return assessments;
}
