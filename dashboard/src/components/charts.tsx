import { type ReactNode } from 'react';

// Theme-driven colors (daisyUI v5 exposes these CSS variables). Cycled for multi-series charts.
export const CHART_COLORS = [
  'var(--color-primary)',
  'var(--color-secondary)',
  'var(--color-accent)',
  'var(--color-info)',
  'var(--color-success)',
  'var(--color-warning)',
  'var(--color-error)',
];

export interface BarItem {
  id: string;
  label: ReactNode;
  value: number;
  href?: string;
}

/**
 * Ranked horizontal bars (HTML, responsive). Used for leaderboards and top-N lists. A row can be
 * highlighted (e.g. the current player) and the bar width is proportional to the max value.
 */
export function BarList({
  items,
  format = (n) => n.toLocaleString(),
  highlightId,
  showRank = true,
  onSelect,
}: {
  items: BarItem[];
  format?: (n: number) => string;
  highlightId?: string;
  showRank?: boolean;
  onSelect?: (id: string) => void;
}) {
  const max = Math.max(1, ...items.map((i) => i.value));
  return (
    <ol className="space-y-2">
      {items.map((it, i) => {
        const pct = Math.max(2, Math.round((it.value / max) * 100));
        const active = it.id === highlightId;
        const Row = (
          <div className="flex items-center gap-3">
            {showRank && (
              <span
                className={`w-5 shrink-0 text-right text-xs tabular-nums ${
                  i < 3 ? 'font-semibold text-primary' : 'text-base-content/40'
                }`}
              >
                {i + 1}
              </span>
            )}
            <div className="min-w-0 flex-1">
              <div className="mb-1 flex items-baseline justify-between gap-2">
                <span className={`min-w-0 truncate text-sm ${active ? 'font-semibold' : ''}`}>{it.label}</span>
                <span className="shrink-0 text-sm font-medium tabular-nums">{format(it.value)}</span>
              </div>
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-base-300">
                <div
                  className="h-full rounded-full"
                  style={{
                    width: `${pct}%`,
                    background: active ? 'var(--color-accent)' : 'var(--color-primary)',
                    transition: 'width 300ms ease',
                  }}
                />
              </div>
            </div>
          </div>
        );
        return (
          <li key={it.id}>
            {onSelect ? (
              <button className="block w-full text-left" onClick={() => onSelect(it.id)}>
                {Row}
              </button>
            ) : (
              Row
            )}
          </li>
        );
      })}
    </ol>
  );
}

export interface DonutSegment {
  label: string;
  value: number;
  color?: string;
}

