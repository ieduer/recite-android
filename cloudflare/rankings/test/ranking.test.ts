import { describe, expect, it } from "vitest";
import {
  beijingDayKey,
  rankForPoints,
  summarizeProgress,
} from "../src/ranking";

describe("rankForPoints", () => {
  it("assigns Palace and Peak only at their thresholds", () => {
    expect(rankForPoints(314).frame).toBe("standard");
    expect(rankForPoints(315).name).toBe("殿堂");
    expect(rankForPoints(315).frame).toBe("palace");
    expect(rankForPoints(375).name).toBe("巔峰");
    expect(rankForPoints(390).frame).toBe("peak");
  });
});

describe("summarizeProgress", () => {
  it("deduplicates pieces and trusts only bounded Recite progress fields", () => {
    const summary = summarizeProgress({
      items: [
        { itemKey: "p1", state: "in_progress", meta: { stage: 2, lastActivityAt: "2026-07-29T01:00:00Z" } },
        { itemKey: "p1", state: "completed", meta: { stage: 5, sealed: true, lastActivityAt: "2026-07-29T02:00:00Z" } },
        { itemKey: "p2", progressPercent: 40, meta: {} },
        { itemKey: "other", state: "completed", meta: { stage: 5 } },
        { itemKey: "p3", meta: { stage: 99 } },
      ],
    });

    expect(summary.totalPoints).toBe(12);
    expect(summary.completedPieces).toBe(2);
    expect(summary.activePieces).toBe(3);
    expect(summary.sourceUpdatedAt).toBe("2026-07-29T02:00:00Z");
  });

  it("does not grant points for malformed payloads", () => {
    expect(summarizeProgress(null).totalPoints).toBe(0);
    expect(summarizeProgress({ items: "not-an-array" }).activePieces).toBe(0);
  });
});

describe("beijingDayKey", () => {
  it("uses the Beijing calendar boundary", () => {
    expect(beijingDayKey(new Date("2026-07-28T16:30:00Z"))).toBe("2026-07-29");
  });
});
