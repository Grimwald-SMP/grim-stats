import { Link } from 'react-router-dom';
import type { LeaderboardEntry } from '../api/types';
import { BarList } from './charts';
import { Avatar, EmptyState } from './ui';

/** Classic podium (2nd, 1st, 3rd) for the top three entries of a ranking. */
export function Podium({
  entries,
  format,
  linkTo,
}: {
  entries: LeaderboardEntry[];
  format: (n: number) => string;
  /** Builds a per-entry link target; omit to render non-navigating entries (e.g. archived seasons). */
  linkTo?: (e: LeaderboardEntry) => string;
}) {
  if (entries.length === 0) return null;
  // Order as 2nd, 1st, 3rd for the classic podium shape.
  const order = [entries[1], entries[0], entries[2]].filter(Boolean) as LeaderboardEntry[];
  const heights: Record<number, string> = { 1: 'h-20', 2: 'h-14', 3: 'h-10' };
  const medal: Record<number, string> = { 1: 'bg-warning', 2: 'bg-base-content/30', 3: 'bg-secondary' };
  return (
    <div className="flex items-end justify-center gap-3 sm:gap-6">
      {order.map((e) => {
        const body = (
          <>
            <Avatar uuid={e.uuid} name={e.name} size={e.rank === 1 ? 52 : 40} />
            <span className="max-w-full truncate text-sm font-medium">{e.name}</span>
            <span className="text-xs tabular-nums text-base-content/60">{format(e.value)}</span>
            <div className={`flex w-full items-start justify-center rounded-t-box ${heights[e.rank]} ${medal[e.rank]}`}>
              <span className="mt-1 text-xs font-semibold text-base-100">#{e.rank}</span>
            </div>
          </>
        );
        const cls = 'flex w-24 flex-col items-center gap-2';
        return linkTo ? (
          <Link key={e.uuid} to={linkTo(e)} className={cls}>
            {body}
          </Link>
        ) : (
          <div key={e.uuid} className={cls}>
            {body}
          </div>
        );
      })}
    </div>
  );
}

/** A full ranking display: header with optional total, podium, then the ranked bar list. */
export function Board({
  title,
  subtitle,
  entries,
  format,
  total,
  onBack,
  linkTo,
}: {
  title: string;
  subtitle?: string;
  entries: LeaderboardEntry[];
  format: (n: number) => string;
  total?: number;
  onBack?: () => void;
  linkTo?: (e: LeaderboardEntry) => string;
}) {
  if (entries.length === 0) {
    return <EmptyState title="No data yet" hint="No players have recorded this stat." />;
  }
  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between gap-2">
        <div>
          <h3 className="text-lg font-semibold">{title}</h3>
          {subtitle && <p className="text-xs text-base-content/50">{subtitle}</p>}
        </div>
        <div className="flex items-center gap-3">
          {total != null && (
            <span className="text-sm text-base-content/50">
              total <span className="font-medium tabular-nums text-base-content/80">{format(total)}</span>
            </span>
          )}
          {onBack && (
            <button className="btn btn-ghost btn-sm" onClick={onBack}>
              Change
            </button>
          )}
        </div>
      </div>

      <Podium entries={entries.slice(0, 3)} format={format} linkTo={linkTo} />

      <div className="rounded-box border border-base-300 bg-base-100 p-4">
        <BarList
          items={entries.map((e) => ({
            id: e.uuid,
            label: linkTo ? <Link to={linkTo(e)}>{e.name}</Link> : e.name,
            value: e.value,
          }))}
          format={format}
        />
      </div>
    </div>
  );
}
