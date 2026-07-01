# Changelog

All notable changes to GrimStats. The format follows [Keep a Changelog](https://keepachangelog.com/)
and versions follow [Semantic Versioning](https://semver.org/).

## Unreleased

## 0.6.0 - 2026-07-01
### Security
- Request bodies are read through a hard size cap (1 MiB for API/admin endpoints, 32 MiB for season
  imports), refusing oversized uploads with 413 before buffering them, so an anonymous caller can no
  longer exhaust server memory via a large POST.
- Live-stream (`/api/stream`) connections are capped globally and per client address (503 beyond the
  limit) so open SSE connections cannot starve the HTTP thread pool.
- Login is constant-time for unknown usernames (equal-cost dummy hash) and rate limited per client
  address (429 after repeated attempts), mitigating username enumeration and online brute force.
- Session tokens are only accepted in the query string for `/api/stream`; every other endpoint
  requires the `Authorization` header, keeping tokens out of logs and history.
- Archived seasons now apply the same `hiddenObjectives` and `hiddenStatTypes` visibility rules as
  live data for non-admin viewers.

## 0.4.0 - 2026-07-01

### Added
- Seasons: archive a world's full statistics as portable JSON, import archives from other servers,
  and browse past-season leaderboards on the dashboard (`/seasons`).
- Discord/Open Graph link previews: player and leaderboard links unfurl with live stat summaries
  and server-rendered preview images (`/og/...`).
- Player K/D as a rankable leaderboard metric, plus a `deathsToPlayers` highlight
  (PvP deaths, from `minecraft:killed_by` -> `minecraft:player`).
- Players page: sort by activity, recency, kills, K/D or name; online-only filter; stat columns.
- Overview: in-game time-of-day indicator and past-season quick links.

### Changed
- Web server binds `0.0.0.0` by default so the dashboard is reachable from other machines
  (set `http.host` to `127.0.0.1` for local-only).
- Player avatars resolve by username via mc-heads.net, which also works on offline-mode servers.
- Dashboard routing uses real paths (BrowserRouter) instead of hash fragments.

## 0.3.0 - 2026-06-30

### Added
- Multiple admin users with ROOT and ADMIN roles; role-aware sessions.
- API keys (`gsk_...`) with per-key roles, created and revoked from the admin view.
- Password changes and user management endpoints; last-ROOT protection.

## 0.2.0 - 2026-06-29

### Added
- Interactive dashboard: leaderboards with podium, player profile radar and combat panels,
  pinned stats on the homepage, SVG charts, grimwald theme.
- Server-Sent Events live snapshot stream with polling fallback.

## 0.1.0 - 2026-06-28

### Added
- Initial release: registry-driven stat collection (all vanilla + modded/datapack stats),
  scoreboard objectives, async snapshot cache, embedded HTTP server with JSON API and
  bundled React dashboard, PBKDF2 admin auth, 26.1-ready world layout abstraction.
