import { afterEach, describe, expect, it, vi } from "vitest";

const pushRepository = vi.hoisted(() => ({
  claimPushDelivery: vi.fn(),
  deactivateDevicePushTokens: vi.fn(),
  getActiveDevicePushTokens: vi.fn(),
  listPushDeliveryCandidates: vi.fn(),
  markPushDeliverySent: vi.fn(),
  markPushDeliverySkipped: vi.fn(),
  releasePushDelivery: vi.fn(),
}));

const observability = vi.hoisted(() => ({
  captureException: vi.fn(),
  logger: {
    error: vi.fn(),
    info: vi.fn(),
    warn: vi.fn(),
  },
}));

vi.mock("../src/repository.js", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../src/repository.js")>();
  return { ...actual, ...pushRepository };
});

vi.mock("../src/observability.js", () => observability);

import {
  buildDataOnlyFcmMessage,
  FcmHttpV1Gateway,
  PushDeliveryDispatcher,
  PushGatewayRetryError,
  toPushData,
} from "../src/services/push-notification-service.js";
import type { PushGateway } from "../src/services/push-notification-service.js";
import type { PendingPushDelivery } from "../src/repository.js";

const PUSH_DELIVERY: PendingPushDelivery = {
  cursor: "42",
  recipientUserId: "00000000-0000-4000-8000-000000000001",
  topic: "SYSTEM",
  title: "System update",
  body: "Your account has an update.",
  payload: {},
};

function gatewayWithCachedAccessToken(): FcmHttpV1Gateway {
  const gateway = new FcmHttpV1Gateway();
  Reflect.set(gateway, "accessToken", { value: "test-access-token", expiresAt: Number.MAX_SAFE_INTEGER });
  return gateway;
}

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  for (const mock of Object.values(pushRepository)) mock.mockReset();
  for (const mock of Object.values(observability.logger)) mock.mockReset();
  observability.captureException.mockReset();
});

describe("FCM data-only delivery", () => {
  it("uses canonical notification hints and excludes unsafe event payload values", () => {
    const delivery: PendingPushDelivery = {
      cursor: "42",
      recipientUserId: "00000000-0000-4000-8000-000000000001",
      topic: "JOB_STATUS_CHANGED",
      title: "Job status updated",
      body: "Your job moved to a new stage.",
      payload: {
        job_id: "00000000-0000-4000-8000-000000000002",
        status: "IN_PROGRESS",
        reassigned: false,
        title: "payload must not replace notification title",
        cursor: "999",
        recipient_user_id: "00000000-0000-4000-8000-000000000003",
        from: "reserved",
        "unsafe-key": "ignored",
        nested: { secret: "ignored" },
        items: ["ignored"],
      },
    };

    expect(toPushData(delivery)).toEqual({
      recipient_user_id: delivery.recipientUserId,
      title: delivery.title,
      body: delivery.body,
      cursor: delivery.cursor,
      topic: delivery.topic,
      job_id: "00000000-0000-4000-8000-000000000002",
      status: "IN_PROGRESS",
      reassigned: "false",
    });
  });

  it("builds an Android high-priority, APNs background message without notification", () => {
    const data = { recipient_user_id: "user", title: "Title", body: "Body", cursor: "1", topic: "SYSTEM" };
    const result = buildDataOnlyFcmMessage("token", data);

    expect(result).toEqual({
      message: {
        token: "token",
        data,
        android: { priority: "HIGH" },
        apns: {
          headers: { "apns-push-type": "background", "apns-priority": "5" },
          payload: { aps: { "content-available": 1 } },
        },
      },
    });
    expect(result.message).not.toHaveProperty("notification");
  });

  it("recognizes FCM HTTP v1 UNREGISTERED details and continues to remaining tokens", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        error: {
          code: 404,
          status: "NOT_FOUND",
          message: "Requested entity was not found.",
          details: [{
            "@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError",
            errorCode: "UNREGISTERED",
          }],
        },
      }), { status: 404 }))
      .mockResolvedValueOnce(new Response("", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await gatewayWithCachedAccessToken().send({
      tokens: ["unregistered-token", "active-token"],
      title: "Title",
      body: "Body",
      data: { cursor: "42" },
    });

    expect(result).toEqual({ invalidTokens: ["unregistered-token"] });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("honors a transient FCM quota response without retrying sooner than one minute", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      error: { code: 429, status: "RESOURCE_EXHAUSTED", message: "Quota exceeded" },
    }), {
      status: 429,
      headers: { "retry-after": "5" },
    }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(gatewayWithCachedAccessToken().send({
      tokens: ["active-token"],
      title: "Title",
      body: "Body",
      data: { cursor: "42" },
    })).rejects.toMatchObject({
      invalidTokens: [],
      retryAfterMs: 60_000,
    });
  });

  it("honors a longer FCM Retry-After value", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      error: { code: 503, status: "UNAVAILABLE", message: "Try again later" },
    }), {
      status: 503,
      headers: { "retry-after": "120" },
    }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(gatewayWithCachedAccessToken().send({
      tokens: ["active-token"],
      title: "Title",
      body: "Body",
      data: { cursor: "42" },
    })).rejects.toMatchObject({ retryAfterMs: 120_000 });
  });

  it("releases transient failures with the durable retry delay", async () => {
    pushRepository.listPushDeliveryCandidates.mockResolvedValue([PUSH_DELIVERY.cursor]);
    pushRepository.claimPushDelivery.mockResolvedValue(PUSH_DELIVERY);
    pushRepository.getActiveDevicePushTokens.mockResolvedValue(["unregistered-token"]);
    pushRepository.deactivateDevicePushTokens.mockResolvedValue(undefined);
    const gateway: PushGateway = {
      enabled: true,
      send: vi.fn().mockRejectedValue(new PushGatewayRetryError("FCM send failed: 429", 5_000, ["unregistered-token"])),
    };
    const dispatcher = new PushDeliveryDispatcher(gateway);

    await dispatcher.flush();

    expect(pushRepository.deactivateDevicePushTokens).toHaveBeenCalledWith(["unregistered-token"]);
    expect(pushRepository.releasePushDelivery).toHaveBeenCalledWith(
      PUSH_DELIVERY.cursor,
      "FCM send failed: 429",
      60_000,
    );
    expect(pushRepository.listPushDeliveryCandidates).toHaveBeenCalledTimes(1);
  });

  it("keeps non-transient failures on the immediate durable retry path", async () => {
    pushRepository.claimPushDelivery.mockResolvedValue(PUSH_DELIVERY);
    pushRepository.getActiveDevicePushTokens.mockResolvedValue(["active-token"]);
    pushRepository.releasePushDelivery.mockResolvedValue(undefined);
    const gateway: PushGateway = {
      enabled: true,
      send: vi.fn().mockRejectedValue(new Error("FCM send failed: 401")),
    };

    await expect(new PushDeliveryDispatcher(gateway).process(PUSH_DELIVERY.cursor)).rejects.toThrow(
      "FCM send failed: 401",
    );

    expect(pushRepository.releasePushDelivery).toHaveBeenCalledWith(
      PUSH_DELIVERY.cursor,
      "FCM send failed: 401",
    );
  });
});
