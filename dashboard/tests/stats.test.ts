import { describe, expect, it } from 'vitest';
import type { PlayerHighlights, PlayerStats } from '../src/api/types';
import { KD_METRIC, distanceBreakdown, playerKd, rankFor, sumOf, topOf } from '../src/lib/stats';

function player(uuid: string, highlights: Partial<PlayerHighlights>, stats: PlayerStats['stats'] = {}): PlayerStats {
  return {
    uuid,
    name: uuid,
    online: false,
    lastSeenEpochMs: null,
    highlights: {
      playTime: 0,
      deaths: 0,
      deathsToPlayers: 0,
      mobKills: 0,
      playerKills: 0,
      distanceCm: 0,
      blocksMined: 0,
      itemsCrafted: 0,
      damageDealt: 0,
      damageTaken: 0,
      ...highlights,
    },
    stats,
    scores: [],
  };
}

describe('rankFor', () => {
  const players = [
    player('a', { mobKills: 100 }),
    player('b', { mobKills: 50 }),
    player('c', { mobKills: 10 }),
    player('d', { mobKills: 0 }),
  ];

  it('ranks descending and computes percentile', () => {
    expect(rankFor(players, (p) => p.highlights.mobKills, 'a')).toEqual({ rank: 1, count: 3, percentile: 100 });
    expect(rankFor(players, (p) => p.highlights.mobKills, 'c')).toEqual({ rank: 3, count: 3, percentile: 0 });
  });

  it('returns unranked for zero values', () => {
    expect(rankFor(players, (p) => p.highlights.mobKills, 'd')).toEqual({ rank: 0, count: 3, percentile: 0 });
  });

  it('handles a single ranked player as top percentile', () => {
    expect(rankFor([player('x', { deaths: 5 })], (p) => p.highlights.deaths, 'x')).toEqual({
      rank: 1,
      count: 1,
      percentile: 100,
    });
  });
});

describe('playerKd', () => {
  it('divides player kills by PvP deaths', () => {
    expect(playerKd(player('a', { playerKills: 10, deathsToPlayers: 4 }).highlights)).toBe(2.5);
  });

  it('floors the denominator at 1 so kills with no PvP deaths still rank', () => {
    expect(playerKd(player('a', { playerKills: 7, deathsToPlayers: 0 }).highlights)).toBe(7);
  });

  it('is the value backing the K/D leaderboard metric, formatted to two decimals', () => {
    const p = player('a', { playerKills: 3, deathsToPlayers: 2 });
    expect(KD_METRIC.value(p)).toBe(1.5);
    expect(KD_METRIC.format(KD_METRIC.value(p))).toBe('1.50');
  });
});

describe('distanceBreakdown', () => {
  it('collects movement distances, excludes fall, sorts desc', () => {
    const stats = {
      'minecraft:custom': {
        'minecraft:walk_one_cm': 500,
        'minecraft:sprint_one_cm': 1500,
        'minecraft:fall_one_cm': 9999,
        'minecraft:jump': 42,
      },
    };
    const out = distanceBreakdown(stats);
    expect(out.map((d) => d.key)).toEqual(['minecraft:sprint_one_cm', 'minecraft:walk_one_cm']);
    expect(out[0].label).toBe('Sprint');
  });
});

describe('topOf / sumOf', () => {
  const stats = { 'minecraft:mined': { 'minecraft:stone': 300, 'minecraft:dirt': 100, 'minecraft:air': 0 } };
  it('returns top entries by value, excluding zero', () => {
    expect(topOf(stats, 'minecraft:mined', 2)).toEqual([
      { key: 'minecraft:stone', value: 300 },
      { key: 'minecraft:dirt', value: 100 },
    ]);
  });
  it('sums a stat type', () => {
    expect(sumOf(stats, 'minecraft:mined')).toBe(400);
    expect(sumOf(stats, 'minecraft:nope')).toBe(0);
  });
});
