import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import { z } from "zod";
import { authService } from "../services/auth-service.js";
import { AuthError, type TokenPair } from "../auth.js";
import { requireAuth } from "../middleware/auth.js";
import { ok, fail } from "../contracts.js";
import { parseBody } from "../utils/validation.js";
import { config } from "../config.js";
import { getUserProfile, updateUserProfile } from "../repository.js";

const phoneSchema = z
  .string()
  .trim()
  .regex(/^\+[1-9]\d{1,14}$/, "Phone number must be in E.164 format, e.g. +1234567890");

const transportSchema = z.enum(["browser", "native"]);
const requestOtpSchema = z.object({
  phone_number: phoneSchema,
  // The role is only used when creating a new public Cognito account. Cognito
  // groups remain authoritative for every existing account and on verification.
  role: z.enum(["CLIENT", "WORKER"]).optional(),
}).strict();

const verifyOtpSchema = z.object({
  phone_number: phoneSchema,
  challenge_id: z.string().min(1).max(8_192),
  otp: z.string().regex(/^\d{4,8}$/, "OTP must be 4-8 digits"),
  transport: transportSchema.default("native"),
}).strict();

const refreshSchema = z.object({
  refresh_token: z.string().min(1).optional(),
}).strict().default({});

const refreshCookieName = config.WEB_SESSION_COOKIE_NAME;

function cookieOptions() {
  const options = {
    httpOnly: true,
    maxAge: config.COGNITO_REFRESH_TTL_SECONDS,
    path: `${config.API_PREFIX}/auth`,
    sameSite: config.WEB_SESSION_COOKIE_SAME_SITE as "lax" | "none" | "strict",
    secure: config.WEB_SESSION_COOKIE_SECURE === "true",
  };
  return config.WEB_SESSION_COOKIE_DOMAIN ? { ...options, domain: config.WEB_SESSION_COOKIE_DOMAIN } : options;
}

function allowedBrowserOrigins(): Set<string> {
  return new Set(config.CORS_ORIGINS.split(",").map((origin) => origin.trim()).filter(Boolean));
}

function requireAllowedBrowserOrigin(request: FastifyRequest): void {
  const origin = request.headers.origin;
  if (!origin) {
    if (config.NODE_ENV === "production") {
      throw new AuthError("CSRF_ORIGIN_REQUIRED", "Browser authentication requests must include an Origin header", 403);
    }
    return;
  }
  if (!allowedBrowserOrigins().has(origin)) {
    throw new AuthError("CSRF_ORIGIN_INVALID", "Browser authentication request origin is not allowed", 403);
  }
}

function browserTokenResponse(tokens: TokenPair) {
  return {
    access_token: tokens.access_token,
    expires_in: tokens.expires_in,
    user: tokens.user,
  };
}

function refreshTokenFromRequest(
  request: FastifyRequest,
  body: { refresh_token?: string },
): { token: string; browser: boolean } {
  const cookieToken = request.cookies[refreshCookieName];
  if (cookieToken) {
    requireAllowedBrowserOrigin(request);
    return { token: cookieToken, browser: true };
  }
  if (!body.refresh_token) {
    throw new AuthError("REFRESH_TOKEN_MISSING", "A refresh token is required", 400);
  }
  return { token: body.refresh_token, browser: false };
}