/** A donut chart (SVG) with a centered label and a legend. Segments under ~2% are still visible. */
export function Donut({
  segments,
  size = 168,
  thickness = 22,
  centerValue,
  centerLabel,
}: {
  segments: DonutSegment[];
  size?: number;
  thickness?: number;
  centerValue?: ReactNode;
  centerLabel?: ReactNode;
}) {
  const total = segments.reduce((s, x) => s + x.value, 0);
  const r = (size - thickness) / 2;
  const cx = size / 2;
  const circumference = 2 * Math.PI * r;
  let offset = 0;

  return (
    <div className="flex flex-col items-center gap-4 sm:flex-row sm:items-center">
      <svg viewBox={`0 0 ${size} ${size}`} width={size} height={size} role="img" aria-label="Distribution chart">
        <circle cx={cx} cy={cx} r={r} fill="none" stroke="var(--color-base-300)" strokeWidth={thickness} />
        <g transform={`rotate(-90 ${cx} ${cx})`}>
          {total > 0 &&
            segments.map((s, i) => {
              const len = (s.value / total) * circumference;
              const seg = (
                <circle
                  key={s.label}
                  cx={cx}
                  cy={cx}
                  r={r}
                  fill="none"
                  stroke={s.color ?? CHART_COLORS[i % CHART_COLORS.length]}
                  strokeWidth={thickness}
                  strokeDasharray={`${Math.max(len - 1, 0)} ${circumference - Math.max(len - 1, 0)}`}
                  strokeDashoffset={-offset}
                />
              );
              offset += len;
              return seg;
            })}
        </g>
        {(centerValue != null || centerLabel != null) && (
          <text x={cx} y={cx} textAnchor="middle" className="fill-base-content">
            {centerValue != null && (
              <tspan x={cx} dy="-0.1em" style={{ fontSize: 18, fontWeight: 600 }}>
                {centerValue}
              </tspan>
            )}
            {centerLabel != null && (
              <tspan x={cx} dy="1.4em" style={{ fontSize: 11 }} className="fill-base-content/50">
                {centerLabel}
              </tspan>
            )}
          </text>
        )}
      </svg>
      <ul className="grid w-full grid-cols-1 gap-1 text-sm sm:max-w-[14rem]">
        {segments.map((s, i) => (
          <li key={s.label} className="flex items-center gap-2">
            <span
              className="h-2.5 w-2.5 shrink-0 rounded-sm"
              style={{ background: s.color ?? CHART_COLORS[i % CHART_COLORS.length] }}
            />
            <span className="min-w-0 flex-1 truncate text-base-content/70">{s.label}</span>
            <span className="tabular-nums text-base-content/50">
              {total > 0 ? Math.round((s.value / total) * 100) : 0}%
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export interface RadarAxis {
  label: string;
  value: number; // 0..1 normalized
}

/** A radar/spider chart (SVG) for a normalized multi-dimensional profile. */
export function Radar({ axes, size = 240 }: { axes: RadarAxis[]; size?: number }) {
  const cx = size / 2;
  const r = size / 2 - 34;
  const n = axes.length;
  const point = (i: number, radius: number) => {
    const angle = (Math.PI * 2 * i) / n - Math.PI / 2;
    return [cx + radius * Math.cos(angle), cx + radius * Math.sin(angle)];
  };
  const rings = [0.25, 0.5, 0.75, 1];
  const dataPoints = axes.map((a, i) => point(i, r * Math.max(0.02, Math.min(1, a.value))));
  const polygon = dataPoints.map((p) => p.join(',')).join(' ');

  return (
    <svg viewBox={`0 0 ${size} ${size}`} width="100%" height={size} role="img" aria-label="Player profile radar">
      {rings.map((ring) => (
        <polygon
          key={ring}
          points={axes.map((_, i) => point(i, r * ring).join(',')).join(' ')}
          fill="none"
          stroke="var(--color-base-300)"
          strokeWidth={1}
        />
      ))}
      {axes.map((a, i) => {
        const [x, y] = point(i, r);
        const [lx, ly] = point(i, r + 16);
        return (
          <g key={a.label}>
            <line x1={cx} y1={cx} x2={x} y2={y} stroke="var(--color-base-300)" strokeWidth={1} />
            <text
              x={lx}
              y={ly}
              textAnchor={Math.abs(lx - cx) < 4 ? 'middle' : lx > cx ? 'start' : 'end'}
              dominantBaseline="middle"
              className="fill-base-content/60"
              style={{ fontSize: 10 }}
            >
              {a.label}
            </text>
          </g>
        );
      })}
      <polygon
        points={polygon}
        fill="var(--color-primary)"
        fillOpacity={0.25}
        stroke="var(--color-primary)"
        strokeWidth={2}
      />
      {dataPoints.map(([x, y], i) => (
        <circle key={i} cx={x} cy={y} r={3} fill="var(--color-primary)" />
      ))}
    </svg>
  );
}

/** Compact rank + percentile indicator with a thin progress bar. */
export function RankBadge({ rank, count, percentile }: { rank: number; count: number; percentile: number }) {
  if (rank <= 0) {
    return <span className="text-xs text-base-content/40">unranked</span>;
  }
  const top = 100 - percentile;
  return (
    <div className="flex items-center gap-2">
      <span className="badge badge-sm" style={{ background: 'var(--color-primary)', color: 'var(--color-primary-content)', border: 'none' }}>
        #{rank}
      </span>
      <span className="text-xs text-base-content/50">
        of {count} · top {Math.max(1, top)}%
      </span>
    </div>
  );
}
