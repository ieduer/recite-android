CREATE TABLE IF NOT EXISTS recite_ranking_snapshots (
  user_key TEXT PRIMARY KEY,
  public_name TEXT NOT NULL,
  total_points INTEGER NOT NULL DEFAULT 0 CHECK (total_points BETWEEN 0 AND 390),
  daily_points INTEGER NOT NULL DEFAULT 0 CHECK (daily_points BETWEEN 0 AND 390),
  completed_pieces INTEGER NOT NULL DEFAULT 0 CHECK (completed_pieces BETWEEN 0 AND 78),
  active_pieces INTEGER NOT NULL DEFAULT 0 CHECK (active_pieces BETWEEN 0 AND 78),
  day_key TEXT NOT NULL,
  source_updated_at TEXT NOT NULL DEFAULT '',
  synced_at_ms INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_recite_rankings_total
  ON recite_ranking_snapshots(total_points DESC, completed_pieces DESC, updated_at ASC);

CREATE INDEX IF NOT EXISTS idx_recite_rankings_daily
  ON recite_ranking_snapshots(day_key, daily_points DESC, total_points DESC, updated_at ASC);
