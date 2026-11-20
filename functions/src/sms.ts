// REM-034: real SMS delivery through Africa's Talking.
//
// The previous helper only logged and returned `true`, so SOS and job alerts were recorded as sent
// when nothing left the server. This module builds and parses the provider call; the HTTP transport
// is injectable so failure handling is testable without credits.

export const SMS_ENDPOINT = "https://api.africastalking.com/version1/messaging";
export const SMS_MAX_RECIPIENTS = 20;

export interface SmsConfig {
  apiKey: string;
  username: string;
  senderId?: string;
}

export interface SmsSendResult {
  ok: boolean;
  messageId?: string;
  error?: string;
}

export interface SmsHttpResponse {
  status: number;
  data: unknown;
}

export type SmsTransport = (
  url: string,
  body: Record<string, unknown>,
  headers: Record<string, string>
) => Promise<SmsHttpResponse>;

export function loadSmsConfig(env: Record<string, string | undefined>): SmsConfig | null {
  const apiKey = (env.AFRICASTALKING_API_KEY || "").trim();
  const username = (env.AFRICASTALKING_USERNAME || "").trim();
  if (apiKey.length < 16 || username.length === 0) return null;
  const senderId = (env.AFRICASTALKING_SENDER_ID || "").trim();
  return { apiKey, username, ...(senderId ? { senderId } : {}) };
}

/** Accept a comma/space separated string or an array, keep plausible MSISDNs, de-duplicate. */
export function normalisePhoneList(input: unknown, max = SMS_MAX_RECIPIENTS): string[] {
  const values = Array.isArray(input)
    ? input
    : typeof input === "string"
      ? input.split(/[,\s;]+/)
      : [];
  const seen = new Set<string>();
  const phones: string[] = [];
  for (const value of values) {
    if (typeof value !== "string") continue;
    const phone = value.trim().replace(/[\s()-]/g, "");
    if (!/^\+?[0-9]{9,15}$/.test(phone)) continue;
    if (seen.has(phone)) continue;
    seen.add(phone);
    phones.push(phone);
    if (phones.length >= max) break;
  }
  return phones;
}

export function buildSmsBody(config: SmsConfig, to: string[], message: string): Record<string, unknown> {
  return {
    username: config.username,
    to,
    message,
    ...(config.senderId ? { from: config.senderId } : {}),
  };
}

/** Africa's Talking returns `SMSMessageData.Recipients[]` with a per-recipient status. */
export function parseSmsResponse(status: number, body: unknown): SmsSendResult {
  if (status < 200 || status >= 300) {
    return { ok: false, error: `provider status ${status}` };
  }
  const root = (body ?? {}) as Record<string, unknown>;
  const data = (root.SMSMessageData ?? root.smsMessageData ?? root) as Record<string, unknown>;
  const recipients = data.Recipients ?? data.recipients;
  if (!Array.isArray(recipients) || recipients.length === 0) {
    return { ok: false, error: "no recipients in provider response" };
  }
  const first = recipients[0] as Record<string, unknown>;
  const messageId = typeof first.messageId === "string" ? first.messageId : undefined;
  const providerStatus = typeof first.status === "string" ? first.status : "";
  const ok = /^(Success|Sent|Queued)$/i.test(providerStatus);
  return ok
    ? { ok: true, ...(messageId ? { messageId } : {}) }
    : { ok: false, error: providerStatus || "provider rejected message" };
}

export function createSmsSender(
  config: SmsConfig,
  transport: SmsTransport,
  now: () => number = Date.now
): (to: string[], message: string) => Promise<SmsSendResult> {
  return async (to: string[], message: string): Promise<SmsSendResult> => {
    const recipients = normalisePhoneList(to);
    if (recipients.length === 0) return { ok: false, error: "no valid recipients" };
    if (message.trim().length === 0) return { ok: false, error: "empty message" };
    try {
      const response = await transport(
        SMS_ENDPOINT,
        buildSmsBody(config, recipients, message),
        {
          apiKey: config.apiKey,
          Accept: "application/json",
          "Content-Type": "application/json",
        }
      );
      return parseSmsResponse(response.status, response.data);
    } catch (error) {
      return { ok: false, error: (error as Error).message || "transport failure" };
    } finally {
      void now();
    }
  };
}
