# GrimStats

[![CI](https://github.com/grimstats/grim-stats/actions/workflows/ci.yml/badge.svg)](https://github.com/grimstats/grim-stats/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Minecraft 1.21.11](https://img.shields.io/badge/minecraft-1.21.11-brightgreen)
![Fabric](https://img.shields.io/badge/loader-fabric-dbb69b)

A lightweight Fabric server mod that gives your world a **web dashboard and JSON API** for every
statistic the game tracks. One jar, nothing else to run: the dashboard is bundled inside the mod
and served by its own embedded HTTP server.

## Features

- **Every stat, automatically.** Collectors iterate the game's registries, so all vanilla
  statistics, scoreboard objectives, and anything added by mods or datapacks show up without
  configuration.
- **Interactive dashboard.** Leaderboards with podiums, per-player profiles with radar and combat
  panels, distance and mining breakdowns, pinned stats on the homepage, live updates over SSE,
  theme picker with a custom "grimwald" default.
- **Seasons.** Archive a world's full stats as portable JSON before a reset, import archives from
  other servers, and keep past seasons browsable forever.
- **Share-ready links.** Player and leaderboard URLs unfurl on Discord with live stat summaries
  and server-rendered preview images.
- **Admin tools.** Multiple accounts with ROOT/ADMIN roles, API keys, visibility controls for
  stats and objectives, all managed from the dashboard.
- **Lightweight by design.** An async snapshot cache means HTTP requests never touch game state
  or the disk. Live data is read on the server thread as cheap in-memory lookups; offline-player
  files are read off-thread.
- **26.1-ready.** File access goes through the game's own path API plus a version-aware layout
  layer, so the upcoming world-storage overhaul is a recompile, not a rewrite.

## Installation (server admins)

1. Download `grimstats-<version>.jar` from [Releases](https://github.com/grimstats/grim-stats/releases).
2. Drop it into your Fabric 1.21.11 server's `mods/` folder alongside
   [Fabric API](https://modrinth.com/mod/fabric-api). Java 21+ is required.
3. Start the server. The dashboard listens on all interfaces on port `8765`:
   `http://<server-ip>:8765` (or `http://localhost:8765` locally).
4. Create the admin account in-game as an operator:
   ```
   /grimstats setup <username> <password>
   ```
   `/grimstats info` prints the dashboard URL.
5. Open the dashboard, browse stats, and sign in at **Admin** to manage visibility, users,
   API keys, pinned stats and settings.

> Exposing the dashboard beyond your LAN? Put it behind a reverse proxy with TLS so credentials
> are not sent in plaintext, or set `http.host` to `127.0.0.1` to keep it local-only.

## Configuration

`config/grimstats.json` (created on first start, editable live from the admin view):

| Section | Keys |
|---|---|
| `http` | `host` (default `0.0.0.0`), `port` (8765), `corsAllowAll`, `corsOrigins`, `threadPoolSize` |
| `auth` | `users` (managed via dashboard), `apiKeys`, `sessionTtlMinutes`, `tokenSecret` |
| `display` | `defaultTheme`, `publicDashboard`, `hiddenStatTypes`, `hiddenObjectives`, `exposeSeed`, `pinned` |
| `collection` | `snapshotIntervalSeconds`, `includeOfflinePlayers`, `maxPlayers` |

## Seasons (archive, export, import)

Worlds come and go; their stats can stay. A **season** is a frozen JSON archive of a world's full
statistics that remains browsable on the dashboard's **Seasons** page, with totals, podiums and
per-metric leaderboards over the archived players.

- **Archive** the current world from the Seasons page before a reset.
- **Download** any season as a self-contained JSON file.
- **Import** a season file from another server or an older world. Files are validated and
  normalized on upload.

Season files live in `config/grimstats-seasons/`, one JSON file per season, so they survive world
deletion and can be backed up or copied between servers by hand.

## API

Everything the dashboard shows is available as JSON, authenticated with session tokens or API
keys (`gsk_...`) created from the admin view. The most used endpoints:

| Method | Path | Notes |
|---|---|---|
| GET | `/api/health` | liveness + snapshot age |
| GET | `/api/world` | server and world summary |
| GET | `/api/players` | player list with headline stats |
| GET | `/api/players/{uuid}` | full per-player stats + scores |
| GET | `/api/leaderboard?type=&key=` | rank any stat on demand |
| GET | `/api/seasons` | archived seasons |
| GET | `/api/stream` | Server-Sent Events live snapshots |

The complete reference, including auth, admin endpoints, seasons import/export, response shapes
and error codes, is in **[docs/API.md](docs/API.md)**.

## Building from source

Requirements: JDK 21+, Node.js 18+.

```bash
./gradlew build          # dashboard + mod + tests -> mod/build/libs/grimstats-<version>.jar
./scripts/build.sh       # same, and prints the jar path (build.ps1 on Windows)
```

The Gradle build installs dashboard dependencies, builds the SPA, bundles it into the jar and
runs the mod's tests. Use `-PskipDashboard` for quick mod-only iterations (a previously built
`dashboard/dist` is still bundled if present).

## Development

```
grim-stats/
  mod/         Fabric mod (Java 21, Mojang mappings, Loom 1.14)
  dashboard/   React + TypeScript + Tailwind v4 + daisyUI v5, built with Vite
  scripts/     dev, build, test and release helpers (each as .ps1 and .sh)
  docs/        API reference
```

| Script | What it does |
|---|---|
| `scripts/dev` | test server on `:8765` + hot-reloading dashboard on `:5173` |
| `scripts/build` | full release build, prints the jar path |
| `scripts/test` | mod JUnit tests + dashboard Vitest |
| `scripts/bump-version` | bumps `major`/`minor`/`patch` or an explicit `x.y.z` across gradle.properties, package.json and CHANGELOG.md; `-Tag`/`--tag` also commits and tags |

Or run the test server alone with `./gradlew :mod:runTestServer` (writes `eula.txt` and a fast
flat-world `server.properties`, then boots a throwaway server).

Tests: `./gradlew :mod:test` (world layouts, auth, sessions, config, seasons, meta injection),
`cd dashboard && npm test` (helpers, theming), plus Playwright smoke tests via `npm run test:e2e`.

## Releasing

1. `./scripts/bump-version.ps1 minor -Tag` (or `bump-version.sh minor --tag`)
2. `git push && git push origin v<version>`

Pushing the tag triggers the [release workflow](.github/workflows/release.yml), which verifies the
tag matches `mod_version`, builds the jar, extracts the matching CHANGELOG section, and publishes a
GitHub release with the jar attached. CI builds and tests every push and pull request.

## Minecraft 26.1 readiness

26.1 reorganizes world storage (dimensions under `dimensions/`, player data under `players/`,
namespaced data such as `data/minecraft/scoreboard.dat`). GrimStats is built to absorb this:
Mojang mappings already, the game's own `LevelResource` path API for file access, a documented
`WorldLayout` abstraction isolating the remaining path assumptions, and live-API-first collection
so file changes only affect offline-player reads. `gradle.properties` documents the mechanical
switch (Loom 1.15, Java 25, Loader 0.18.4).

## License

[MIT](LICENSE)
