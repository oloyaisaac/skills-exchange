// R10-P2-03: say what actually happened with an emergency alert.
//
// The app told the person "Live GPS location & job telemetry broadcasted to local emergency contacts
// and platform security" the instant the button was pressed — before any message left the platform.
// The server-side trigger writes the real outcome to `sosIncidents.dispatch` (SENT / NO_RECIPIENTS /
// FAILED), and the client never read it back, so a user could believe help had been contacted when
// nobody was.
//
// The wording lives here, on the server, so the app cannot invent a more comforting sentence than the
// evidence supports. The app displays `message` verbatim.

export type SosDispatchState =
  | "NOT_DISPATCHED"
  | "PENDING"
  | "SENT"
  | "PARTIAL"
  | "FAILED"
  | "NO_RECIPIENTS"
  | "STALE"
  | "UNKNOWN";

/** A dispatch that has not reported back within this window is treated as unconfirmed. */
export const DISPATCH_STALE_MS = 2 * 60 * 1000;

export interface SosDispatchDescription {
  state: SosDispatchState;
  /** Shown verbatim in the app. Never claims delivery that was not reported. */
  message: string;
  recipients: number;
  delivered: number;
  attemptedAt: number | null;
  /** True when the person should be told to call emergency services themselves. */
  adviseDirectCall: boolean;
}

const DIRECT_CALL = "If you are in immediate danger, call the police or an ambulance yourself now.";

function asCount(value: unknown): number {
  const numeric = Number(value);
  return Number.isFinite(numeric) && numeric >= 0 ? Math.floor(numeric) : 0;
}

/**
 * Turn the stored dispatch record into a sentence a person can act on.
 *
 * Anything the record does not prove is reported as unconfirmed: absent data is `PENDING` while the
 * alert is fresh, `STALE` after the window, `UNKNOWN` if the record is unrecognisable.
 */
export function describeSosDispatch(dispatch: unknown, now: number): SosDispatchDescription {
  if (!dispatch || typeof dispatch !== "object") {
    return {
      state: "NOT_DISPATCHED",
      message:
        "Your alert is recorded on this device but the platform has not confirmed any message yet. " +
        DIRECT_CALL,
      recipients: 0,
      delivered: 0,
      attemptedAt: null,
      adviseDirectCall: true,
    };
  }

  const record = dispatch as Record<string, unknown>;
  const status = typeof record.status === "string" ? record.status.toUpperCase() : "";
  const recipients = asCount(record.recipients);
  const delivered = asCount(record.delivered);
  const attemptedAtRaw = Number(record.attemptedAt);
  const attemptedAt = Number.isFinite(attemptedAtRaw) && attemptedAtRaw > 0 ? attemptedAtRaw : null;

  if (status === "SENT" || (status === "PARTIAL" && delivered > 0)) {
    if (delivered > 0 && recipients > delivered) {
      return {
        state: "PARTIAL",
        message:
          `Emergency SMS reached ${delivered} of ${recipients} contacts. Some contacts were not reached — ` +
          DIRECT_CALL,
        recipients,
        delivered,
        attemptedAt,
        adviseDirectCall: true,
      };
    }
    return {
      state: "SENT",
      message: `Emergency SMS delivered to ${delivered} contact${delivered === 1 ? "" : "s"}. Operators have been alerted.`,
      recipients,
      delivered,
      attemptedAt,
      adviseDirectCall: false,
    };
  }

  if (status === "NO_RECIPIENTS") {
    return {
      state: "NO_RECIPIENTS",
      message:
        "No emergency contact is configured for your account, so no SMS was sent. Add an emergency " +
        "contact in your profile — and " + DIRECT_CALL.toLowerCase(),
      recipients: 0,
      delivered: 0,
      attemptedAt,
      adviseDirectCall: true,
    };
  }

  if (status === "FAILED") {
    return {
      state: "FAILED",
      message: `The emergency SMS could not be sent (${recipients} contact${recipients === 1 ? "" : "s"} tried). ` + DIRECT_CALL,
      recipients,
      delivered: 0,
      attemptedAt,
      adviseDirectCall: true,
    };
  }

  // No status yet: fresh is "pending", old is "the platform never confirmed".
  const age = attemptedAt === null ? null : now - attemptedAt;
  if (age === null || age < 0 || age <= DISPATCH_STALE_MS) {
    return {
      state: "PENDING",
      message: "Your alert reached the platform. Confirming that contacts were messaged…",
      recipients,
      delivered,
      attemptedAt,
      adviseDirectCall: false,
    };
  }
  if (status === "" || status === "PENDING") {
    return {
      state: "STALE",
      message:
        "The platform has not confirmed that anyone was contacted. " + DIRECT_CALL,
      recipients,
      delivered,
      attemptedAt,
      adviseDirectCall: true,
    };
  }

  return {
    state: "UNKNOWN",
    message: "The dispatch result could not be read. " + DIRECT_CALL,
    recipients,
    delivered,
    attemptedAt,
    adviseDirectCall: true,
  };
}

/** True when the alert still needs the client to keep polling for a confirmed outcome. */
export function shouldKeepChecking(description: SosDispatchDescription): boolean {
  return description.state === "PENDING" || description.state === "NOT_DISPATCHED";
}

/** The chat line the app records once the outcome is known — it never says "help is on the way". */
export function dispatchChatMessage(description: SosDispatchDescription, clock: string): string {
  switch (description.state) {
    case "SENT":
      return `Emergency dispatch: ${description.message} (${clock})`;
    case "PARTIAL":
    case "FAILED":
    case "NO_RECIPIENTS":
    case "STALE":
    case "UNKNOWN":
      return `Emergency dispatch: ${description.message} (${clock})`;
    case "PENDING":
    case "NOT_DISPATCHED":
      return `Emergency dispatch: not yet confirmed by the platform (${clock}).`;
  }
}
