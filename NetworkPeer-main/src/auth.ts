import { CognitoJwtVerifier } from "aws-jwt-verify";
import { config } from "./config.js";
import type { UserRole } from "./contracts.js";

/** A normalized authentication error suitable for the API error envelope. */
export class AuthError extends Error {
  readonly code: string;
  readonly statusCode: number;

  constructor(code: string, message: string, statusCode = 401) {
    super(message);
    this.name = "AuthError";
    this.code = code;
    this.statusCode = statusCode;
  }
}

/**
 * Cognito access-token claims used by the API. A Cognito `sub` is an opaque
 * external identity; it is mapped to the internal PostgreSQL user UUID before
 * any marketplace authorization is performed.
 */
export type AccessTokenClaims = {
  sub: string;
  exp: number;
  clientId: string;
  username: string | null;
  groups: string[];
};

export type TokenUser = { id: string; role: UserRole; phone: string };

/** Cognito-issued tokens normalized for native clients. */
export type TokenPair = {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  user: TokenUser;
};

export type AccessTokenVerifier = {
  verify(token: string): Promise<Record<string, unknown>>;
};

let verifier: AccessTokenVerifier | null = null;
let verifierOverride: AccessTokenVerifier | null = null;

export function assertCognitoConfigured(): void {
  if (!config.COGNITO_USER_POOL_ID || !config.COGNITO_CLIENT_ID || !config.COGNITO_ISSUER) {
    throw new AuthError(
      "AUTH_NOT_CONFIGURED",
      "Amazon Cognito authentication is not configured for this environment",
      503,
    );
  }
}

function accessVerifier(): AccessTokenVerifier {
  assertCognitoConfigured();
  if (!verifier) {
    verifier = CognitoJwtVerifier.create({
      userPoolId: config.COGNITO_USER_POOL_ID,
      tokenUse: "access",
      clientId: config.COGNITO_CLIENT_ID,
    }) as unknown as AccessTokenVerifier;
  }
  return verifier;
}

/** A marketplace principal must belong to exactly one trusted Cognito role group. */
export function roleFromCognitoGroups(groups: readonly string[]): UserRole {
  const roles = groups.filter(
    (group): group is UserRole => group === "CLIENT" || group === "WORKER" || group === "ADMIN",
  );
  if (roles.length !== 1) {
    throw new AuthError("TOKEN_INVALID", "Cognito token has an invalid role assignment");
  }
  return roles[0]!;
}

function requiredString(value: unknown, claim: string): string {
  if (typeof value !== "string" || value.length === 0) {
    throw new AuthError("TOKEN_INVALID", `Cognito token is missing ${claim}`);
  }
  return value;
}

function requiredPositiveInteger(value: unknown, claim: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value <= 0) {
    throw new AuthError("TOKEN_INVALID", `Cognito token has an invalid ${claim}`);
  }
  return value;
}

/**
 * Verifies only Cognito RS256 access tokens. The AWS verifier caches JWKS keys
 * and refreshes them on an unknown key ID; no user token is signed locally.
 */
export async function verifyAccessToken(token: string): Promise<AccessTokenClaims> {
  if (!token || token.length > 16_384) {
    throw new AuthError("TOKEN_INVALID", "Bearer token is malformed");
  }

  try {
    const payload = await (verifierOverride ?? accessVerifier()).verify(token);
    const exp = requiredPositiveInteger(payload["exp"], "expiry");
    if (exp <= Math.floor(Date.now() / 1000)) {
      throw new AuthError("TOKEN_EXPIRED", "Token has expired");
    }
    const groups = Array.isArray(payload["cognito:groups"])
      ? payload["cognito:groups"].filter((group): group is string => typeof group === "string")
      : [];
    return {
      sub: requiredString(payload["sub"], "subject"),
      exp,
      clientId: requiredString(payload["client_id"], "client ID"),
      username: typeof payload["username"] === "string" ? payload["username"] : null,
      groups,
    };
  } catch (error) {
    if (error instanceof AuthError) throw error;
    throw new AuthError("TOKEN_INVALID", "Cognito token verification failed");
  }
}

/** Test-only verifier injection; production cannot mint or bypass Cognito JWTs. */
export function setAccessTokenVerifierForTests(next: AccessTokenVerifier | null): void {
  if (process.env["NODE_ENV"] !== "test") {
    throw new Error("Test Cognito verifier injection is only available in NODE_ENV=test");
  }
  verifierOverride = next;
}
