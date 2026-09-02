import { randomBytes } from "node:crypto";
import {
  AdminAddUserToGroupCommand,
  AdminCreateUserCommand,
  AdminDisableUserCommand,
  AdminGetUserCommand,
  AdminInitiateAuthCommand,
  AdminListGroupsForUserCommand,
  AdminRespondToAuthChallengeCommand,
  AdminSetUserPasswordCommand,
  AdminUpdateUserAttributesCommand,
  CognitoIdentityProviderClient,
  GetTokensFromRefreshTokenCommand,
  RevokeTokenCommand,
} from "@aws-sdk/client-cognito-identity-provider";
import {
  AuthError,
  assertCognitoConfigured,
  roleFromCognitoGroups,
  verifyAccessToken,
  type TokenPair,
  type TokenUser,
} from "../auth.js";
import { config } from "../config.js";
import type { UserRole } from "../contracts.js";
import { getUserByCognitoSub, recordLastLogin, resolveCognitoUser } from "../repository.js";

type PublicRole = Extract<UserRole, "CLIENT" | "WORKER">;

type CognitoAuthResult = {
  AccessToken?: string;
  RefreshToken?: string;
  ExpiresIn?: number;
};

type CognitoUser = {
  sub: string;
  phone: string;
  role: UserRole;
};

export type OtpRequestResult = {
  challenge_id: string;
  expires_in_seconds: number;
  otp_length: number;
  delivery: { transport: "sms" };
};

function commandErrorName(error: unknown): string | null {
  return typeof error === "object" && error !== null && "name" in error && typeof error.name === "string"
    ? error.name
    : null;
}

function normalizeCognitoError(error: unknown): AuthError {
  const name = commandErrorName(error);
  if (name === "NotAuthorizedException" || name === "CodeMismatchException") {
    return new AuthError("OTP_INVALID", "The verification code is invalid or has expired");
  }
  if (name === "ExpiredCodeException") {
    return new AuthError("OTP_EXPIRED", "The verification code has expired");
  }
  if (name === "TooManyRequestsException" || name === "LimitExceededException") {
    return new AuthError("RATE_LIMITED", "Too many authentication attempts. Please try again shortly.", 429);
  }
  if (name === "UserNotConfirmedException" || name === "UserNotFoundException") {
    return new AuthError("AUTHENTICATION_FAILED", "The account could not be authenticated");
  }
  if (error instanceof AuthError) return error;
  return new AuthError("AUTH_UNAVAILABLE", "Authentication is temporarily unavailable", 503);
}

function attributeValue(attributes: Array<{ Name?: string; Value?: string }> | undefined, name: string): string | null {
  return attributes?.find((attribute) => attribute.Name === name)?.Value ?? null;
}

function generatedPassword(): string {
  // This permanent random password is never returned to a client or used for
  // login. Cognito Custom Auth is the only client-facing credential flow.
  return `${randomBytes(36).toString("base64url")}Aa1!`;
}

/**
 * Thin Cognito broker. It creates/starts Custom Auth challenges and forwards
 * Cognito-issued tokens, but never generates an OTP or signs a user token.
 */
export class AuthService {
  private readonly client: CognitoIdentityProviderClient;

  constructor(client = new CognitoIdentityProviderClient({ region: config.COGNITO_REGION })) {
    this.client = client;
  }

  async requestOtp(input: { phone: string; role?: PublicRole }): Promise<OtpRequestResult> {
    assertCognitoConfigured();
    try {
      await this.ensureCognitoUser(input.phone, input.role);
      const response = await this.client.send(new AdminInitiateAuthCommand({
        UserPoolId: config.COGNITO_USER_POOL_ID,
        ClientId: config.COGNITO_CLIENT_ID,
        AuthFlow: "CUSTOM_AUTH",
        AuthParameters: {
          USERNAME: input.phone,
          CHALLENGE_NAME: "CUSTOM_CHALLENGE",
        },
      }));
      if (response.ChallengeName !== "CUSTOM_CHALLENGE" || !response.Session) {
        throw new AuthError("AUTH_CHALLENGE_UNAVAILABLE", "The verification challenge could not be started", 503);
      }
      return {
        challenge_id: response.Session,
        expires_in_seconds: config.COGNITO_CHALLENGE_TTL_SECONDS,
        otp_length: 6,
        delivery: { transport: "sms" },
      };
    } catch (error) {
      throw normalizeCognitoError(error);
    }
  }

  async verifyOtpAndLogin(input: {
    phone: string;
    otp: string;
    challengeId: string;
  }): Promise<TokenPair> {
    assertCognitoConfigured();
    try {
      const response = await this.client.send(new AdminRespondToAuthChallengeCommand({
        UserPoolId: config.COGNITO_USER_POOL_ID,
        ClientId: config.COGNITO_CLIENT_ID,
        ChallengeName: "CUSTOM_CHALLENGE",
        ChallengeResponses: {
          USERNAME: input.phone,
          ANSWER: input.otp,
        },
        Session: input.challengeId,
      }));
      if (response.ChallengeName || !response.AuthenticationResult) {
        throw new AuthError("OTP_INVALID", "The verification code could not be accepted");
      }
      const cognitoUser = await this.readCognitoUser(input.phone);
      await this.client.send(new AdminUpdateUserAttributesCommand({
        UserPoolId: config.COGNITO_USER_POOL_ID,
        Username: input.phone,
        UserAttributes: [{ Name: "phone_number_verified", Value: "true" }],
      }));
      return this.toTokenPair(response.AuthenticationResult, cognitoUser);
    } catch (error) {
      throw normalizeCognitoError(error);
    }
  }

