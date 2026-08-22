import { createHmac } from "node:crypto";
import Fastify from "fastify";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const logout = vi.hoisted(() => vi.fn());

vi.mock("../src/services/auth-service.js", () => ({
  authService: { logout },
}));

import { config } from "../src/config.js";
import { signAccessToken } from "../src/auth.js";
import authRoutes from "../src/routes/auth.js";

const USER_ID = "00000000-0000-4000-8000-000000000031";
const PHONE = "+15550000031";
const REFRESH_TOKEN = "refresh-token-for-session-revocation";
let app: ReturnType<typeof Fastify> | undefined;

function expiredBearer(): string {
  const now = Math.floor(Date.now() / 1000);
  const encode = (value: Record<string, unknown>) => Buffer.from(JSON.stringify(value)).toString("base64url");
  const header = encode({ alg: "HS256", typ: "JWT" });
  const payload = encode({
    iss: config.JWT_ISSUER,
    aud: config.JWT_AUDIENCE,
    iat: now - 60,
    exp: now - 1,
    jti: "expired-access-token",
    type: "access",
    sub: USER_ID,
    role: "CLIENT",
    phone: PHONE,
  });
  const signed = `${header}.${payload}`;
  const signature = createHmac("sha256", config.JWT_SECRET).update(signed).digest("base64url");
  return `Bearer ${signed}.${signature}`;
}

beforeEach(() => {
  logout.mockResolvedValue(undefined);
});

afterEach(async () => {
  await app?.close();
  app = undefined;
  logout.mockReset();
});

describe("POST /auth/logout", () => {
  it("revokes a refresh session without an access bearer", async () => {
    app = Fastify();
    await app.register(authRoutes, { prefix: "/api/v1" });
    await app.ready();

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/auth/logout",
      payload: { refresh_token: REFRESH_TOKEN },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ success: true, data: { logged_out: true }, error: null });
    expect(logout).toHaveBeenCalledWith(REFRESH_TOKEN, undefined);
  });

  it("preserves subject binding for callers with a valid access bearer", async () => {
    app = Fastify();
    await app.register(authRoutes, { prefix: "/api/v1" });
    await app.ready();

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/auth/logout",
      headers: { authorization: `Bearer ${signAccessToken({ id: USER_ID, role: "CLIENT", phone: PHONE })}` },
      payload: { refresh_token: REFRESH_TOKEN },
    });

    expect(response.statusCode).toBe(200);
    expect(logout).toHaveBeenCalledWith(REFRESH_TOKEN, USER_ID);
  });

  it("still revokes when the local access bearer has expired", async () => {
    app = Fastify();
    await app.register(authRoutes, { prefix: "/api/v1" });
    await app.ready();

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/auth/logout",
      headers: { authorization: expiredBearer() },
      payload: { refresh_token: REFRESH_TOKEN },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ success: true, data: { logged_out: true }, error: null });
    expect(logout).toHaveBeenCalledWith(REFRESH_TOKEN, undefined);
  });
});
