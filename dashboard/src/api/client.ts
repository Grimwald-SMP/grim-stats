import type {
  ApiKeyView,
  ConfigView,
  CreatedApiKey,
  Health,
  Leaderboard,
  Me,
  ObjectiveInfo,
  PinnedStat,
  PlayerStats,
  Role,
  Season,
  SeasonInfo,
  StatTypeInfo,
  UserView,
  WorldInfo,
} from './types';

// In production the dashboard is served by the mod from the same origin, so the base is empty
// (relative /api). In dev, point VITE_API_BASE at the running mod (e.g. http://127.0.0.1:8765).
const API_BASE = (import.meta.env.VITE_API_BASE as string | undefined)?.replace(/\/$/, '') ?? '';

const TOKEN_KEY = 'grimstats.token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null): void {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

export function apiUrl(path: string): string {
  return `${API_BASE}${path}`;
}

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers = new Headers(init.headers);
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  const res = await fetch(apiUrl(path), { ...init, headers });
  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = await res.json();
      if (body?.error) message = body.error;
    } catch {
      // non-JSON error body; keep statusText
    }
    throw new ApiError(res.status, message);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

export const api = {
  health: () => request<Health>('/api/health'),
  world: () => request<WorldInfo>('/api/world'),
  players: () => request<PlayerStats[]>('/api/players'),
  player: (uuid: string) => request<PlayerStats>(`/api/players/${uuid}`),
  scoreboard: () => request<ObjectiveInfo[]>('/api/scoreboard'),
  pinned: () => request<PinnedStat[]>('/api/pinned'),
  leaderboard: (statType: string, statKey: string, limit = 50) =>
    request<Leaderboard>(
      `/api/leaderboard?type=${encodeURIComponent(statType)}&key=${encodeURIComponent(statKey)}&limit=${limit}`,
    ),
  statTypes: () => request<StatTypeInfo[]>('/api/stats/registry'),

  // Seasons (archived world stats)
  seasons: () => request<SeasonInfo[]>('/api/seasons'),
  season: (id: string) => request<Season>(`/api/seasons/${encodeURIComponent(id)}`),
  seasonDownloadUrl: (id: string) => apiUrl(`/api/seasons/${encodeURIComponent(id)}?download=1`),
  exportSeason: (name: string) =>
    request<SeasonInfo>('/api/admin/seasons/export', { method: 'POST', body: JSON.stringify({ name }) }),
  importSeason: (json: string) =>
    request<SeasonInfo>('/api/admin/seasons/import', { method: 'POST', body: json }),
  deleteSeason: (id: string) =>
    request<{ ok: boolean }>(`/api/admin/seasons/${encodeURIComponent(id)}`, { method: 'DELETE' }),

  login: (username: string, password: string) =>
    request<{ token: string; username: string; role: Role }>('/api/admin/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),
  logout: () => request<{ ok: boolean }>('/api/admin/logout', { method: 'POST' }),
  me: () => request<Me>('/api/admin/me'),
  getConfig: () => request<ConfigView>('/api/admin/config'),
  putConfig: (cfg: ConfigView) =>
    request<ConfigView>('/api/admin/config', { method: 'PUT', body: JSON.stringify(cfg) }),
  refresh: () => request<{ ok: boolean }>('/api/admin/refresh', { method: 'POST' }),

  // Account
  changePassword: (currentPassword: string, newPassword: string) =>
    request<{ ok: boolean }>('/api/admin/password', {
      method: 'PUT',
      body: JSON.stringify({ currentPassword, newPassword }),
    }),

  // Users (root manages)
  users: () => request<UserView[]>('/api/admin/users'),
  addUser: (username: string, password: string, role: Role) =>
    request<UserView>('/api/admin/users', {
      method: 'POST',
      body: JSON.stringify({ username, password, role }),
    }),
  updateUser: (username: string, patch: { role?: Role; password?: string }) =>
    request<UserView>(`/api/admin/users/${encodeURIComponent(username)}`, {
      method: 'PUT',
      body: JSON.stringify(patch),
    }),
  deleteUser: (username: string) =>
    request<{ ok: boolean }>(`/api/admin/users/${encodeURIComponent(username)}`, { method: 'DELETE' }),

  // API keys (admin creates; ROOT keys require ROOT)
  apiKeys: () => request<ApiKeyView[]>('/api/admin/apikeys'),
  createApiKey: (name: string, role: Role) =>
    request<CreatedApiKey>('/api/admin/apikeys', { method: 'POST', body: JSON.stringify({ name, role }) }),
  revokeApiKey: (id: string) =>
    request<{ ok: boolean }>(`/api/admin/apikeys/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
