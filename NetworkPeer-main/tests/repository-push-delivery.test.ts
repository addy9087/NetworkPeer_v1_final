import { afterEach, describe, expect, it, vi } from "vitest";

const query = vi.hoisted(() => vi.fn());

vi.mock("../src/db.js", () => ({
  pool: { query },
  adminPool: {},
  financialPool: {},
  mediaVerifierPool: {},
}));

import {
  claimPushDelivery,
  listPushDeliveryCandidates,
  releasePushDelivery,
} from "../src/repository.js";

afterEach(() => {
  query.mockReset();
});

describe("push delivery durable retry scheduling", () => {
  it("lists and claims pending deliveries only after their persisted due time", async () => {
    query
      .mockResolvedValueOnce({ rows: [] })
      .mockResolvedValueOnce({ rows: [] })
      .mockResolvedValueOnce({ rows: [{ cursor: "42" }] })
      .mockResolvedValueOnce({ rows: [] });

    await expect(listPushDeliveryCandidates(25)).resolves.toEqual(["42"]);
    await expect(claimPushDelivery("42")).resolves.toBeNull();

    const [listSql] = query.mock.calls[2] as [string];
    const [claimSql] = query.mock.calls[3] as [string];
    expect(listSql).toContain("push_state = 'PENDING' AND push_not_before_at <= NOW()");
    expect(claimSql).toContain("se.push_state = 'PENDING' AND se.push_not_before_at <= NOW()");
    expect(claimSql).toContain("push_not_before_at = NOW()");
  });

  it("persists the exact retry delay when releasing a transient claim", async () => {
    query.mockResolvedValue({ rows: [] });

    await releasePushDelivery("42", "FCM send failed: 429", 120_000);

    const [sql, values] = query.mock.calls[0] as [string, unknown[]];
    expect(sql).toContain("NOW() + ($3::bigint * INTERVAL '1 millisecond')");
    expect(values).toEqual(["42", "FCM send failed: 429", 120_000]);
  });
});
