import { randomUUID } from "node:crypto";
import {
  AuthError,
  setAccessTokenVerifierForTests,
  type AccessTokenVerifier,
} from "../auth.js";
import type { UserRole } from "../contracts.js";

type TestAccessTokenInput = {
  id: string;
  cognitoSub?: string;
  phone: string;
  role: UserRole;
  expiresAt?: number;
  groups?: string[];
};

const payloads = new Map<string, Record<string, unknown>>();

const verifier: AccessTokenVerifier = {
  async verify(token) {
    const payload = payloads.get(token);
    if (!payload) throw new AuthError("TOKEN_INVALID", "Unknown test Cognito access token");
    return payload;
  },
};

export function cognitoSubForTestUser(userId: string): string {
  return `test-cognito:${userId}`;
}

/**
 * Produces an opaque token understood only by the test-only verifier. It is
 * never a JWT and cannot be enabled outside NODE_ENV=test.
 */
export function issueTestCognitoAccessToken(input: TestAccessTokenInput): string {
  setAccessTokenVerifierForTests(verifier);
  const token = `test-cognito-access-${randomUUID()}`;
  payloads.set(token, {
    sub: input.cognitoSub ?? cognitoSubForTestUser(input.id),
    exp: input.expiresAt ?? Math.floor(Date.now() / 1000) + 3_600,
    client_id: "test-cognito-client",
    username: input.phone,
    "cognito:groups": input.groups ?? [input.role],
  });
  return token;
}

export function resetTestCognitoVerifier(): void {
  payloads.clear();
  setAccessTokenVerifierForTests(null);
}
