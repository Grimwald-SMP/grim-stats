import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Archive, Moon, Sun, Sunrise, Sunset, type LucideIcon } from 'lucide-react';
import { api } from '../api/client';
import { useDashboard } from '../components/Layout';
import { BarList } from '../components/charts';
import { EmptyState, Loading, StatCard, SectionTitle, Avatar } from '../components/ui';
import {
  formatCustomValue,
  formatDistanceCm,
  formatNumber,
  formatTicks,
  humanizeKey,
  relativeTime,
  statTypeLabel,
} from '../lib/format';
import type { PinnedStat, SeasonInfo } from '../api/types';

export function Overview() {
  const { snapshot } = useDashboard();
  const [pinFilter, setPinFilter] = useState('');
  const [seasons, setSeasons] = useState<SeasonInfo[]>([]);

  useEffect(() => {
    // Best-effort teaser; the Overview must render fine without it.
    api.seasons().then(setSeasons).catch(() => {});
  }, []);

  const pinned = snapshot?.pinnedStats ?? [];
  const filteredPins = useMemo(() => {
    const q = pinFilter.trim().toLowerCase();
    if (!q) return pinned;
    return pinned.filter(
      (p) => humanizeKey(p.statKey).toLowerCase().includes(q) || p.statKey.includes(q),
    );
  }, [pinned, pinFilter]);

  const totals = useMemo(() => {
    const ps = snapshot?.players ?? [];
    const sum = (f: (p: (typeof ps)[number]) => number) => ps.reduce((s, p) => s + f(p), 0);
    return {
      playTime: sum((p) => p.highlights.playTime),
      distanceCm: sum((p) => p.highlights.distanceCm),
      blocksMined: sum((p) => p.highlights.blocksMined),
      deaths: sum((p) => p.highlights.deaths),
      mobKills: sum((p) => p.highlights.mobKills),
      itemsCrafted: sum((p) => p.highlights.itemsCrafted),
    };
  }, [snapshot]);

  const topPlaytime = useMemo(() => {
    return [...(snapshot?.players ?? [])]
      .filter((p) => p.highlights.playTime > 0)
      .sort((a, b) => b.highlights.playTime - a.highlights.playTime)
      .slice(0, 5);
  }, [snapshot]);

  if (!snapshot) return <Loading label="Connecting to server…" />;

  const { world, players, objectives } = snapshot;
  const online = players.filter((p) => p.online);
  const minecraftDay = Math.floor(world.gameTime / 24000);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">{world.serverName || 'Minecraft Server'}</h1>
        {world.motd && <p className="text-sm text-base-content/60">{stripFormatting(world.motd)}</p>}
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <StatCard label="Online" value={`${world.onlinePlayers}/${world.maxPlayers}`} />
        <StatCard label="Known players" value={formatNumber(players.length)} />
        <StatCard label="Day" value={formatNumber(minecraftDay)} sub={<TimeOfDay dayTime={world.dayTime} />} />
        <StatCard
          label="Version"
          value={world.minecraftVersion || '-'}
          sub={[capitalize(world.difficulty), world.hardcore ? 'Hardcore' : null].filter(Boolean).join(' · ')}
        />
      </div>

      {players.length > 0 && (
        <section>
          <SectionTitle>Server totals</SectionTitle>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
            <StatCard label="Play time" value={formatTicks(totals.playTime)} />
            <StatCard label="Distance" value={formatDistanceCm(totals.distanceCm)} />
            <StatCard label="Blocks mined" value={formatNumber(totals.blocksMined)} />
            <StatCard label="Items crafted" value={formatNumber(totals.itemsCrafted)} />
            <StatCard label="Mob kills" value={formatNumber(totals.mobKills)} />
            <StatCard label="Deaths" value={formatNumber(totals.deaths)} />
          </div>
        </section>
      )}

      {topPlaytime.length > 0 && (
        <section>
          <SectionTitle action={<Link to="/leaderboards" className="link link-hover text-sm">All leaderboards</Link>}>
            Most active players
          </SectionTitle>
          <div className="rounded-box border border-base-300 bg-base-100 p-4">
            <BarList
              items={topPlaytime.map((p) => ({
                id: p.uuid,
                label: <Link to={`/players/${p.uuid}`}>{p.name}</Link>,
                value: p.highlights.playTime,
              }))}
              format={formatTicks}
            />
          </div>
        </section>
      )}

      {pinned.length > 0 && (
        <section>
          <SectionTitle
            action={
              pinned.length > 3 ? (
                <input
                  type="search"
                  value={pinFilter}
                  onChange={(e) => setPinFilter(e.target.value)}
                  placeholder="Filter pinned"
                  className="input input-bordered input-sm w-36 sm:w-48"
                />
              ) : undefined
            }
          >
            Pinned stats
          </SectionTitle>
          {filteredPins.length === 0 ? (
            <EmptyState title="No pinned stats match" hint="Clear the filter to see all pinned stats." />
          ) : (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {filteredPins.map((p) => (
                <PinnedCard key={`${p.statType}/${p.statKey}`} pin={p} />
              ))}
            </div>
          )}
        </section>
      )}

      <section>
        <SectionTitle action={<Link to="/players" className="link link-hover text-sm">All players</Link>}>
          Online now
        </SectionTitle>
        {online.length === 0 ? (
          <EmptyState title="No players online" hint="Players appear here as soon as they join." />
        ) : (
          <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {online.slice(0, 12).map((p) => (
              <li key={p.uuid}>
                <Link
                  to={`/players/${p.uuid}`}
                  className="flex items-center gap-3 rounded-box border border-base-300 bg-base-100 p-3"
                >
                  <Avatar uuid={p.uuid} name={p.name} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-medium">{p.name}</span>
                    <span className="block text-xs text-success">online</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      {seasons.length > 0 && (
        <section>
          <SectionTitle action={<Link to="/seasons" className="link link-hover text-sm">All seasons</Link>}>
            Past seasons
          </SectionTitle>
          <div className="flex flex-wrap gap-2">
            {seasons.slice(0, 6).map((s) => (
              <Link
                key={s.id}
                to={`/seasons/${s.id}`}
                className="inline-flex items-center gap-2 rounded-box border border-base-300 bg-base-100 px-3 py-2 text-sm"
              >
                <Archive size={14} className="text-base-content/40" aria-hidden />
                <span className="font-medium">{s.name}</span>
                <span className="text-xs text-base-content/50">
                  {formatNumber(s.playerCount)} {s.playerCount === 1 ? 'player' : 'players'} · {formatTicks(s.totalPlayTimeTicks)}
                </span>
              </Link>
            ))}
          </div>
        </section>
      )}

      {objectives.length > 0 && (
        <section>
          <SectionTitle action={<Link to="/scoreboards" className="link link-hover text-sm">All scoreboards</Link>}>
            Scoreboards
          </SectionTitle>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {objectives.slice(0, 4).map((o) => (
              <div key={o.name} className="rounded-box border border-base-300 bg-base-100 p-4">
                <div className="mb-2 flex items-baseline justify-between gap-2">
                  <span className="font-medium">{stripFormatting(o.displayName) || o.name}</span>
                  <span className="text-xs text-base-content/50">{o.criterion}</span>
                </div>
                {o.entries.length === 0 ? (
                  <p className="text-sm text-base-content/50">No scores yet.</p>
                ) : (
                  <ol className="space-y-1 text-sm">
                    {o.entries.slice(0, 5).map((e, i) => (
                      <li key={e.holder} className="flex justify-between gap-2">
                        <span className="truncate text-base-content/70">
                          {i + 1}. {e.holder}
                        </span>
                        <span className="tabular-nums font-medium">{formatNumber(e.value)}</span>
                      </li>
                    ))}
                  </ol>
                )}
              </div>
            ))}
          </div>
        </section>
      )}

      <p className="text-right text-xs text-base-content/40">Updated {relativeTime(snapshot.generatedAtEpochMs)}</p>
    </div>
  );
}

function PinnedCard({ pin }: { pin: PinnedStat }) {
  const format = (value: number) =>
    pin.statType === 'minecraft:custom' ? formatCustomValue(pin.statKey, value) : formatNumber(value);
  return (
    <div className="rounded-box border border-base-300 bg-base-100 p-4">
      <div className="mb-2">
        <div className="font-medium">{humanizeKey(pin.statKey)}</div>
        <div className="text-xs text-base-content/40">{statTypeLabel(pin.statType)}</div>
      </div>
      {pin.entries.length === 0 ? (
        <p className="text-sm text-base-content/50">No data yet.</p>
      ) : (
        <ol className="space-y-1">
          {pin.entries.slice(0, 5).map((e, i) => (
            <li key={e.uuid} className="flex items-center gap-2 text-sm">
              <span className="w-4 text-right text-xs tabular-nums text-base-content/40">{i + 1}</span>
              <Link to={`/players/${e.uuid}`} className="min-w-0 flex-1 truncate text-base-content/80 hover:underline">
                {e.name}
              </Link>
              <span className="tabular-nums font-medium">{format(e.value)}</span>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

/** Current in-game time of day with a matching icon (dayTime ticks: 0 is sunrise, 24000 a full day). */
function TimeOfDay({ dayTime }: { dayTime: number }) {
  const t = ((dayTime % 24000) + 24000) % 24000;
  let label: string;
  let Icon: LucideIcon;
  if (t >= 23000 || t < 1000) {
    label = 'Sunrise';
    Icon = Sunrise;
  } else if (t < 12000) {
    label = 'Daytime';
    Icon = Sun;
  } else if (t < 13000) {
    label = 'Sunset';
    Icon = Sunset;
  } else {
    label = 'Night';
    Icon = Moon;
  }
  return (
    <span className="inline-flex items-center gap-1">
      <Icon size={12} aria-hidden />
      {label}
    </span>
  );
}

function capitalize(s: string): string {
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : s;
}

/** Removes Minecraft section-sign formatting codes from display strings. */
function stripFormatting(s: string): string {
  return s.replace(/§./g, '');
}
