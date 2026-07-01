import { useEffect, useRef, useState } from 'react';
import { apiUrl, getToken } from '../api/client';
import type {
  ObjectiveInfo,
  PinnedStat,
  PlayerStats,
  StatTypeInfo,
  StatsSnapshot,
  WorldInfo,
} from '../api/types';

interface SnapshotState {
  snapshot: StatsSnapshot | null;
  connected: boolean;
  error: string | null;
}

/**
 * Subscribes to the live snapshot via Server-Sent Events, with an automatic fall back to periodic
 * polling if the stream cannot be established (e.g. a proxy that buffers SSE). The token, if any,
 * is passed as a query param because EventSource cannot send Authorization headers.
 */
export function useSnapshot(pollMs = 10000): SnapshotState {
  const [state, setState] = useState<SnapshotState>({ snapshot: null, connected: false, error: null });
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    let stopped = false;
    let pollTimer: number | undefined;

    const startPolling = () => {
      const tick = async () => {
        try {
          const token = getToken();
          // No SSE available: compose a snapshot from the individual REST endpoints.
          const [players, objectives, statTypes, world, pinnedStats] = await Promise.all([
            fetchJson<PlayerStats[]>('/api/players', token),
            fetchJson<ObjectiveInfo[]>('/api/scoreboard', token),
            fetchJson<StatTypeInfo[]>('/api/stats/registry', token),
            fetchJson<WorldInfo>('/api/world', token),
            fetchJson<PinnedStat[]>('/api/pinned', token),
          ]);
          if (!stopped) {
            setState({
              snapshot: {
                generatedAtEpochMs: Date.now(),
                world: world ?? EMPTY_WORLD,
                players: players ?? [],
                objectives: objectives ?? [],
                statTypes: statTypes ?? [],
                pinnedStats: pinnedStats ?? [],
              },
              connected: false,
              error: null,
            });
          }
        } catch (e) {
          if (!stopped) setState((s) => ({ ...s, error: String(e) }));
        }
      };
      void tick();
      pollTimer = window.setInterval(tick, pollMs);
    };

    const startSse = () => {
      const token = getToken();
      const url = apiUrl('/api/stream') + (token ? `?token=${encodeURIComponent(token)}` : '');
      const es = new EventSource(url);
      esRef.current = es;
      es.addEventListener('snapshot', (ev) => {
        try {
          const snapshot = JSON.parse((ev as MessageEvent).data) as StatsSnapshot;
          if (!stopped) setState({ snapshot, connected: true, error: null });
        } catch {
          /* ignore malformed frame */
        }
      });
      es.onerror = () => {
        es.close();
        esRef.current = null;
        if (!stopped) {
          setState((s) => ({ ...s, connected: false }));
          startPolling();
        }
      };
    };

    if ('EventSource' in window) {
      startSse();
    } else {
      startPolling();
    }

    return () => {
      stopped = true;
      esRef.current?.close();
      if (pollTimer) window.clearInterval(pollTimer);
    };
  }, [pollMs]);

  return state;
}

async function fetchJson<T>(path: string, token: string | null): Promise<T | null> {
  const res = await fetch(apiUrl(path), {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  return res.ok ? ((await res.json()) as T) : null;
}

const EMPTY_WORLD: WorldInfo = {
  serverName: '',
  motd: '',
  minecraftVersion: '',
  onlinePlayers: 0,
  maxPlayers: 0,
  gameTime: 0,
  dayTime: 0,
  difficulty: 'normal',
  hardcore: false,
  seed: null,
  dimensions: [],
};
