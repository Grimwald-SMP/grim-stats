// Presentation helpers for turning raw stat ids/values into human-readable text.

/** Strips a namespace and turns snake_case into Title Case: "minecraft:play_time" -> "Play Time". */
export function humanizeKey(id: string): string {
  const withoutNs = id.includes(':') ? id.slice(id.indexOf(':') + 1) : id;
  return withoutNs
    .split(/[._/]/)
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}

/** Returns the namespace of an id, or "minecraft" if none. */
export function namespaceOf(id: string): string {
  return id.includes(':') ? id.slice(0, id.indexOf(':')) : 'minecraft';
}

const SECOND = 20; // ticks per second
const MINUTE = SECOND * 60;
const HOUR = MINUTE * 60;
const DAY = HOUR * 24;

/** Formats a tick count (Minecraft time stats) as a compact duration. */
export function formatTicks(ticks: number): string {
  if (ticks <= 0) return '0s';
  const days = Math.floor(ticks / DAY);
  const hours = Math.floor((ticks % DAY) / HOUR);
  const minutes = Math.floor((ticks % HOUR) / MINUTE);
  const parts: string[] = [];
  if (days) parts.push(`${days}d`);
  if (hours) parts.push(`${hours}h`);
  if (minutes || parts.length === 0) parts.push(`${minutes}m`);
  return parts.join(' ');
}

/** Formats a distance stat (stored in cm) as metres or kilometres. */
export function formatDistanceCm(cm: number): string {
  const metres = cm / 100;
  if (metres >= 1000) return `${(metres / 1000).toFixed(2)} km`;
  return `${metres.toFixed(1)} m`;
}

const TIME_KEYS = new Set([
  'minecraft:play_time',
  'minecraft:total_world_time',
  'minecraft:time_since_death',
  'minecraft:time_since_rest',
  'minecraft:sneak_time',
]);

const DISTANCE_KEYS = /(_one_cm|distance)/;

/** Chooses the most readable rendering for a custom-stat value based on its key. */
export function formatCustomValue(key: string, value: number): string {
  if (TIME_KEYS.has(key)) return formatTicks(value);
  if (DISTANCE_KEYS.test(key)) return formatDistanceCm(value);
  return formatNumber(value);
}

/** Thousands-separated integer. */
export function formatNumber(value: number): string {
  return value.toLocaleString();
}

/** "minecraft:custom" -> "Custom"; used for stat-type group headings. */
export function statTypeLabel(id: string): string {
  return humanizeKey(id);
}

export function relativeTime(epochMs: number | null): string {
  if (!epochMs) return 'unknown';
  const diff = Date.now() - epochMs;
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}