  async refresh(refreshToken: string): Promise<TokenPair> {
    assertCognitoConfigured();
    try {
      const response = await this.client.send(new GetTokensFromRefreshTokenCommand({
        ClientId: config.COGNITO_CLIENT_ID,
        RefreshToken: refreshToken,
      }));
      if (!response.AuthenticationResult) {
        throw new AuthError("TOKEN_INVALID", "Cognito did not return refreshed tokens");
      }
      return this.toTokenPair(response.AuthenticationResult, undefined, refreshToken);
    } catch (error) {
      throw normalizeCognitoError(error);
    }
  }

  async logout(refreshToken: string): Promise<void> {
    assertCognitoConfigured();
    try {
      await this.client.send(new RevokeTokenCommand({
        ClientId: config.COGNITO_CLIENT_ID,
        Token: refreshToken,
      }));
    } catch (error) {
      throw normalizeCognitoError(error);
    }
  }

  async disableCognitoUser(phone: string): Promise<void> {
    assertCognitoConfigured();
    try {
      await this.client.send(new AdminDisableUserCommand({
        UserPoolId: config.COGNITO_USER_POOL_ID,
        Username: phone,
      }));
    } catch (error) {
      throw normalizeCognitoError(error);
    }
  }

  private async ensureCognitoUser(phone: string, role?: PublicRole): Promise<void> {
    try {
      const user = await this.readCognitoUser(phone);
      if (role && user.role !== role) {
        throw new AuthError("ROLE_MISMATCH", "The requested role does not match this account", 403);
      }
      return;
    } catch (error) {
      if (commandErrorName(error) !== "UserNotFoundException") throw error;
    }

    if (!role) {
      throw new AuthError("ROLE_REQUIRED", "A role is required when creating a new account", 400);
    }

    let created = false;
    try {
      await this.client.send(new AdminCreateUserCommand({
        UserPoolId: config.COGNITO_USER_POOL_ID,
        Username: phone,
        MessageAction: "SUPPRESS",
        UserAttributes: [
          { Name: "phone_number", Value: phone },
          { Name: "phone_number_verified", Value: "false" },
        ],
      }));
      created = true;
    } catch (error) {
      if (commandErrorName(error) !== "UsernameExistsException") throw error;
    }

    if (!created) {
      const user = await this.readCognitoUser(phone);
      if (user.role !== role) {
        throw new AuthError("ROLE_MISMATCH", "The requested role does not match this account", 403);
      }
      return;
    }

    await this.client.send(new AdminSetUserPasswordCommand({
      UserPoolId: config.COGNITO_USER_POOL_ID,
      Username: phone,
      Password: generatedPassword(),
      Permanent: true,
    }));
    await this.client.send(new AdminAddUserToGroupCommand({
      UserPoolId: config.COGNITO_USER_POOL_ID,
      Username: phone,
      GroupName: role,
    }));
  }

  private async readCognitoUser(phone: string): Promise<CognitoUser> {
    const [user, groups] = await Promise.all([
      this.client.send(new AdminGetUserCommand({
        UserPoolId: config.COGNITO_USER_POOL_ID,
        Username: phone,
      })),
      this.client.send(new AdminListGroupsForUserCommand({
        UserPoolId: config.COGNITO_USER_POOL_ID,
        Username: phone,
      })),
    ]);
    const sub = attributeValue(user.UserAttributes, "sub");
    const configuredPhone = attributeValue(user.UserAttributes, "phone_number");
    if (!sub || !configuredPhone || configuredPhone !== phone) {
      throw new AuthError("IDENTITY_INVALID", "The Cognito account identity is invalid", 403);
    }
    return {
      sub,
      phone: configuredPhone,
      role: roleFromCognitoGroups((groups.Groups ?? []).map((group) => group.GroupName ?? "")),
    };
  }

  private async toTokenPair(
    result: CognitoAuthResult,
    expectedIdentity?: CognitoUser,
    fallbackRefreshToken?: string,
  ): Promise<TokenPair> {
    if (!result.AccessToken || !result.ExpiresIn) {
      throw new AuthError("AUTHENTICATION_FAILED", "Cognito did not return an access token");
    }
    const claims = await verifyAccessToken(result.AccessToken);
    const role = roleFromCognitoGroups(claims.groups);
    if (expectedIdentity && (claims.sub !== expectedIdentity.sub || role !== expectedIdentity.role)) {
      throw new AuthError("IDENTITY_INVALID", "Cognito token does not match the authenticated account", 403);
    }

    const user = expectedIdentity
      ? await resolveCognitoUser({ cognitoSub: claims.sub, phone: expectedIdentity.phone, role })
      : await getUserByCognitoSub(claims.sub);
    if (!user || !user.is_active || !user.is_verified || user.role !== role) {
      throw new AuthError("USER_NOT_AUTHORIZED", "User is not authorized", 403);
    }
    await recordLastLogin(user.id);
    const refreshToken = result.RefreshToken ?? fallbackRefreshToken;
    if (!refreshToken) {
      throw new AuthError("AUTHENTICATION_FAILED", "Cognito did not return a refresh token");
    }
    const tokenUser: TokenUser = { id: user.id, role: user.role, phone: user.phone_number };
    return {
      access_token: result.AccessToken,
      refresh_token: refreshToken,
      expires_in: result.ExpiresIn,
      user: tokenUser,
    };
  }
}

export const authService = new AuthService();
