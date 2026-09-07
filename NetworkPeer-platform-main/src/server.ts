import "./lib/error-capture";

import { consumeLastCapturedError } from "./lib/error-capture";
import { renderErrorPage } from "./lib/error-page";

type ServerEntry = {
  fetch: (request: Request, env: unknown, ctx: unknown) => Promise<Response> | Response;
};

let serverEntryPromise: Promise<ServerEntry> | undefined;

async function getServerEntry(): Promise<ServerEntry> {
  if (!serverEntryPromise) {
    serverEntryPromise = import("@tanstack/react-start/server-entry").then(
      (m) => (m.default ?? m) as ServerEntry,
    );
  }
  return serverEntryPromise;
}

// h3 swallows in-handler throws into a normal 500 Response with body
// {"unhandled":true,"message":"HTTPError"} — try/catch alone never fires for those.
async function normalizeCatastrophicSsrResponse(response: Response): Promise<Response> {
  if (response.status < 500) return response;
  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) return response;

  const body = await response.clone().text();
  if (!isH3SwallowedErrorBody(body)) return response;

  console.error(consumeLastCapturedError() ?? new Error(`h3 swallowed SSR error: ${body}`));
  return new Response(renderErrorPage(), {
    status: 500,
    headers: { "content-type": "text/html; charset=utf-8" },
  });
}

function isH3SwallowedErrorBody(body: string): boolean {
  try {
    const payload = JSON.parse(body) as { unhandled?: unknown; message?: unknown };
    return payload.unhandled === true && payload.message === "HTTPError";
  } catch {
    return false;
  }
}

const ALB_ORIGIN = "http://networkpeer-staging-api-alb-969746120.eu-north-1.elb.amazonaws.com";

export default {
  async fetch(request: Request, env: unknown, ctx: unknown) {
    const url = new URL(request.url);

    // Proxy /api/v1/* requests directly to the AWS ALB backend to eliminate browser Mixed Content issues
    if (url.pathname.startsWith("/api/v1")) {
      const targetUrl = new URL(url.pathname + url.search, ALB_ORIGIN);
      const headers = new Headers(request.headers);
      headers.set("host", targetUrl.host);

      try {
        const body =
          request.method !== "GET" && request.method !== "HEAD"
            ? await request.arrayBuffer()
            : undefined;

        const albResponse = await fetch(targetUrl.toString(), {
          method: request.method,
          headers,
          body,
          redirect: "manual",
        });

        const responseHeaders = new Headers(albResponse.headers);
        responseHeaders.set("access-control-allow-origin", "*");
        responseHeaders.set("access-control-allow-methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
        responseHeaders.set("access-control-allow-headers", "Content-Type,Authorization,X-Requested-With");

        return new Response(albResponse.body, {
          status: albResponse.status,
          statusText: albResponse.statusText,
          headers: responseHeaders,
        });
      } catch (err) {
        console.error("ALB Proxy Error:", err);
        return new Response(
          JSON.stringify({
            success: false,
            data: null,
            error: { code: "PROXY_ERROR", message: "Failed to connect to backend service" },
          }),
          {
            status: 502,
            headers: { "content-type": "application/json" },
          },
        );
      }
    }

    try {
      const handler = await getServerEntry();
      const response = await handler.fetch(request, env, ctx);
      return await normalizeCatastrophicSsrResponse(response);
    } catch (error) {
      console.error(error);
      return new Response(renderErrorPage(), {
        status: 500,
        headers: { "content-type": "text/html; charset=utf-8" },
      });
    }
  },
};
