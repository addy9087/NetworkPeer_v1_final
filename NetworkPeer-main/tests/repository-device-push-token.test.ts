import { afterEach, describe, expect, it, vi } from "vitest";

const query = vi.hoisted(() => vi.fn());

vi.mock("../src/db.js", () => ({
  pool: { query },
  adminPool: {},
  financialPool: {},
  mediaVerifierPool: {},
}));

import { deactivateDevicePushTokenForUser } from "../src/repository.js";

afterEach(() => {
  query.mockReset();
});

describe("deactivateDevicePushTokenForUser", () => {
  it("limits token deactivation to the supplied owner", async () => {
    query.mockResolvedValue({ rowCount: 1 });

    await expect(deactivateDevicePushTokenForUser("owner-id", "device-token")).resolves.toBe(true);

    const [sql, values] = query.mock.calls[0] as [string, unknown[]];
    expect(sql).toContain("WHERE user_id = $1");
    expect(sql).toContain("AND token = $2");
    expect(sql).toContain("AND is_active = TRUE");
    expect(values).toEqual(["owner-id", "device-token"]);
  });

  it("reports false without changing a missing, foreign, or already inactive token", async () => {
    query.mockResolvedValue({ rowCount: 0 });

    await expect(deactivateDevicePushTokenForUser("owner-id", "device-token")).resolves.toBe(false);
  });
});
