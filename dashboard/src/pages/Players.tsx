import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import type { PlayerStats } from '../api/types';
import { useDashboard } from '../components/Layout';
import { Avatar, EmptyState, Loading, SectionTitle } from '../components/ui';
import { formatNumber, formatTicks, relativeTime } from '../lib/format';
import { playerKd } from '../lib/stats';

const SORTS = [
  { key: 'active', label: 'Most active', compare: byDesc((p) => p.highlights.playTime) },
  { key: 'recent', label: 'Recently seen', compare: byRecent },
  { key: 'kills', label: 'Mob kills', compare: byDesc((p) => p.highlights.mobKills) },
  { key: 'kd', label: 'K/D', compare: byDesc((p) => playerKd(p.highlights)) },
  { key: 'name', label: 'Name', compare: (a: PlayerStats, b: PlayerStats) => a.name.localeCompare(b.name) },
] as const;

type SortKey = (typeof SORTS)[number]['key'];

function byDesc(value: (p: PlayerStats) => number) {
  return (a: PlayerStats, b: PlayerStats) => value(b) - value(a) || a.name.localeCompare(b.name);
}

function byRecent(a: PlayerStats, b: PlayerStats) {
  if (a.online !== b.online) return a.online ? -1 : 1;
  return (b.lastSeenEpochMs ?? 0) - (a.lastSeenEpochMs ?? 0) || a.name.localeCompare(b.name);
}

export function Players() {
  const { snapshot } = useDashboard();
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState<SortKey>('active');
  const [onlineOnly, setOnlineOnly] = useState(false);

  const players = useMemo(() => {
    if (!snapshot) return [];
    const q = query.trim().toLowerCase();
    const compare = SORTS.find((s) => s.key === sort)!.compare;
    return snapshot.players
      .filter((p) => (!onlineOnly || p.online) && (p.name.toLowerCase().includes(q) || p.uuid.includes(q)))
      .sort(compare);
  }, [snapshot, query, sort, onlineOnly]);

  if (!snapshot) return <Loading />;
  const onlineCount = snapshot.players.filter((p) => p.online).length;

  return (
    <div className="space-y-4">
      <SectionTitle>Players</SectionTitle>

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by name or UUID"
          className="input input-bordered input-sm w-full sm:max-w-xs"
        />
        <select
          className="select select-bordered select-sm w-full sm:w-44"
          value={sort}
          onChange={(e) => setSort(e.target.value as SortKey)}
          aria-label="Sort players"
        >
          {SORTS.map((s) => (
            <option key={s.key} value={s.key}>
              {s.label}
            </option>
          ))}
        </select>
        <label className="label cursor-pointer justify-start gap-2 py-0">
          <input
            type="checkbox"
            className="toggle toggle-sm"
            checked={onlineOnly}
            onChange={(e) => setOnlineOnly(e.target.checked)}
          />
          <span className="label-text text-sm">Online only ({onlineCount})</span>
        </label>
        <span className="text-sm text-base-content/50 sm:ml-auto">{players.length} shown</span>
      </div>

      {players.length === 0 ? (
        <EmptyState title="No players found" hint="Try a different search or filter." />
      ) : (
        <>
          {/* Mobile: stat-rich cards */}
          <ul className="grid grid-cols-1 gap-2 sm:hidden">
            {players.map((p) => (
              <li key={p.uuid}>
                <Link
                  to={`/players/${p.uuid}`}
                  className="block rounded-box border border-base-300 bg-base-100 p-3"
                >
                  <span className="flex items-center gap-3">
                    <Avatar uuid={p.uuid} name={p.name} />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-medium">{p.name}</span>
                      <span className={`block text-xs ${p.online ? 'text-success' : 'text-base-content/50'}`}>
                        {p.online ? 'online now' : `seen ${relativeTime(p.lastSeenEpochMs)}`}
                      </span>
                    </span>
                  </span>
                  <span className="mt-2 grid grid-cols-3 gap-2 text-center text-xs">
                    <MiniStat label="played" value={formatTicks(p.highlights.playTime)} />
                    <MiniStat label="mob kills" value={formatNumber(p.highlights.mobKills)} />
                    <MiniStat label="deaths" value={formatNumber(p.highlights.deaths)} />
                  </span>
                </Link>
              </li>
            ))}
          </ul>

          {/* Desktop: stat table */}
          <div className="hidden overflow-x-auto rounded-box border border-base-300 sm:block">
            <table className="table">
              <thead>
                <tr>
                  <th>Player</th>
                  <th>Status</th>
                  <th className="text-right">Play time</th>
                  <th className="text-right">Mob kills</th>
                  <th className="text-right">Deaths</th>
                  <th className="text-right">K/D</th>
                  <th>Last seen</th>
                </tr>
              </thead>
              <tbody>
                {players.map((p) => (
                  <tr key={p.uuid}>
                    <td>
                      <Link to={`/players/${p.uuid}`} className="flex items-center gap-3">
                        <Avatar uuid={p.uuid} name={p.name} size={28} />
                        <span className="font-medium">{p.name}</span>
                      </Link>
                    </td>
                    <td>
                      {p.online ? (
                        <span className="badge badge-success badge-sm">online</span>
                      ) : (
                        <span className="badge badge-ghost badge-sm">offline</span>
                      )}
                    </td>
                    <td className="text-right tabular-nums">{formatTicks(p.highlights.playTime)}</td>
                    <td className="text-right tabular-nums">{formatNumber(p.highlights.mobKills)}</td>
                    <td className="text-right tabular-nums">{formatNumber(p.highlights.deaths)}</td>
                    <td className="text-right tabular-nums">
                      {p.highlights.playerKills > 0 || p.highlights.deathsToPlayers > 0
                        ? playerKd(p.highlights).toFixed(2)
                        : '-'}
                    </td>
                    <td className="text-base-content/60">{p.online ? 'now' : relativeTime(p.lastSeenEpochMs)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}

function MiniStat({ label, value }: { label: string; value: string }) {
  return (
    <span className="rounded bg-base-200 px-1 py-1.5">
      <span className="block font-medium tabular-nums">{value}</span>
      <span className="block text-base-content/50">{label}</span>
    </span>
  );
}
