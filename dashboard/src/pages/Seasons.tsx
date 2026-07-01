import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Archive, Calendar, Download, Trash2, Upload, Users } from 'lucide-react';
import { api } from '../api/client';
import type { LeaderboardEntry, Season, SeasonInfo } from '../api/types';
import { Board } from '../components/board';
import { EmptyState, Loading, SectionTitle, StatCard } from '../components/ui';
import { formatDistanceCm, formatNumber, formatTicks } from '../lib/format';

function playersLabel(count: number): string {
  return `${formatNumber(count)} ${count === 1 ? 'player' : 'players'}`;
}
import { LEADERBOARD_METRICS } from '../lib/stats';
import { useAuth } from '../state/AuthContext';

/** List of archived seasons, with admin export/import controls. */
export function Seasons() {
  const { isAdmin } = useAuth();
  const [seasons, setSeasons] = useState<SeasonInfo[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(() => {
    api
      .seasons()
      .then(setSeasons)
      .catch((e) => setError(String(e)));
  }, []);

  useEffect(reload, [reload]);

  if (error) return <EmptyState title="Could not load seasons" hint={error} />;
  if (!seasons) return <Loading label="Loading seasons…" />;

  return (
    <div className="space-y-5">
      <SectionTitle>Seasons</SectionTitle>
      <p className="text-sm text-base-content/60">
        Frozen snapshots of past worlds. Export the current world before a reset, or import a season
        file from another server, and its stats stay browsable here forever.
      </p>

      {isAdmin && <AdminSeasonTools onChanged={reload} />}

      {seasons.length === 0 ? (
        <EmptyState
          title="No seasons archived yet"
          hint={isAdmin ? 'Archive the current world above to start the timeline.' : 'Ask an admin to archive a season.'}
        />
      ) : (
        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {seasons.map((s) => (
            <SeasonCard key={s.id} season={s} isAdmin={isAdmin} onChanged={reload} />
          ))}
        </ul>
      )}
    </div>
  );
}

function SeasonCard({
  season,
  isAdmin,
  onChanged,
}: {
  season: SeasonInfo;
  isAdmin: boolean;
  onChanged: () => void;
}) {
  const [confirming, setConfirming] = useState(false);
  const date = new Date(season.createdAtEpochMs).toLocaleDateString();
  const days = Math.floor(season.gameTime / 24000);

  return (
    <li className="rounded-box border border-base-300 bg-base-100 p-4">
      <div className="flex items-start justify-between gap-2">
        <Link to={`/seasons/${season.id}`} className="min-w-0">
          <h3 className="truncate text-lg font-semibold">{season.name}</h3>
          <p className="text-xs text-base-content/50">
            {season.serverName || 'unknown server'}
            {season.minecraftVersion ? ` · ${season.minecraftVersion}` : ''}
          </p>
        </Link>
        <Archive size={18} className="shrink-0 text-base-content/30" aria-hidden />
      </div>

      <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-sm text-base-content/70">
        <span className="inline-flex items-center gap-1.5">
          <Calendar size={14} aria-hidden />
          {date}
        </span>
        <span className="inline-flex items-center gap-1.5">
          <Users size={14} aria-hidden />
          {playersLabel(season.playerCount)}
        </span>
        <span className="tabular-nums">{formatTicks(season.totalPlayTimeTicks)} played</span>
        {days > 0 && <span className="tabular-nums">day {formatNumber(days)}</span>}
      </div>

      <div className="mt-3 flex items-center gap-2">
        <Link to={`/seasons/${season.id}`} className="btn btn-primary btn-sm">
          View stats
        </Link>
        <a href={api.seasonDownloadUrl(season.id)} className="btn btn-ghost btn-sm gap-1.5" download>
          <Download size={14} aria-hidden />
          JSON
        </a>
        {isAdmin &&
          (confirming ? (
            <span className="ml-auto flex items-center gap-1">
              <button
                className="btn btn-error btn-sm"
                onClick={() => {
                  void api.deleteSeason(season.id).then(onChanged);
                }}
              >
                Delete
              </button>
              <button className="btn btn-ghost btn-sm" onClick={() => setConfirming(false)}>
                Keep
              </button>
            </span>
          ) : (
            <button
              className="btn btn-ghost btn-sm btn-square ml-auto text-base-content/40"
              aria-label={`Delete ${season.name}`}
              onClick={() => setConfirming(true)}
            >
              <Trash2 size={15} aria-hidden />
            </button>
          ))}
      </div>
    </li>
  );
}

function AdminSeasonTools({ onChanged }: { onChanged: () => void }) {
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const doExport = () => {
    setBusy(true);
    setMessage(null);
    api
      .exportSeason(name.trim())
      .then((info) => {
        setName('');
        setMessage(`Archived "${info.name}" with ${info.playerCount} players.`);
        onChanged();
      })
      .catch((e) => setMessage(String(e)))
      .finally(() => setBusy(false));
  };

  const doImport = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setBusy(true);
    setMessage(null);
    file
      .text()
      .then((json) => api.importSeason(json))
      .then((info) => {
        setMessage(`Imported "${info.name}" with ${info.playerCount} players.`);
        onChanged();
      })
      .catch((err) => setMessage(String(err)))
      .finally(() => {
        setBusy(false);
        if (fileRef.current) fileRef.current.value = '';
      });
  };

  return (
    <div className="rounded-box border border-base-300 bg-base-100 p-4">
      <h3 className="mb-3 text-sm font-medium uppercase tracking-wide text-base-content/50">Admin</h3>
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Season name (e.g. Season 3)"
          className="input input-bordered input-sm w-full sm:max-w-xs"
        />
        <button className="btn btn-primary btn-sm gap-1.5" disabled={busy} onClick={doExport}>
          <Archive size={14} aria-hidden />
          Archive current world
        </button>
        <span className="hidden text-base-content/30 sm:inline">or</span>
        <label className={`btn btn-ghost btn-sm gap-1.5 border border-base-300 ${busy ? 'btn-disabled' : ''}`}>
          <Upload size={14} aria-hidden />
          Import season file
          <input ref={fileRef} type="file" accept=".json,application/json" className="hidden" onChange={doImport} />
        </label>
      </div>
      {message && <p className="mt-2 text-sm text-base-content/60">{message}</p>}
    </div>
  );
}

