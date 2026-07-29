export const MAX_POINTS = 390;
export const MAX_PIECES = 78;

export const RANKS = Object.freeze([
  Object.freeze({ name: "初識", minimumPoints: 0, frame: "standard" }),
  Object.freeze({ name: "啟聲", minimumPoints: 20, frame: "standard" }),
  Object.freeze({ name: "尋章", minimumPoints: 55, frame: "standard" }),
  Object.freeze({ name: "知音", minimumPoints: 105, frame: "standard" }),
  Object.freeze({ name: "博聞", minimumPoints: 170, frame: "standard" }),
  Object.freeze({ name: "文心", minimumPoints: 240, frame: "standard" }),
  Object.freeze({ name: "殿堂", minimumPoints: 315, frame: "palace" }),
  Object.freeze({ name: "巔峰", minimumPoints: 375, frame: "peak" }),
]);

export type RankFrame = "standard" | "palace" | "peak";

export interface RankDefinition {
  name: string;
  minimumPoints: number;
  frame: RankFrame;
}

export interface ProgressSummary {
  totalPoints: number;
  completedPieces: number;
  activePieces: number;
  sourceUpdatedAt: string;
}

interface UnknownRecord {
  [key: string]: unknown;
}

function record(value: unknown): UnknownRecord {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as UnknownRecord
    : {};
}

function boundedInteger(value: unknown, minimum: number, maximum: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed)
    ? Math.min(maximum, Math.max(minimum, Math.trunc(parsed)))
    : minimum;
}

function safeTime(value: unknown): string {
  const text = typeof value === "string" ? value.trim().slice(0, 40) : "";
  return Number.isFinite(Date.parse(text)) ? text : "";
}

export function rankForPoints(points: number): RankDefinition {
  const bounded = boundedInteger(points, 0, MAX_POINTS);
  return [...RANKS].reverse().find((rank) => bounded >= rank.minimumPoints) ?? RANKS[0]!;
}

export function summarizeProgress(payload: unknown): ProgressSummary {
  const root = record(payload);
  const rawItems = Array.isArray(root.items) ? root.items.slice(0, MAX_PIECES * 2) : [];
  const bestStageByItem = new Map<string, { stage: number; updatedAt: string }>();

  for (const rawItem of rawItems) {
    const item = record(rawItem);
    const itemKey = typeof item.itemKey === "string"
      ? item.itemKey.trim().slice(0, 24)
      : "";
    if (!/^p\d{1,3}$/.test(itemKey)) continue;
    const meta = record(item.meta);
    const state = typeof item.state === "string" ? item.state.toLowerCase() : "";
    const progressPercent = boundedInteger(
      meta.progressPercent ?? item.progressPercent ?? item.score,
      0,
      100,
    );
    const sealed = meta.sealed === true
      || ["completed", "complete", "done", "passed"].includes(state)
      || progressPercent >= 100;
    const stage = sealed
      ? 5
      : Math.max(
        boundedInteger(meta.stage, 0, 5),
        Math.floor(progressPercent / 20),
      );
    const updatedAt = safeTime(
      meta.lastActivityAt ?? item.lastActivityAt ?? item.updatedAt,
    );
    const existing = bestStageByItem.get(itemKey);
    if (!existing || stage > existing.stage || updatedAt > existing.updatedAt) {
      bestStageByItem.set(itemKey, { stage, updatedAt });
    }
  }

  const values = [...bestStageByItem.values()];
  return {
    totalPoints: Math.min(MAX_POINTS, values.reduce((sum, item) => sum + item.stage, 0)),
    completedPieces: values.filter((item) => item.stage >= 5).length,
    activePieces: values.filter((item) => item.stage > 0).length,
    sourceUpdatedAt: values.reduce(
      (latest, item) => item.updatedAt > latest ? item.updatedAt : latest,
      "",
    ),
  };
}

export function beijingDayKey(now = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
}
