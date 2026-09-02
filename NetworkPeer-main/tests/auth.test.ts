import { afterEach, describe, expect, it } from "vitest";
import { issueTestCognitoAccessToken, resetTestCognitoVerifier } from "../src/testing/cognito-test-verifier.js";
import { verifyAccessToken, AuthError } from "../src/auth.js";

describe("Cognito access token verification (test verifier)", () => {
  const userId = "user-1";
  const role = "WORKER" as const;
  const phone = "+15550001111";

  afterEach(() => {
    resetTestCognitoVerifier();
  });

  it("verifies a test Cognito token with correct claims", async () => {
    const token = issueTestCognitoAccessToken({ id: userId, role, phone });
    const claims = await verifyAccessToken(token);
    expect(claims.sub).toBe(`test-cognito:${userId}`);
    expect(claims.clientId).toBe("test-cognito-client");
    expect(claims.username).toBe(phone);
    expect(claims.groups).toEqual([role]);
    expect(claims.exp).toBeGreaterThan(Math.floor(Date.now() / 1000));
  });

  it("supports an explicit Cognito subject for seeded integration users", async () => {
    const token = issueTestCognitoAccessToken({
      id: userId,
      cognitoSub: "seeded-cognito-subject",
      role,
      phone,
    });

    await expect(verifyAccessToken(token)).resolves.toMatchObject({ sub: "seeded-cognito-subject" });
  });

  it("rejects an unknown test token", async () => {
    await expect(verifyAccessToken("unknown-test-token")).rejects.toThrow(AuthError);
  });

  it("rejects an expired test token", async () => {
    const token = issueTestCognitoAccessToken({
      id: userId,
      role,
      phone,
      expiresAt: Math.floor(Date.now() / 1000) - 60,
    });
    await expect(verifyAccessToken(token)).rejects.toThrow(AuthError);
  });
});
