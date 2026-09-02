import Fastify from "fastify";
import cookie from "@fastify/cookie";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { issueTestCognitoAccessToken, resetTestCognitoVerifier } from "../src/testing/cognito-test-verifier.js";

const logout = vi.hoisted(() => vi.fn());
const requestOtp = vi.hoisted(() => vi.fn());
const verifyOtpAndLogin = vi.hoisted(() => vi.fn());

vi.mock("../src/services/auth-service.js", () => ({
  authService: { logout, requestOtp, verifyOtpAndLogin },
}));

import authRoutes from "../src/routes/auth.js";
import { config } from "../src/config.js";

const USER_ID = "00000000-0000-4000-8000-000000000031";
const REFRESH_TOKEN = "refresh-token-for-session-revocation";
let app: ReturnType<typeof Fastify> | undefined;

async function buildTestApp() {
  const testApp = Fastify();
  await testApp.register(cookie);
  await testApp.register(authRoutes, { prefix: config.API_PREFIX });
  await testApp.ready();
  return testApp;
}

beforeEach(() => {
  logout.mockResolvedValue(undefined);
  requestOtp.mockResolvedValue({
    challenge_id: "cognito-challenge",
    expires_in_seconds: 300,
    otp_length: 6,
    delivery: { transport: "sms" },
  });
  verifyOtpAndLogin.mockResolvedValue({
    access_token: "cognito-access-token",
    refresh_token: REFRESH_TOKEN,
    expires_in: 900,
    user: { id: USER_ID, role: "CLIENT", phone: "+15550000031" },
  });
});

afterEach(async () => {
  await app?.close();
  app = undefined;
  logout.mockReset();
  requestOtp.mockReset();
  verifyOtpAndLogin.mockReset();
  resetTestCognitoVerifier();
});

describe("Cognito OTP routes", () => {
  it("forwards the selected role when starting a public account challenge", async () => {
    app = await buildTestApp();

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/auth/otp/request",
      payload: { phone_number: "+15550000031", role: "CLIENT" },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({
      success: true,
      data: {
        challenge_id: "cognito-challenge",
        expires_in_seconds: 300,
        otp_length: 6,
        delivery: { transport: "sms" },
      },
      error: null,
    });
    expect(requestOtp).toHaveBeenCalledWith({ phone: "+15550000031", role: "CLIENT" });
  });

  it("verifies a native challenge without accepting the legacy role field", async () => {
    app = await buildTestApp();

    const success = await app.inject({
      method: "POST",
      url: "/api/v1/auth/otp/verify",
      payload: {
        phone_number: "+15550000031",
        challenge_id: "cognito-challenge",
        otp: "123456",
        transport: "native",
      },
    });

    expect(success.statusCode).toBe(200);
    expect(verifyOtpAndLogin).toHaveBeenCalledWith({
      phone: "+15550000031",
      otp: "123456",
      challengeId: "cognito-challenge",
    });

    verifyOtpAndLogin.mockClear();
    const legacy = await app.inject({
      method: "POST",
      url: "/api/v1/auth/otp/verify",
      payload: {
        phone_number: "+15550000031",
        challenge_id: "cognito-challenge",
        otp: "123456",
        role: "CLIENT",
        transport: "native",
      },
    });

    expect(legacy.statusCode).toBe(400);
    expect(verifyOtpAndLogin).not.toHaveBeenCalled();
  });
});

describe("POST /auth/logout", () => {
  it("revokes a refresh session without an access bearer", async () => {
    app = await buildTestApp();

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/auth/logout",
      payload: { refresh_token: REFRESH_TOKEN },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ success: true, data: { logged_out: true }, error: null });
    expect(logout).toHaveBeenCalledWith(REFRESH_TOKEN);
  });

  it("preserves subject binding for callers with a valid access bearer", async () => {
    app = await buildTestApp();

    const response = await app.inject({
      method: "POST",
      url: "/api/v1/auth/logout",
      headers: { authorization: `Bearer ${issueTestCognitoAccessToken({ id: USER_ID, role: "CLIENT", phone: "+15550000031" })}` },
      payload: { refresh_token: REFRESH_TOKEN },
    });

    expect(response.statusCode).toBe(200);
    expect(logout).toHaveBeenCalledWith(REFRESH_TOKEN);
  });
});
