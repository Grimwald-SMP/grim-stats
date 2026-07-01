import { useEffect, useState, type ReactNode } from 'react';

/** A compact metric card. Minimal by design: label, value, optional sublabel. */
export function StatCard({ label, value, sub }: { label: string; value: ReactNode; sub?: ReactNode }) {
  return (
    <div className="rounded-box border border-base-300 bg-base-100 p-4">
      <div className="text-xs uppercase tracking-wide text-base-content/50">{label}</div>
      <div className="mt-1 text-2xl font-semibold tabular-nums">{value}</div>
      {sub != null && <div className="mt-0.5 text-xs text-base-content/50">{sub}</div>}
    </div>
  );
}

export function SectionTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-3 flex items-center justify-between gap-2">
      <h2 className="text-lg font-semibold">{children}</h2>
      {action}
    </div>
  );
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16 text-base-content/60">
      <span className="loading loading-spinner loading-md" />
      <span>{label}</span>
    </div>
  );
}

export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="rounded-box border border-dashed border-base-300 bg-base-100 px-4 py-12 text-center">
      <p className="font-medium">{title}</p>
      {hint && <p className="mt-1 text-sm text-base-content/50">{hint}</p>}
    </div>
  );
}

/**
 * Player avatar that always renders. A deterministic colored block with the player's initial is the
 * base layer (works offline and for offline-mode UUIDs that have no Minecraft skin). The real face
 * is fetched and layered on top only once it successfully loads, so a failed/blocked request never
 * leaves a broken image.
 *
 * Faces come from mc-heads.net, a NameMC-style service that caches skins behind a CDN. It is far
 * more reliable than Crafatar (which rate-limits aggressively and frequently 429s even for valid
 * players). Crucially, we look up by *username* rather than UUID: offline-mode servers assign
 * name-derived (version-3) UUIDs that no skin service can resolve, but the username still maps to a
 * real Mojang account if one exists, so the actual skin loads. Names with no premium account fall
 * back to the colored initial underneath.
 */
export function Avatar({ uuid, name, size = 32 }: { uuid: string; name: string; size?: number }) {
  const [faceLoaded, setFaceLoaded] = useState(false);
  const initial = (name.trim()[0] ?? '?').toUpperCase();
  const bg = colorFromString(uuid || name);
  const src = `https://mc-heads.net/avatar/${encodeURIComponent(name.trim())}/${size * 2}`;

  // Reset when the player changes so a reused component instance never keeps a stale face visible.
  useEffect(() => setFaceLoaded(false), [src]);

  return (
    <span
      className="relative inline-flex shrink-0 select-none items-center justify-center overflow-hidden rounded"
      style={{ width: size, height: size, background: bg, color: '#fff' }}
      aria-hidden="true"
    >
      <span style={{ fontSize: Math.round(size * 0.45), fontWeight: 600, lineHeight: 1 }}>{initial}</span>
      <img
        src={src}
        alt=""
        width={size}
        height={size}
        loading="lazy"
        decoding="async"
        onLoad={() => setFaceLoaded(true)}
        className="absolute inset-0 h-full w-full object-cover"
        style={{
          imageRendering: 'pixelated',
          opacity: faceLoaded ? 1 : 0,
          transition: 'opacity 150ms ease',
        }}
      />
    </span>
  );
}

/** Deterministic, theme-friendly background color from a string (UUID). */
function colorFromString(input: string): string {
  let hash = 0;
  for (let i = 0; i < input.length; i++) {
    hash = (hash << 5) - hash + input.charCodeAt(i);
    hash |= 0;
  }
  const hue = Math.abs(hash) % 360;
  return `hsl(${hue} 42% 42%)`;
}
