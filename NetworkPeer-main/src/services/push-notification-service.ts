import { createSign } from "node:crypto";
import { config } from "../config.js";
import {
  claimPushDelivery,
  deactivateDevicePushTokens,
  getActiveDevicePushTokens,
  listPushDeliveryCandidates,
  markPushDeliverySent,
  markPushDeliverySkipped,
  releasePushDelivery,
  type PendingPushDelivery,
} from "../repository.js";
import { captureException, logger } from "../observability.js";

export type PushGatewayResult = { invalidTokens: string[] };

export interface PushGateway {
  readonly enabled: boolean;
  send(input: {
    tokens: readonly string[];
    title: string;
    body: string;
    data: Record<string, string>;
  }): Promise<PushGatewayResult>;
}

function base64Url(value: string): string {
  return Buffer.from(value).toString("base64url");
}

function fetchWithTimeout(url: string, init: RequestInit): Promise<Response> {
  return fetch(url, { ...init, signal: AbortSignal.timeout(10_000) });
}

const MINIMUM_FCM_RETRY_DELAY_MS = 60_000;
const RETRYABLE_FCM_HTTP_STATUSES = new Set([408, 429, 500, 502, 503, 504]);
const RETRYABLE_FCM_STATUSES = new Set([
  "DEADLINE_EXCEEDED",
  "INTERNAL",
  "RESOURCE_EXHAUSTED",
  "UNAVAILABLE",
]);
const RETRYABLE_FCM_ERROR_CODES = new Set(["QUOTA_EXCEEDED", "UNAVAILABLE"]);

type FcmResponseError = Record<string, unknown>;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function fcmError(payload: unknown): FcmResponseError | null {
  if (!isRecord(payload) || !isRecord(payload["error"])) return null;
  return payload["error"];
}

function fcmErrorMessage(payload: unknown): string {
  const error = fcmError(payload);
  if (typeof error?.["message"] === "string") return error["message"];
  if (isRecord(payload) && typeof payload["error"] === "string") return payload["error"];
  return "";
}

function fcmErrorStatus(payload: unknown): string | null {
  const status = fcmError(payload)?.["status"];
  return typeof status === "string" ? status : null;
}

function fcmErrorHasCode(payload: unknown, code: string): boolean {
  const details = fcmError(payload)?.["details"];
  return Array.isArray(details) && details.some((detail) => isRecord(detail) && detail["errorCode"] === code);
}

function isFcmUnregistered(payload: unknown): boolean {
  // HTTP v1 reports this as NOT_FOUND plus an FcmError detail, rather than
  // placing UNREGISTERED in error.status.
  return fcmErrorStatus(payload) === "UNREGISTERED" || fcmErrorHasCode(payload, "UNREGISTERED");
}

function retryDelayFromHeader(retryAfter: string | null): number {
  const value = retryAfter?.trim();
  if (value && /^\d+$/.test(value)) {
    const seconds = Number(value);
    if (Number.isSafeInteger(seconds)) {
      return Math.max(MINIMUM_FCM_RETRY_DELAY_MS, seconds * 1_000);
    }
  }
  if (value) {
    const retryAt = Date.parse(value);
    if (Number.isFinite(retryAt)) {
      return Math.max(MINIMUM_FCM_RETRY_DELAY_MS, retryAt - Date.now());
    }
  }
  return MINIMUM_FCM_RETRY_DELAY_MS;
}

function isRetryableFcmFailure(response: Response, payload: unknown): boolean {
  return (
    RETRYABLE_FCM_HTTP_STATUSES.has(response.status) ||
    RETRYABLE_FCM_STATUSES.has(fcmErrorStatus(payload) ?? "") ||
    [...RETRYABLE_FCM_ERROR_CODES].some((code) => fcmErrorHasCode(payload, code))
  );
}

export class PushGatewayError extends Error {
  readonly invalidTokens: string[];

  constructor(message: string, invalidTokens: readonly string[] = []) {
    super(message);
    this.name = "PushGatewayError";
    this.invalidTokens = [...invalidTokens];
  }
}

export class PushGatewayRetryError extends PushGatewayError {
  readonly retryAfterMs: number;

  constructor(message: string, retryAfterMs: number, invalidTokens: readonly string[] = []) {
    super(message, invalidTokens);
    this.name = "PushGatewayRetryError";
    this.retryAfterMs = Number.isFinite(retryAfterMs)
      ? Math.max(MINIMUM_FCM_RETRY_DELAY_MS, retryAfterMs)
      : MINIMUM_FCM_RETRY_DELAY_MS;
  }
}

