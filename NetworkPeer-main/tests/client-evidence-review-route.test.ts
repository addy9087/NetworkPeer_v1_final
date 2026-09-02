import Fastify from "fastify";
import cookie from "@fastify/cookie";
import { afterEach, describe, expect, it, vi } from "vitest";
import { issueTestCognitoAccessToken, resetTestCognitoVerifier } from "../src/testing/cognito-test-verifier.js";
import { config } from "../src/config.js";

const getUserByCognitoSub = vi.hoisted(() => vi.fn());

vi.mock("../src/repository.js", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../src/repository.js")>();
  return { ...actual, getUserByCognitoSub };
});

import type { User, UserRole } from "../src/contracts.js";
import clientJobsRoutes from "../src/routes/client-jobs.js";
import type { ClientEvidenceReviewService } from "../src/services/client-evidence-review-service.js";

const CLIENT_ID = "00000000-0000-4000-8000-000000000011";
const JOB_ID = "00000000-0000-4000-8000-000000000012";
let app: ReturnType<typeof Fastify> | undefined;

async function buildTestApp(evidenceReviewService: ClientEvidenceReviewService) {
  const testApp = Fastify();
  await testApp.register(cookie);
  await testApp.register(clientJobsRoutes, {
    prefix: config.API_PREFIX,
    evidenceReviewService,
  });
  await testApp.ready();
  return testApp;
}

function activeUser(role: UserRole): User {
  return {
    id: CLIENT_ID,
    phone_number: "+15550000011",
    email: null,
    full_name: "Evidence Review Test User",
    role,
    avatar_url: null,
    is_active: true,
    is_verified: true,
    last_login_at: null,
    created_at: new Date("2026-01-01T00:00:00.000Z"),
    updated_at: new Date("2026-01-01T00:00:00.000Z"),
  };
}

function bearer(role: UserRole): string {
  return `Bearer ${issueTestCognitoAccessToken({ id: CLIENT_ID, role, phone: "+15550000011" })}`;
}

afterEach(async () => {
  await app?.close();
  app = undefined;
  getUserByCognitoSub.mockReset();
  resetTestCognitoVerifier();
});

describe("GET /client/jobs/:jobId/evidence", () => {
  it("returns the review projection for an authenticated client and prevents response caching", async () => {
    const listForClient = vi.fn().mockResolvedValue({ evidence: [] });
    getUserByCognitoSub.mockResolvedValue(activeUser("CLIENT"));
    app = await buildTestApp({ listForClient } as unknown as ClientEvidenceReviewService);

    const response = await app.inject({
      method: "GET",
      url: `/api/v1/client/jobs/${JOB_ID}/evidence`,
      headers: { authorization: bearer("CLIENT") },
    });

    expect(response.statusCode).toBe(200);
    expect(response.headers["cache-control"]).toBe("no-store");
    expect(response.json()).toEqual({ success: true, data: { evidence: [] }, error: null });
    expect(listForClient).toHaveBeenCalledWith(CLIENT_ID, JOB_ID);
  });

  it("rejects non-client roles before requesting evidence", async () => {
    const listForClient = vi.fn();
    getUserByCognitoSub.mockResolvedValue(activeUser("WORKER"));
    app = await buildTestApp({ listForClient } as unknown as ClientEvidenceReviewService);

    const response = await app.inject({
      method: "GET",
      url: `/api/v1/client/jobs/${JOB_ID}/evidence`,
      headers: { authorization: bearer("WORKER") },
    });

    expect(response.statusCode).toBe(403);
    expect(response.json()).toEqual({
      success: false,
      data: null,
      error: { code: "FORBIDDEN", message: "Insufficient permissions" },
    });
    expect(listForClient).not.toHaveBeenCalled();
  });
});