/** Full archived-season view: totals plus rankable leaderboards over the frozen player data. */
export function SeasonDetail() {
  const { id = '' } = useParams();
  const [season, setSeason] = useState<Season | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [metricKey, setMetricKey] = useState('playTime');

  useEffect(() => {
    let cancelled = false;
    api
      .season(id)
      .then((s) => !cancelled && setSeason(s))
      .catch((e) => !cancelled && setError(String(e)));
    return () => {
      cancelled = true;
    };
  }, [id]);

  const metric = LEADERBOARD_METRICS.find((m) => m.key === metricKey)!;
  const entries: LeaderboardEntry[] = useMemo(() => {
    return (season?.players ?? [])
      .map((p) => ({ uuid: p.uuid, name: p.name, value: metric.value(p) }))
      .filter((e) => e.value > 0)
      .sort((a, b) => b.value - a.value)
      .map((e, i) => ({ rank: i + 1, ...e }));
  }, [season, metric]);

  const totals = useMemo(() => {
    const ps = season?.players ?? [];
    const sum = (f: (p: (typeof ps)[number]) => number) => ps.reduce((s, p) => s + f(p), 0);
    return {
      playTime: sum((p) => p.highlights.playTime),
      distanceCm: sum((p) => p.highlights.distanceCm),
      blocksMined: sum((p) => p.highlights.blocksMined),
      itemsCrafted: sum((p) => p.highlights.itemsCrafted),
      mobKills: sum((p) => p.highlights.mobKills),
      deaths: sum((p) => p.highlights.deaths),
    };
  }, [season]);

  if (error) return <EmptyState title="Could not load season" hint={error} />;
  if (!season) return <Loading label="Loading season…" />;

  const date = new Date(season.createdAtEpochMs).toLocaleDateString();
  const days = Math.floor(season.gameTime / 24000);

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Link to="/seasons" className="btn btn-ghost btn-sm btn-square" aria-label="Back to seasons">
          ‹
        </Link>
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-2xl font-semibold">{season.name}</h1>
          <p className="text-xs text-base-content/50">
            Archived {date}
            {season.serverName ? ` from ${season.serverName}` : ''}
            {season.minecraftVersion ? ` · ${season.minecraftVersion}` : ''}
            {days > 0 ? ` · day ${formatNumber(days)}` : ''}
          </p>
        </div>
        <a href={api.seasonDownloadUrl(season.id)} className="btn btn-ghost btn-sm gap-1.5" download>
          <Download size={14} aria-hidden />
          JSON
        </a>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <StatCard label="Play time" value={formatTicks(totals.playTime)} />
        <StatCard label="Distance" value={formatDistanceCm(totals.distanceCm)} />
        <StatCard label="Blocks mined" value={formatNumber(totals.blocksMined)} />
        <StatCard label="Items crafted" value={formatNumber(totals.itemsCrafted)} />
        <StatCard label="Mob kills" value={formatNumber(totals.mobKills)} />
        <StatCard label="Deaths" value={formatNumber(totals.deaths)} />
      </div>

      <div className="flex flex-wrap gap-2">
        {LEADERBOARD_METRICS.map((m) => {
          const Icon = m.icon;
          return (
            <button
              key={m.key}
              className={`btn btn-sm gap-2 ${m.key === metricKey ? 'btn-primary' : 'btn-ghost border border-base-300'}`}
              onClick={() => setMetricKey(m.key)}
            >
              <Icon size={15} aria-hidden />
              {m.label}
            </button>
          );
        })}
      </div>

      {/* Archived players have no live profile page, so entries do not link anywhere. */}
      <Board title={metric.label} subtitle={`${playersLabel(season.players.length)} archived`} entries={entries} format={metric.format} />
    </div>
  );
}
