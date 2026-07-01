import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client';
import type { PlayerStats } from '../api/types';
import { useDashboard } from '../components/Layout';
import { BarList, Donut, Radar, RankBadge } from '../components/charts';
import { Avatar, EmptyState, Loading, SectionTitle } from '../components/ui';
import {
  formatCustomValue,
  formatDistanceCm,
  formatNumber,
  humanizeKey,
  relativeTime,
  statTypeLabel,
} from '../lib/format';
import { HIGHLIGHT_METRICS, distanceBreakdown, playerKd, rankFor, topOf } from '../lib/stats';

const RADAR_METRICS: { key: keyof PlayerStats['highlights']; label: string }[] = [
  { key: 'playTime', label: 'Time' },
  { key: 'distanceCm', label: 'Distance' },
  { key: 'mobKills', label: 'Combat' },
  { key: 'blocksMined', label: 'Mining' },
  { key: 'itemsCrafted', label: 'Crafting' },
  { key: 'damageDealt', label: 'Damage' },
];

export function PlayerDetail() {
  const { uuid = '' } = useParams();
  const { snapshot } = useDashboard();
  const [player, setPlayer] = useState<PlayerStats | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setPlayer(null);
    setError(null);
    api
      .player(uuid)
      .then((p) => !cancelled && setPlayer(p))
      .catch((e) => !cancelled && setError(String(e)));
    return () => {
      cancelled = true;
    };
  }, [uuid]);

  const allPlayers = snapshot?.players ?? [];
  // Server max per radar metric, for normalization.
  const radarMax = useMemo(() => {
    const m: Record<string, number> = {};
    for (const r of RADAR_METRICS) m[r.key] = Math.max(1, ...allPlayers.map((p) => p.highlights[r.key]));
    return m;
  }, [allPlayers]);

  if (error) return <EmptyState title="Could not load player" hint={error} />;
  if (!player) return <Loading label="Loading player…" />;

  const h = player.highlights;
  const distance = distanceBreakdown(player.stats);
  const topMined = topOf(player.stats, 'minecraft:mined', 8);
  const topKilled = topOf(player.stats, 'minecraft:killed', 8);
  const kd = playerKd(h);

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Link to="/players" className="btn btn-ghost btn-sm btn-square" aria-label="Back">
          ‹
        </Link>
        <Avatar uuid={player.uuid} name={player.name} size={48} />
        <div className="min-w-0">
          <h1 className="truncate text-2xl font-semibold">{player.name}</h1>
          <p className="text-xs text-base-content/50">
            {player.online ? 'online now' : `last seen ${relativeTime(player.lastSeenEpochMs)}`}
          </p>
        </div>
      </div>

      {/* Headline metrics with server rank */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
        {HIGHLIGHT_METRICS.map((m) => {
          const Icon = m.icon;
          const rank = rankFor(allPlayers, m.value, player.uuid);
          return (
            <div key={m.key} className="rounded-box border border-base-300 bg-base-100 p-4">
              <div className="flex items-center gap-2 text-base-content/50">
                <Icon size={16} aria-hidden />
                <span className="text-xs uppercase tracking-wide">{m.label}</span>
              </div>
              <div className="mt-1 text-xl font-semibold tabular-nums">{m.format(m.value(player))}</div>
              <div className="mt-1">
                <RankBadge rank={rank.rank} count={rank.count} percentile={rank.percentile} />
              </div>
            </div>
          );
        })}
      </div>

      {/* Profile radar + combat */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <div className="rounded-box border border-base-300 bg-base-100 p-4">
          <h3 className="mb-2 font-medium">Profile</h3>
          <Radar axes={RADAR_METRICS.map((r) => ({ label: r.label, value: h[r.key] / radarMax[r.key] }))} />
          <p className="mt-1 text-center text-xs text-base-content/40">Relative to the server's top player in each area</p>
        </div>

        <div className="rounded-box border border-base-300 bg-base-100 p-4">
          <h3 className="mb-3 font-medium">Combat</h3>
          <div className="grid grid-cols-3 gap-2 text-center">
            <Mini label="Mob kills" value={formatNumber(h.mobKills)} />
            <Mini label="Player kills" value={formatNumber(h.playerKills)} />
            <Mini label="Deaths" value={formatNumber(h.deaths)} />
            <Mini label="Player K/D" value={kd.toFixed(2)} />
            <Mini label="Dealt" value={`${formatNumber(Math.round(h.damageDealt / 10))} ♥`} />
            <Mini label="Taken" value={`${formatNumber(Math.round(h.damageTaken / 10))} ♥`} />
          </div>
          {topKilled.length > 0 && (
            <div className="mt-4">
              <p className="mb-2 text-xs uppercase tracking-wide text-base-content/40">Most killed</p>
              <BarList
                showRank={false}
                items={topKilled.map((t) => ({ id: t.key, label: humanizeKey(t.key), value: t.value }))}
                format={formatNumber}
              />
            </div>
          )}
        </div>
      </div>

      {/* Distance + mining */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {distance.length > 0 && (
          <div className="rounded-box border border-base-300 bg-base-100 p-4">
            <h3 className="mb-3 font-medium">Distance traveled</h3>
            <Donut
              segments={distance.slice(0, 7).map((d) => ({ label: d.label, value: d.value }))}
              centerValue={formatDistanceCm(distance.reduce((s, d) => s + d.value, 0)).split(' ')[0]}
              centerLabel={formatDistanceCm(distance.reduce((s, d) => s + d.value, 0)).split(' ')[1]}
            />
          </div>
        )}

        {topMined.length > 0 && (
          <div className="rounded-box border border-base-300 bg-base-100 p-4">
            <h3 className="mb-3 font-medium">Top blocks mined</h3>
            <BarList
              showRank={false}
              items={topMined.map((t) => ({ id: t.key, label: humanizeKey(t.key), value: t.value }))}
              format={formatNumber}
            />
          </div>
        )}
      </div>

      {player.scores.length > 0 && (
        <section>
          <SectionTitle>Scoreboard</SectionTitle>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4">
            {player.scores.map((s) => (
              <div key={s.objective} className="rounded-box border border-base-300 bg-base-100 p-3">
                <div className="truncate text-xs text-base-content/50">{humanizeKey(s.objective)}</div>
                <div className="text-lg font-semibold tabular-nums">{formatNumber(s.value)}</div>
              </div>
            ))}
          </div>
        </section>
      )}

      <AllStats player={player} typeOrder={snapshot?.statTypes.map((t) => t.id) ?? []} />
    </div>
  );
}

function Mini({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-box bg-base-200 p-2">
      <div className="text-lg font-semibold tabular-nums">{value}</div>
      <div className="text-xs text-base-content/50">{label}</div>
    </div>
  );
}

/** The full, filterable stat browser, grouped by stat type. */
function AllStats({ player, typeOrder }: { player: PlayerStats; typeOrder: string[] }) {
  const [filter, setFilter] = useState('');
  const groups = Object.entries(player.stats).sort((a, b) => {
    const ai = typeOrder.indexOf(a[0]);
    const bi = typeOrder.indexOf(b[0]);
    return (ai === -1 ? 999 : ai) - (bi === -1 ? 999 : bi);
  });
  const q = filter.trim().toLowerCase();

  if (groups.length === 0) return null;

  return (
    <section>
      <SectionTitle
        action={
          <input
            type="search"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            placeholder="Filter stats"
            className="input input-bordered input-sm w-40 sm:w-56"
          />
        }
      >
        All statistics
      </SectionTitle>
      <div className="space-y-4">
        {groups.map(([typeId, entries]) => {
          const rows = Object.entries(entries).filter(
            ([key]) => !q || humanizeKey(key).toLowerCase().includes(q) || key.includes(q),
          );
          if (rows.length === 0) return null;
          const isCustom = typeId === 'minecraft:custom';
          return (
            <div key={typeId} className="rounded-box border border-base-300 bg-base-100">
              <div className="border-b border-base-300 px-4 py-2 text-sm font-medium">
                {statTypeLabel(typeId)}
                <span className="ml-2 text-xs text-base-content/40">{rows.length}</span>
              </div>
              <ul className="divide-y divide-base-200">
                {rows
                  .sort((a, b) => b[1] - a[1])
                  .map(([key, value]) => (
                    <li key={key} className="flex items-center justify-between gap-3 px-4 py-1.5 text-sm">
                      <span className="min-w-0 truncate text-base-content/70">{humanizeKey(key)}</span>
                      <span className="tabular-nums font-medium">
                        {isCustom ? formatCustomValue(key, value) : formatNumber(value)}
                      </span>
                    </li>
                  ))}
              </ul>
            </div>
          );
        })}
      </div>
    </section>
  );
}
