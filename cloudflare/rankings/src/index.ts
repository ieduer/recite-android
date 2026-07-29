import {
  beijingDayKey,
  MAX_PIECES,
  MAX_POINTS,
  rankForPoints,
  summarizeProgress,
} from "./ranking";

const USER_CENTER_ORIGIN = "https://my.bdfz.net";
const MAX_LIMIT = 30;
const MIN_SYNC_INTERVAL_MS = 10_000;

interface SessionUser {
  slug: string;
}

interface RankingRow {
  user_key: string;
  public_name: string;
  total_points: number;
  daily_points: number;
  completed_pieces: number;
  active_pieces: number;
  day_key: string;
  source_updated_at: string;
  synced_at_ms: number;
  updated_at: string;
  position: number;
}

interface PublicEntry {
  position: number;
  displayName: string;
  totalPoints: number;
  todayPoints: number;
  completedPieces: number;
  activePieces: number;
  rankName: string;
  frame: string;
  isMe: boolean;
}

function json(payload: unknown, status = 200, cacheControl = "no-store"): Response {
  return Response.json(payload, {
    status,
    headers: {
      "cache-control": cacheControl,
      "content-type": "application/json; charset=utf-8",
      "x-content-type-options": "nosniff",
    },
  });
}

function cookieHeader(request: Request): string {
  return (request.headers.get("Cookie") || "").slice(0, 4_096);
}

function boundedLimit(url: URL): number {
  const parsed = Number(url.searchParams.get("limit") || 20);
  return Number.isFinite(parsed)
    ? Math.min(MAX_LIMIT, Math.max(1, Math.trunc(parsed)))
    : 20;
}

async function userCenterJson(
  env: Env,
  path: string,
  cookie: string,
): Promise<unknown> {
  const response = await env.USER_CENTER.fetch(
    new Request(`${USER_CENTER_ORIGIN}${path}`, {
      headers: {
        accept: "application/json",
        cookie,
      },
    }),
  );
  if (!response.ok) {
    throw new Error(`user_center_${response.status}`);
  }
  return response.json<unknown>();
}

function sessionUser(payload: unknown): SessionUser | null {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return null;
  const root = payload as Record<string, unknown>;
  if (root.authenticated !== true || !root.user || typeof root.user !== "object") {
    return null;
  }
  const user = root.user as Record<string, unknown>;
  const slug = typeof user.slug === "string" ? user.slug.trim().slice(0, 96) : "";
  return slug ? { slug } : null;
}

async function hmacUserKey(slug: string, pepper: string): Promise<string> {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(pepper),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(slug));
  return [...new Uint8Array(signature)]
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function publicName(userKey: string): string {
  return `學子·${userKey.slice(0, 4).toUpperCase()}`;
}

async function authenticate(
  request: Request,
  env: Env,
): Promise<{ userKey: string; cookie: string } | null> {
  const cookie = cookieHeader(request);
  if (!cookie.includes("bdfz_uc_session=")) return null;
  const user = sessionUser(await userCenterJson(env, "/api/session", cookie));
  if (!user) return null;
  const userKey = await hmacUserKey(user.slug, env.RANKING_PEPPER);
  return { userKey, cookie };
}

function publicEntry(row: RankingRow, meKey: string): PublicEntry {
  const rank = rankForPoints(row.total_points);
  return {
    position: Number(row.position || 0),
    displayName: row.public_name,
    totalPoints: Number(row.total_points || 0),
    todayPoints: Number(row.daily_points || 0),
    completedPieces: Number(row.completed_pieces || 0),
    activePieces: Number(row.active_pieces || 0),
    rankName: rank.name,
    frame: rank.frame,
    isMe: Boolean(meKey && row.user_key === meKey),
  };
}

async function loadBoard(
  env: Env,
  dayKey: string,
  limit: number,
  meKey = "",
): Promise<{
  daily: PublicEntry[];
  total: PublicEntry[];
  meDaily: PublicEntry | null;
  meTotal: PublicEntry | null;
}> {
  const dailySql = `
    SELECT *, ROW_NUMBER() OVER (
      ORDER BY daily_points DESC, total_points DESC, completed_pieces DESC, updated_at ASC
    ) AS position
      FROM recite_ranking_snapshots
     WHERE day_key = ? AND daily_points > 0
  `;
  const totalSql = `
    SELECT *, ROW_NUMBER() OVER (
      ORDER BY total_points DESC, completed_pieces DESC, active_pieces DESC, updated_at ASC
    ) AS position
      FROM recite_ranking_snapshots
     WHERE total_points > 0
  `;
  const [dailyResult, totalResult] = await Promise.all([
    env.DB.prepare(`${dailySql} LIMIT ?`).bind(dayKey, limit).all<RankingRow>(),
    env.DB.prepare(`${totalSql} LIMIT ?`).bind(limit).all<RankingRow>(),
  ]);
  const dailyRows = dailyResult.results || [];
  const totalRows = totalResult.results || [];
  const dailyMeInPage = dailyRows.find((row) => row.user_key === meKey);
  const totalMeInPage = totalRows.find((row) => row.user_key === meKey);

  let meDaily: PublicEntry | null =
    dailyMeInPage ? publicEntry(dailyMeInPage, meKey) : null;
  let meTotal: PublicEntry | null =
    totalMeInPage ? publicEntry(totalMeInPage, meKey) : null;

  if (meKey && (!meDaily || !meTotal)) {
    const [dailyMe, totalMe] = await Promise.all([
      env.DB.prepare(`SELECT * FROM (${dailySql}) WHERE user_key = ?`)
        .bind(dayKey, meKey)
        .all<RankingRow>(),
      env.DB.prepare(`SELECT * FROM (${totalSql}) WHERE user_key = ?`)
        .bind(meKey)
        .all<RankingRow>(),
    ]);
    const dailyRow = dailyMe.results?.[0];
    const totalRow = totalMe.results?.[0];
    if (dailyRow) meDaily = publicEntry(dailyRow, meKey);
    if (totalRow) meTotal = publicEntry(totalRow, meKey);
  }

  return {
    daily: dailyRows.map((row) => publicEntry(row, meKey)),
    total: totalRows.map((row) => publicEntry(row, meKey)),
    meDaily,
    meTotal,
  };
}

