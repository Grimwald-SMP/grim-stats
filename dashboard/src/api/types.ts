// Mirrors the JSON produced by the mod (dev.grimstats.data.model.*). Kept in one place so the
// whole UI shares a single source of truth for the API shape.

export interface DimensionInfo {
  id: string;
  time: number;
}

export interface WorldInfo {
  serverName: string;
  motd: string;
  minecraftVersion: string;
  onlinePlayers: number;
  maxPlayers: number;
  gameTime: number;
  dayTime: number;
  difficulty: string;
  hardcore: boolean;
  seed: number | null;
  dimensions: DimensionInfo[];
}

export interface Score {
  objective: string;
  value: number;
}

/** Derived "at a glance" metrics, present on every player even in summary list responses. */
export interface PlayerHighlights {
  playTime: number;
  deaths: number;
  // Deaths caused by other players (from the minecraft:killed_by -> minecraft:player stat).
  deathsToPlayers: number;
  mobKills: number;
  playerKills: number;
  distanceCm: number;
  blocksMined: number;
  itemsCrafted: number;
  damageDealt: number;
  damageTaken: number;
}

export type HighlightKey = keyof PlayerHighlights;

export interface PlayerStats {
  uuid: string;
  name: string;
  online: boolean;
  lastSeenEpochMs: number | null;
  highlights: PlayerHighlights;
  // statTypeId -> (statKey -> value). Empty on list endpoints (summary only).
  stats: Record<string, Record<string, number>>;
  scores: Score[];
}

export interface LeaderboardEntry {
  rank: number;
  uuid: string;
  name: string;
  value: number;
}

export interface Leaderboard {
  statType: string;
  statKey: string;
  count: number;
  total: number;
  entries: LeaderboardEntry[];
}

export interface ObjectiveEntry {
  holder: string;
  value: number;
}

export interface ObjectiveInfo {
  name: string;
  displayName: string;
  criterion: string;
  renderType: string;
  entries: ObjectiveEntry[];
}

export interface StatTypeInfo {
  id: string;
  translationKey: string;
  modded: boolean;
  keys: string[];
}

export interface PinnedEntry {
  uuid: string;
  name: string;
  value: number;
}

export interface PinnedStat {
  statType: string;
  statKey: string;
  entries: PinnedEntry[];
}

/** A reference to a single statistic, used in config to choose what is pinned. */
export interface PinnedRef {
  statType: string;
  statKey: string;
}

export interface StatsSnapshot {
  generatedAtEpochMs: number;
  world: WorldInfo;
  players: PlayerStats[];
  objectives: ObjectiveInfo[];
  statTypes: StatTypeInfo[];
  pinnedStats: PinnedStat[];
}

export interface ConfigView {
  host: string;
  port: number;
  corsAllowAll: boolean;
  corsOrigins: string[];
  defaultTheme: string;
  publicDashboard: boolean;
  hiddenStatTypes: string[];
  hiddenObjectives: string[];
  exposeSeed: boolean;
  pinned: PinnedRef[];
  snapshotIntervalSeconds: number;
  includeOfflinePlayers: boolean;
  maxPlayers: number;
  sessionTtlMinutes: number;
}

export type Role = 'ROOT' | 'ADMIN';

export interface Me {
  name: string;
  role: Role;
  kind: 'SESSION' | 'API_KEY';
}

export interface UserView {
  username: string;
  role: Role;
}

export interface ApiKeyView {
  id: string;
  name: string;
  role: Role;
  preview: string;
  createdBy: string;
  createdAtEpochMs: number;
  lastUsedEpochMs: number | null;
}

/** Returned once at creation; `key` is the full secret and is never retrievable again. */
export interface CreatedApiKey {
  key: string;
  id: string;
  name: string;
  role: Role;
}

/** Listing summary of an archived season (full player data is fetched per season). */
export interface SeasonInfo {
  id: string;
  name: string;
  createdAtEpochMs: number;
  serverName: string;
  minecraftVersion: string;
  gameTime: number;
  playerCount: number;
  totalPlayTimeTicks: number;
}

/** A frozen, portable archive of a world's stats (exported/imported as JSON). */
export interface Season {
  formatVersion: number;
  id: string;
  name: string;
  createdAtEpochMs: number;
  serverName: string;
  minecraftVersion: string;
  gameTime: number;
  players: PlayerStats[];
  objectives: ObjectiveInfo[];
}

export interface Health {
  status: string;
  mod: string;
  snapshotAge: number;
  players: number;
}