async function fetchFcm(
  url: string,
  init: RequestInit,
  operation: string,
  invalidTokens: readonly string[] = [],
): Promise<Response> {
  try {
    return await fetchWithTimeout(url, init);
  } catch {
    throw new PushGatewayRetryError(
      `${operation} did not receive a response`,
      MINIMUM_FCM_RETRY_DELAY_MS,
      invalidTokens,
    );
  }
}

function fcmFailure(
  operation: string,
  response: Response,
  payload: unknown,
  invalidTokens: readonly string[] = [],
): PushGatewayError {
  const message = `${operation} failed: ${response.status} ${fcmErrorMessage(payload)}`.trim();
  if (isRetryableFcmFailure(response, payload)) {
    return new PushGatewayRetryError(message, retryDelayFromHeader(response.headers.get("retry-after")), invalidTokens);
  }
  return new PushGatewayError(message, invalidTokens);
}

const REQUIRED_PUSH_DATA_KEYS = new Set([
  "recipient_user_id",
  "title",
  "body",
  "cursor",
  "topic",
]);
const RESERVED_FCM_DATA_KEYS = new Set(["from", "message_type", "collapse_key"]);

function isSafeEventDataKey(key: string): boolean {
  return (
    /^[a-z][a-z0-9_]{0,63}$/.test(key) &&
    !REQUIRED_PUSH_DATA_KEYS.has(key) &&
    !RESERVED_FCM_DATA_KEYS.has(key)
  );
}

function scalarPushValue(value: unknown): string | null {
  if (typeof value === "string" || typeof value === "boolean") return String(value);
  if (typeof value === "number" && Number.isFinite(value)) return String(value);
  return null;
}

/**
 * Push data is only a wake-up/display hint. The mandatory fields come from the
 * durable notification record so event payload values cannot override them.
 */
export function toPushData(delivery: PendingPushDelivery): Record<string, string> {
  const data: Record<string, string> = {
    recipient_user_id: delivery.recipientUserId,
    title: delivery.title,
    body: delivery.body,
    cursor: delivery.cursor,
    topic: delivery.topic,
  };
  for (const [key, value] of Object.entries(delivery.payload)) {
    if (!isSafeEventDataKey(key)) continue;
    const scalar = scalarPushValue(value);
    if (scalar !== null) data[key] = scalar;
  }
  return data;
}

export function buildDataOnlyFcmMessage(token: string, data: Record<string, string>) {
  return {
    message: {
      token,
      data,
      android: { priority: "HIGH" },
      apns: {
        headers: {
          "apns-push-type": "background",
          "apns-priority": "5",
        },
        payload: { aps: { "content-available": 1 } },
      },
    },
  };
}

/** FCM HTTP v1 gateway using built-in crypto instead of a large admin SDK. */
export class FcmHttpV1Gateway implements PushGateway {
  readonly enabled = true;
  private accessToken: { value: string; expiresAt: number } | null = null;

  private async getAccessToken(): Promise<string> {
    if (this.accessToken && this.accessToken.expiresAt > Date.now() + 60_000) {
      return this.accessToken.value;
    }

    const issuedAt = Math.floor(Date.now() / 1000);
    const signed = `${base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }))}.${base64Url(
      JSON.stringify({
        iss: config.FIREBASE_CLIENT_EMAIL,
        scope: "https://www.googleapis.com/auth/firebase.messaging",
        aud: "https://oauth2.googleapis.com/token",
        iat: issuedAt,
        exp: issuedAt + 3600,
      }),
    )}`;
    const signer = createSign("RSA-SHA256");
    signer.update(signed);
    signer.end();
    const assertion = `${signed}.${signer.sign(config.FIREBASE_PRIVATE_KEY.replace(/\\n/g, "\n"), "base64url")}`;

    const response = await fetchFcm("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion,
      }),
    }, "FCM OAuth token request");
    const payload = await response.json().catch(() => null) as {
      access_token?: unknown;
      expires_in?: unknown;
      error?: unknown;
    } | null;
    const accessToken = payload?.access_token;
    if (!response.ok || typeof accessToken !== "string") {
      throw fcmFailure("FCM OAuth token request", response, payload);
    }
    const expiresInSeconds = typeof payload?.expires_in === "number" ? payload.expires_in : 3000;
    this.accessToken = { value: accessToken, expiresAt: Date.now() + expiresInSeconds * 1000 };
    return this.accessToken.value;
  }

  async send(input: {
    tokens: readonly string[];
    title: string;
    body: string;
    data: Record<string, string>;
  }): Promise<PushGatewayResult> {
    const accessToken = await this.getAccessToken();
    const invalidTokens: string[] = [];
    for (const token of input.tokens) {
      const response = await fetchFcm(
        `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(config.FIREBASE_PROJECT_ID)}/messages:send`,
        {
          method: "POST",
          headers: {
            authorization: `Bearer ${accessToken}`,
            "content-type": "application/json",
          },
          body: JSON.stringify(buildDataOnlyFcmMessage(token, input.data)),
        },
        "FCM send",
        invalidTokens,
      );
      if (response.ok) continue;
      const payload = await response.json().catch(() => null);
      if (isFcmUnregistered(payload)) {
        invalidTokens.push(token);
        continue;
      }
      throw fcmFailure("FCM send", response, payload, invalidTokens);
    }
    return { invalidTokens };
  }
}