export default async function authRoutes(app: FastifyInstance): Promise<void> {
  app.post("/auth/otp/request", async (request, reply) => {
    const body = parseBody(requestOtpSchema, request.body);
    if (!body.ok) {
      return reply.code(400).send(fail("VALIDATION_ERROR", body.message));
    }
    try {
      const result = await authService.requestOtp({
        phone: body.value.phone_number,
        role: body.value.role,
      });
      return ok(result);
    } catch (err) {
      return handleAuthError(request, reply, err);
    }
  });

  app.post("/auth/otp/verify", async (request, reply) => {
    const body = parseBody(verifyOtpSchema, request.body);
    if (!body.ok) {
      return reply.code(400).send(fail("VALIDATION_ERROR", body.message));
    }
    try {
      if (body.value.transport === "browser") requireAllowedBrowserOrigin(request);
      const result = await authService.verifyOtpAndLogin({
        phone: body.value.phone_number,
        otp: body.value.otp,
        challengeId: body.value.challenge_id,
      });
      if (body.value.transport === "browser") {
        reply.setCookie(refreshCookieName, result.refresh_token, cookieOptions());
        return ok(browserTokenResponse(result));
      }
      return ok(result);
    } catch (err) {
      return handleAuthError(request, reply, err);
    }
  });

  app.post("/auth/refresh", async (request, reply) => {
    const body = parseBody(refreshSchema, request.body);
    if (!body.ok) {
      return reply.code(400).send(fail("VALIDATION_ERROR", body.message));
    }
    try {
      const refresh = refreshTokenFromRequest(request, body.value);
      const result = await authService.refresh(refresh.token);
      if (refresh.browser) {
        reply.setCookie(refreshCookieName, result.refresh_token, cookieOptions());
        return ok(browserTokenResponse(result));
      }
      return ok(result);
    } catch (err) {
      return handleAuthError(request, reply, err);
    }
  });

  app.post("/auth/logout", async (request, reply) => {
    const body = parseBody(refreshSchema, request.body);
    if (!body.ok) {
      return reply.code(400).send(fail("VALIDATION_ERROR", body.message));
    }
    let browser = false;
    try {
      const refresh = refreshTokenFromRequest(request, body.value);
      browser = refresh.browser;
      await authService.logout(refresh.token);
      return ok({ logged_out: true });
    } catch (err) {
      return handleAuthError(request, reply, err);
    } finally {
      // Clear a stale browser credential even when Cognito already revoked it.
      if (browser) reply.clearCookie(refreshCookieName, cookieOptions());
    }
  });

  app.get("/auth/me", { onRequest: [requireAuth] }, async (request) => {
    return ok({
      id: request.auth.userId,
      role: request.auth.role,
      phone: request.auth.phone,
    });
  });

  app.get("/auth/profile", { onRequest: [requireAuth] }, async (request, reply) => {
    const profile = await getUserProfile(request.auth.userId);
    if (!profile) {
      return reply.code(404).send(fail("USER_NOT_FOUND", "User profile not found"));
    }
    return ok(profile);
  });

  const updateProfileSchema = z
    .object({
      email: z.string().email().nullable().optional(),
      avatar_url: z.string().url().nullable().optional(),
      skills: z.array(z.string().max(50)).max(20).optional(),
      preferred_radius_km: z.number().int().min(1).max(200).optional(),
      is_available: z.boolean().optional(),
    })
    .strict();

  app.patch("/auth/profile", { onRequest: [requireAuth] }, async (request, reply) => {
    const body = parseBody(updateProfileSchema, request.body);
    if (!body.ok) {
      return reply.code(400).send(fail("VALIDATION_ERROR", body.message));
    }
    const updated = await updateUserProfile(request.auth.userId, {
      email: body.value.email,
      avatarUrl: body.value.avatar_url,
      skills: body.value.skills,
      preferredRadiusKm: body.value.preferred_radius_km,
      isAvailable: body.value.is_available,
    });
    if (!updated) {
      return reply.code(404).send(fail("USER_NOT_FOUND", "User profile not found"));
    }
    return ok(updated);
  });
}

function handleAuthError(request: FastifyRequest, reply: FastifyReply, err: unknown): unknown {
  if (err instanceof AuthError) {
    request.log.warn({ code: err.code }, "authentication request rejected");
    if (err.statusCode === 429) reply.header("Retry-After", "60");
    return reply.code(err.statusCode).send(fail(err.code, err.message));
  }

  request.log.error({ err }, "authentication request failed");
  return reply.code(500).send(fail("INTERNAL_SERVER_ERROR", "An internal server error occurred"));
}