async function syncCurrentUser(
  env: Env,
  auth: { userKey: string; cookie: string },
  nowMs: number,
): Promise<boolean> {
  const existing = await env.DB.prepare(
    "SELECT synced_at_ms FROM recite_ranking_snapshots WHERE user_key = ?",
  ).bind(auth.userKey).first<{ synced_at_ms: number }>();
  if (existing && nowMs - Number(existing.synced_at_ms || 0) < MIN_SYNC_INTERVAL_MS) {
    return false;
  }

  const progressPayload = await userCenterJson(
    env,
    "/api/progress?site=recite",
    auth.cookie,
  );
  const summary = summarizeProgress(progressPayload);
  const dayKey = beijingDayKey(new Date(nowMs));
  await env.DB.prepare(
    `INSERT INTO recite_ranking_snapshots (
       user_key, public_name, total_points, daily_points, completed_pieces,
       active_pieces, day_key, source_updated_at, synced_at_ms, updated_at
     ) VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
     ON CONFLICT(user_key) DO UPDATE SET
       public_name = excluded.public_name,
       daily_points = CASE
         WHEN recite_ranking_snapshots.day_key = excluded.day_key
         THEN MIN(?, recite_ranking_snapshots.daily_points
           + MAX(0, excluded.total_points - recite_ranking_snapshots.total_points))
         ELSE MAX(0, excluded.total_points - recite_ranking_snapshots.total_points)
       END,
       total_points = MAX(recite_ranking_snapshots.total_points, excluded.total_points),
       completed_pieces = MAX(recite_ranking_snapshots.completed_pieces, excluded.completed_pieces),
       active_pieces = MAX(recite_ranking_snapshots.active_pieces, excluded.active_pieces),
       day_key = excluded.day_key,
       source_updated_at = MAX(recite_ranking_snapshots.source_updated_at, excluded.source_updated_at),
       synced_at_ms = excluded.synced_at_ms,
       updated_at = CURRENT_TIMESTAMP`,
  ).bind(
    auth.userKey,
    publicName(auth.userKey),
    summary.totalPoints,
    summary.completedPieces,
    summary.activePieces,
    dayKey,
    summary.sourceUpdatedAt,
    nowMs,
    MAX_POINTS,
  ).run();
  return true;
}

async function handleBoard(request: Request, env: Env, sync: boolean): Promise<Response> {
  const url = new URL(request.url);
  const auth = await authenticate(request, env);
  if (sync && !auth) {
    return json({ ok: false, error: "login-required" }, 401);
  }
  const nowMs = Date.now();
  const syncAccepted = auth && sync
    ? await syncCurrentUser(env, auth, nowMs)
    : false;
  const dayKey = beijingDayKey(new Date(nowMs));
  const board = await loadBoard(env, dayKey, boundedLimit(url), auth?.userKey || "");
  return json({
    schemaVersion: "recite-rankings-v1",
    ok: true,
    period: { dayKey, timeZone: "Asia/Shanghai" },
    maxPoints: MAX_POINTS,
    maxPieces: MAX_PIECES,
    syncAccepted,
    ...board,
    generatedAt: new Date(nowMs).toISOString(),
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const requestId = crypto.randomUUID();
    const url = new URL(request.url);
    try {
      if (url.pathname === "/api/rankings/health"
        && (request.method === "GET" || request.method === "HEAD")) {
        const response = json({
          ok: true,
          service: "recite-rankings",
          schemaVersion: "recite-rankings-v1",
        }, 200, "public, max-age=30");
        return request.method === "HEAD"
          ? new Response(null, { status: response.status, headers: response.headers })
          : response;
      }
      if (url.pathname !== "/api/rankings") {
        return json({ ok: false, error: "not-found" }, 404);
      }
      if (request.method === "GET") {
        return await handleBoard(request, env, false);
      }
      if (request.method === "POST") {
        return await handleBoard(request, env, true);
      }
      return json({ ok: false, error: "method-not-allowed" }, 405);
    } catch (error) {
      console.error(JSON.stringify({
        event: "recite_rankings_request_failed",
        requestId,
        path: url.pathname,
        method: request.method,
        error: error instanceof Error ? error.message.slice(0, 120) : "unknown",
      }));
      return json({ ok: false, error: "rankings-unavailable", requestId }, 503);
    }
  },
} satisfies ExportedHandler<Env>;
