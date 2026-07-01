import {
  Clock,
  Flame,
  Hammer,
  HeartCrack,
  Pickaxe,
  Route,
  Skull,
  Sword,
  Swords,
  Target,
  UserX,
  type LucideIcon,
} from 'lucide-react';
import type { PlayerStats } from '../api/types';
import { formatDistanceCm, formatNumber, formatTicks, humanizeKey } from './format';

/**
 * A headline metric shown as ranked cards/leaderboards. Most read a single field from
 * player.highlights, but some (e.g. K/D) are derived, so each metric carries its own value
 * resolver rather than a raw highlight key.
 */
export interface HighlightMetric {
  key: string;
  label: string;
  icon: LucideIcon;
  value: (p: PlayerStats) => number;
  format: (value: number) => string;
  higherIsBetter?: boolean;
}

/**
 * Player kill/death ratio. Uses PvP deaths (deaths caused by other players) and floors the
 * denominator at 1 so a player with kills and no PvP deaths still ranks. Matches the ratio shown
 * on the player profile's combat panel.
 */
export function playerKd(h: PlayerStats['highlights']): number {
  return h.playerKills / Math.max(1, h.deathsToPlayers);
}

// Damage stats are stored in tenths of a heart; scale to hearts for display.
export const HIGHLIGHT_METRICS: HighlightMetric[] = [
  { key: 'playTime', label: 'Play time', icon: Clock, value: (p) => p.highlights.playTime, format: formatTicks },
  { key: 'distanceCm', label: 'Distance', icon: Route, value: (p) => p.highlights.distanceCm, format: formatDistanceCm },
  { key: 'mobKills', label: 'Mob kills', icon: Sword, value: (p) => p.highlights.mobKills, format: formatNumber },
  { key: 'deaths', label: 'Deaths', icon: Skull, value: (p) => p.highlights.deaths, format: formatNumber, higherIsBetter: false },
  { key: 'deathsToPlayers', label: 'Deaths to players', icon: UserX, value: (p) => p.highlights.deathsToPlayers, format: formatNumber, higherIsBetter: false },
  { key: 'playerKills', label: 'Player kills', icon: Swords, value: (p) => p.highlights.playerKills, format: formatNumber },
  { key: 'blocksMined', label: 'Blocks mined', icon: Pickaxe, value: (p) => p.highlights.blocksMined, format: formatNumber },
  { key: 'itemsCrafted', label: 'Items crafted', icon: Hammer, value: (p) => p.highlights.itemsCrafted, format: formatNumber },
  { key: 'damageDealt', label: 'Damage dealt', icon: Flame, value: (p) => p.highlights.damageDealt, format: (v) => `${formatNumber(Math.round(v / 10))} ♥` },
  { key: 'damageTaken', label: 'Damage taken', icon: HeartCrack, value: (p) => p.highlights.damageTaken, format: (v) => `${formatNumber(Math.round(v / 10))} ♥` },
];

/**
 * Player kill/death ratio as a rankable metric. Derived (not a raw highlight), so it is surfaced on
 * the leaderboards via {@link LEADERBOARD_METRICS} rather than the profile rank cards, which already
 * show K/D in the combat panel.
 */
export const KD_METRIC: HighlightMetric = {
  key: 'kd',
  label: 'Player K/D',
  icon: Target,
  value: (p) => playerKd(p.highlights),
  format: (v) => v.toFixed(2),
};

/** Metrics selectable on the leaderboards page: the headline metrics plus derived ratios like K/D. */
export const LEADERBOARD_METRICS: HighlightMetric[] = [...HIGHLIGHT_METRICS, KD_METRIC];

export interface RankInfo {
  rank: number; // 1-based; 0 if not ranked (value 0)
  count: number; // number of players with a value > 0
  percentile: number; // 0..100, higher is better
}

/** Computes a player's rank and percentile for a metric across all players (descending). */
export function rankFor(players: PlayerStats[], value: (p: PlayerStats) => number, uuid: string): RankInfo {
  const values = players.map((p) => ({ uuid: p.uuid, v: value(p) })).filter((x) => x.v > 0);
  values.sort((a, b) => b.v - a.v);
  const count = values.length;
  const idx = values.findIndex((x) => x.uuid === uuid);
  if (idx < 0) return { rank: 0, count, percentile: 0 };
  const rank = idx + 1;
  // Top of the list = ~100th percentile.
  const percentile = count <= 1 ? 100 : Math.round(((count - rank) / (count - 1)) * 100);
  return { rank, count, percentile };
}

/** Movement-distance breakdown (in cm) from a player's custom stats, largest first. */
export function distanceBreakdown(stats: Record<string, Record<string, number>>): {
  key: string;
  label: string;
  value: number;
}[] {
  const custom = stats['minecraft:custom'] ?? {};
  const out: { key: string; label: string; value: number }[] = [];
  for (const [k, v] of Object.entries(custom)) {
    if (k.endsWith('_one_cm') && k !== 'minecraft:fall_one_cm' && v > 0) {
      out.push({ key: k, label: humanizeKey(k).replace(/ One Cm$/i, ''), value: v });
    }
  }
  out.sort((a, b) => b.value - a.value);
  return out;
}

/** Top N entries (key, value) of a stat-type map, largest first. */
export function topOf(
  stats: Record<string, Record<string, number>>,
  statType: string,
  n: number,
): { key: string; value: number }[] {
  const map = stats[statType] ?? {};
  return Object.entries(map)
    .filter(([, v]) => v > 0)
    .sort((a, b) => b[1] - a[1])
    .slice(0, n)
    .map(([key, value]) => ({ key, value }));
}

/** Sum of all values in a stat-type map. */
export function sumOf(stats: Record<string, Record<string, number>>, statType: string): number {
  return Object.values(stats[statType] ?? {}).reduce((s, v) => s + v, 0);
}
