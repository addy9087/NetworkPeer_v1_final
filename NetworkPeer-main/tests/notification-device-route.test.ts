import Fastify from "fastify";
import { afterEach, describe, expect, it, vi } from "vitest";

const getUserById = vi.hoisted(() => vi.fn());
const deactivateDevicePushTokenForUser = vi.hoisted(() => vi.fn());

vi.mock("../src/repository.js", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../src/repository.js")>();
  return { ...actual, getUserById, deactivateDevicePushTokenForUser };
});

import { signAccessToken } from "../src/auth.js";
import type { User } from "../src/contracts.js";
import notificationRoutes from "../src/routes/notifications.js";

const USER_ID = "00000000-0000-4000-8000-000000000021";
const TOKEN = "fcm-device-token-that-is-long-enough";
let app: ReturnType<typeof Fastify> | undefined;

function activeUser(): User {
  return {
    id: USER_ID,
    phone_number: "+15550000021",
    email: null,
    full_name: "Notification Test User",
    role: "CLIENT",
    avatar_url: null,
    is_active: true,
    is_verified: true,
    last_login_at: null,
    created_at: new Date("2026-01-01T00:00:00.000Z"),
    updated_at: new Date("2026-01-01T00:00:00.000Z"),
  };
}

function bearer(): string {
  return `Bearer ${signAccessToken({ id: USER_ID, role: "CLIENT", phone: "+15550000021" })}`;
}

afterEach(async () => {
  await app?.close();
  app = undefined;
  getUserById.mockReset();
  deactivateDevicePushTokenForUser.mockReset();
});

describe("DELETE /notifications/devices", () => {
  it("deactivates only the authenticated caller's token", async () => {
    getUserById.mockResolvedValue(activeUser());
    deactivateDevicePushTokenForUser.mockResolvedValue(true);
    app = Fastify();
    await app.register(notificationRoutes, { prefix: "/api/v1" });
    await app.ready();

    const response = await app.inject({
      method: "DELETE",
      url: "/api/v1/notifications/devices",
      headers: { authorization: bearer() },
      payload: { token: TOKEN },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ success: true, data: { deactivated: true }, error: null });
    expect(deactivateDevicePushTokenForUser).toHaveBeenCalledWith(USER_ID, TOKEN);
  });

  it("rejects malformed deregistration bodies before calling the repository", async () => {
    getUserById.mockResolvedValue(activeUser());
    app = Fastify();
    await app.register(notificationRoutes, { prefix: "/api/v1" });
    await app.ready();

    const response = await app.inject({
      method: "DELETE",
      url: "/api/v1/notifications/devices",
      headers: { authorization: bearer() },
      payload: { token: "short" },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      success: false,
      error: { code: "VALIDATION_ERROR" },
    });
    expect(deactivateDevicePushTokenForUser).not.toHaveBeenCalled();
  });
});