export class DisabledPushGateway implements PushGateway {
  readonly enabled = false;

  async send(): Promise<PushGatewayResult> {
    return { invalidTokens: [] };
  }
}

export function createPushGateway(): PushGateway {
  return config.PUSH_NOTIFICATIONS_ENABLED === "true" ? new FcmHttpV1Gateway() : new DisabledPushGateway();
}

export class PushDeliveryDispatcher {
  private timer: NodeJS.Timeout | null = null;
  private flushInFlight: Promise<void> | null = null;

  constructor(private readonly gateway: PushGateway) {}

  get enabled(): boolean {
    return this.gateway.enabled;
  }

  start(): void {
    if (!this.gateway.enabled || this.timer) return;
    this.scheduleFlush();
    this.timer = setInterval(() => this.scheduleFlush(), config.PUSH_DISPATCH_INTERVAL_MS);
    this.timer.unref();
  }

  async close(): Promise<void> {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
    await this.flushInFlight;
  }

  async flush(): Promise<void> {
    if (!this.gateway.enabled) return;
    if (this.flushInFlight) return this.flushInFlight;
    this.flushInFlight = this.runFlush().finally(() => {
      this.flushInFlight = null;
    });
    return this.flushInFlight;
  }

  private scheduleFlush(): void {
    void this.flush().catch((err: unknown) => {
      logger.error({ err }, "push delivery flush failed");
      captureException(err, { operation: "legacy-push-flush" });
    });
  }

  private async runFlush(): Promise<void> {
    const cursors = await listPushDeliveryCandidates(25);
    for (const cursor of cursors) {
      try {
        await this.process(cursor);
      } catch (err) {
        if (err instanceof PushGatewayRetryError) return;
        // process() already releases non-transient failures before propagating.
      }
    }
  }

  private async deactivateInvalidTokens(tokens: readonly string[]): Promise<void> {
    if (tokens.length === 0) return;
    await deactivateDevicePushTokens(tokens).catch((err: unknown) => {
      logger.warn({ err, invalidTokenCount: tokens.length }, "failed to deactivate invalid push tokens");
    });
  }

  async process(cursor: string): Promise<void> {
    if (!this.gateway.enabled) return;
    const delivery = await claimPushDelivery(cursor);
    if (!delivery) return;
    logger.info({ cursor: delivery.cursor, recipientUserId: delivery.recipientUserId }, "push delivery started");
    try {
      const tokens = await getActiveDevicePushTokens(delivery.recipientUserId);
      if (tokens.length === 0) {
        await markPushDeliverySkipped(delivery.cursor);
        return;
      }
      const result = await this.gateway.send({
        tokens,
        title: delivery.title,
        body: delivery.body,
        data: toPushData(delivery),
      });
      await markPushDeliverySent(delivery.cursor);
      await this.deactivateInvalidTokens(result.invalidTokens);
      logger.info({ cursor: delivery.cursor, invalidTokenCount: result.invalidTokens.length }, "push delivery completed");
    } catch (err) {
      const invalidTokens = err instanceof PushGatewayError ? err.invalidTokens : [];
      await this.deactivateInvalidTokens(invalidTokens);
      if (err instanceof PushGatewayRetryError) {
        await releasePushDelivery(delivery.cursor, err.message, err.retryAfterMs);
        logger.warn({ err, cursor: delivery.cursor, retryAfterMs: err.retryAfterMs }, "push delivery released for durable retry");
        throw err;
      }
      logger.warn({ err, cursor: delivery.cursor }, "push delivery released for retry");
      await releasePushDelivery(delivery.cursor, err instanceof Error ? err.message : "Push delivery failed");
      throw err;
    }
  }
}
