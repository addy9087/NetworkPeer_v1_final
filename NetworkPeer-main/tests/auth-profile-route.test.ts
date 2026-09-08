import Fastify from "fastify";
import cookie from "@fastify/cookie";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { issueTestCognitoAccessToken, resetTestCognitoVerifier } from "../src/testing/cognito-test-verifier.js";

const getUserProfile = vi.hoisted(() => vi.fn());
const updateUserProfile = vi.hoisted(() => vi.fn());
const getUserByCognitoSub = vi.hoisted(() => vi.fn());

vi.mock("../src/repository.js", () => ({
  getUserProfile,
  updateUserProfile,
  getUserByCognitoSub,
}));

import authRoutes from "../src/routes/auth.js";
import { config } from "../src/config.js";

const USER_ID = "00000000-0000-4000-8000-000000000031";
let app: ReturnType<typeof Fastify> | undefined;

async function buildTestApp() {
  const testApp = Fastify();
  await testApp.register(cookie);
  await testApp.register(authRoutes, { prefix: config.API_PREFIX });
  await testApp.ready();
  return testApp;
}

beforeEach(() => {
  getUserByCognitoSub.mockResolvedValue({
    id: USER_ID,
    phone_number: "+15550000031",
    email: "worker@example.com",
    full_name: "Verified Worker",
    role: "WORKER",
    avatar_url: "https://example.com/avatar.jpg",
    is_active: true,
    is_verified: true,
    last_login_at: null,
    created_at: new Date(),
    updated_at: new Date(),
  });

  getUserProfile.mockResolvedValue({
    id: USER_ID,
    phoneNumber: "+15550000031",
    email: "worker@example.com",
    fullName: "Verified Worker",
    role: "WORKER",
    avatarUrl: "https://example.com/avatar.jpg",
    isActive: true,
    isVerified: true,
    createdAt: new Date(),
    workerProfile: {
      skills: ["plumbing", "electrical"],
      hourlyRateCents: 5000,
      rating: 4.95,
      totalJobsCompleted: 12,
      verificationStatus: "VERIFIED",
      preferredRadiusKm: 25,
      isAvailable: true,
    },
  });

  updateUserProfile.mockResolvedValue({
    id: USER_ID,
    phoneNumber: "+15550000031",
    email: "updated@example.com",
    fullName: "Verified Worker",
    role: "WORKER",
    avatarUrl: null,
    isActive: true,
    isVerified: true,
    createdAt: new Date(),
    workerProfile: {
      skills: ["carpentry"],
      hourlyRateCents: 5000,
      rating: 4.95,
      totalJobsCompleted: 12,
      verificationStatus: "VERIFIED",
      preferredRadiusKm: 30,
      isAvailable: false,
    },
  });
});

afterEach(async () => {
  await app?.close();
  app = undefined;
  getUserProfile.mockReset();
  updateUserProfile.mockReset();
  getUserByCognitoSub.mockReset();
  resetTestCognitoVerifier();
});

describe("GET and PATCH /auth/profile", () => {
  it("returns the full user profile when authenticated", async () => {
    app = await buildTestApp();
    const token = issueTestCognitoAccessToken({ id: USER_ID, role: "WORKER", phone: "+15550000031" });

    const res = await app.inject({
      method: "GET",
      url: "/api/v1/auth/profile",
      headers: { authorization: `Bearer ${token}` },
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.success).toBe(true);
    expect(body.data.fullName).toBe("Verified Worker");
    expect(body.data.phoneNumber).toBe("+15550000031");
    expect(body.data.workerProfile.skills).toEqual(["plumbing", "electrical"]);
  });

  it("updates allowed profile fields successfully", async () => {
    app = await buildTestApp();
    const token = issueTestCognitoAccessToken({ id: USER_ID, role: "WORKER", phone: "+15550000031" });

    const res = await app.inject({
      method: "PATCH",
      url: "/api/v1/auth/profile",
      headers: {
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
      },
      payload: JSON.stringify({
        email: "updated@example.com",
        skills: ["carpentry"],
        preferred_radius_km: 30,
        is_available: false,
      }),
    });

    expect(res.statusCode).toBe(200);
    const body = JSON.parse(res.payload);
    expect(body.success).toBe(true);
    expect(updateUserProfile).toHaveBeenCalledWith(USER_ID, {
      email: "updated@example.com",
      avatarUrl: undefined,
      skills: ["carpentry"],
      preferredRadiusKm: 30,
      isAvailable: false,
    });
  });

  it("strictly rejects attempts to modify full_name or phone_number", async () => {
    app = await buildTestApp();
    const token = issueTestCognitoAccessToken({ id: USER_ID, role: "WORKER", phone: "+15550000031" });

    const res = await app.inject({
      method: "PATCH",
      url: "/api/v1/auth/profile",
      headers: {
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
      },
      payload: JSON.stringify({
        full_name: "Hacked Name",
        phone_number: "+19999999999",
      }),
    });

    expect(res.statusCode).toBe(400);
    const body = JSON.parse(res.payload);
    expect(body.success).toBe(false);
    expect(body.error.code).toBe("VALIDATION_ERROR");
    expect(updateUserProfile).not.toHaveBeenCalled();
  });
});
