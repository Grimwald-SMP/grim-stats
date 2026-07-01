import { useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import type { Leaderboard, LeaderboardEntry, StatTypeInfo } from '../api/types';
import { useDashboard } from '../components/Layout';
import { Board } from '../components/board';
import { Loading, SectionTitle } from '../components/ui';
import { formatCustomValue, formatNumber, humanizeKey, statTypeLabel } from '../lib/format';
import { LEADERBOARD_METRICS } from '../lib/stats';

const playerLink = (e: LeaderboardEntry) => `/players/${e.uuid}`;

type Selection =
  | { mode: 'highlight'; key: string }
  | { mode: 'custom'; statType: string; statKey: string };

export function Leaderboards() {
  const { snapshot } = useDashboard();
  const [selection, setSelection] = useState<Selection>({ mode: 'highlight', key: 'playTime' });

  if (!snapshot) return <Loading />;

  return (
    <div className="space-y-5">
      <SectionTitle>Leaderboards</SectionTitle>

      <div className="flex flex-wrap gap-2">
        {LEADERBOARD_METRICS.map((m) => {
          const active = selection.mode === 'highlight' && selection.key === m.key;
          const Icon = m.icon;
          return (
            <button
              key={m.key}
              className={`btn btn-sm gap-2 ${active ? 'btn-primary' : 'btn-ghost border border-base-300'}`}
              onClick={() => setSelection({ mode: 'highlight', key: m.key })}
            >
              <Icon size={15} aria-hidden />
              {m.label}
            </button>
          );
        })}
        <button
          className={`btn btn-sm ${selection.mode === 'custom' ? 'btn-primary' : 'btn-ghost border border-base-300'}`}
          onClick={() =>
            setSelection({ mode: 'custom', statType: 'minecraft:custom', statKey: '' })
          }
        >
          View Other Stat
        </button>
      </div>

      {selection.mode === 'highlight' ? (
        <HighlightBoard metricKey={selection.key} />
      ) : (
        <CustomBoard
          statTypes={snapshot.statTypes}
          statType={selection.statType}
          statKey={selection.statKey}
          onPick={(statType, statKey) => setSelection({ mode: 'custom', statType, statKey })}
        />
      )}
    </div>
  );
}

function HighlightBoard({ metricKey }: { metricKey: string }) {
  const { snapshot } = useDashboard();
  const metric = LEADERBOARD_METRICS.find((m) => m.key === metricKey)!;

  const entries: LeaderboardEntry[] = useMemo(() => {
    const players = snapshot?.players ?? [];
    return players
      .map((p) => ({ uuid: p.uuid, name: p.name, value: metric.value(p) }))
      .filter((e) => e.value > 0)
      .sort((a, b) => b.value - a.value)
      .map((e, i) => ({ rank: i + 1, ...e }));
  }, [snapshot, metric.key]);

  return <Board title={metric.label} entries={entries} format={metric.format} linkTo={playerLink} />;
}

function CustomBoard({
  statTypes,
  statType,
  statKey,
  onPick,
}: {
  statTypes: StatTypeInfo[];
  statType: string;
  statKey: string;
  onPick: (statType: string, statKey: string) => void;
}) {
  const [board, setBoard] = useState<Leaderboard | null>(null);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState('');

  const activeType = statTypes.find((t) => t.id === statType) ?? statTypes[0];
  const keys = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (activeType?.keys ?? [])
      .filter((k) => !q || humanizeKey(k).toLowerCase().includes(q) || k.includes(q))
      .slice(0, 60);
  }, [activeType, query]);

  useEffect(() => {
    if (!statKey) {
      setBoard(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    api
      .leaderboard(statType, statKey, 50)
      .then((b) => !cancelled && setBoard(b))
      .catch(() => !cancelled && setBoard(null))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [statType, statKey]);

  const format = (n: number) => (statType === 'minecraft:custom' ? formatCustomValue(statKey, n) : formatNumber(n));

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-2 rounded-box border border-base-300 bg-base-100 p-3 sm:flex-row">
        <select
          className="select select-bordered select-sm w-full sm:w-56"
          value={activeType?.id ?? ''}
          onChange={(e) => onPick(e.target.value, '')}
        >
          {statTypes.map((t) => (
            <option key={t.id} value={t.id}>
              {statTypeLabel(t.id)}
              {t.modded ? ' (mod)' : ''}
            </option>
          ))}
        </select>
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search a stat to rank"
          className="input input-bordered input-sm w-full sm:flex-1"
        />
      </div>

      {!statKey ? (
        <div className="rounded-box border border-base-300 bg-base-100">
          <ul className="max-h-72 divide-y divide-base-200 overflow-y-auto">
            {keys.length === 0 ? (
              <li className="py-6 text-center text-sm text-base-content/50">No matching stats.</li>
            ) : (
              keys.map((k) => (
                <li key={k}>
                  <button
                    className="flex w-full items-center justify-between px-4 py-2 text-left text-sm hover:bg-base-200"
                    onClick={() => onPick(statType, k)}
                  >
                    <span className="truncate">{humanizeKey(k)}</span>
                    <span className="text-xs text-base-content/40">Rank →</span>
                  </button>
                </li>
              ))
            )}
          </ul>
        </div>
      ) : loading ? (
        <Loading label="Ranking players…" />
      ) : (
        <Board
          title={humanizeKey(statKey)}
          subtitle={statTypeLabel(statType)}
          entries={board?.entries ?? []}
          format={format}
          total={board?.total}
          onBack={() => onPick(statType, '')}
          linkTo={playerLink}
        />
      )}
    </div>
  );
}
