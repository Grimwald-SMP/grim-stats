# GrimStats HTTP API

Everything the dashboard shows is available as JSON from the mod's embedded HTTP server
(default `http://<server-ip>:8765`). This document covers authentication, every endpoint,
and the response shapes.

- [Authentication](#authentication)
- [Visibility rules](#visibility-rules)
- [Read endpoints](#read-endpoints)
- [Live stream (SSE)](#live-stream-sse)
- [Seasons](#seasons)
- [Admin endpoints](#admin-endpoints)
- [Link preview images](#link-preview-images)
- [CORS](#cors)
- [Errors](#errors)

## Authentication

Two credential types, both sent as a bearer token:

```
Authorization: Bearer <session-token or API key>
```

| Type | Obtained from | Notes |
|---|---|---|
| Session token | `POST /api/admin/login` | HMAC-signed, expires after `auth.sessionTtlMinutes` (default 720) |
| API key | Admin view or `POST /api/admin/apikeys` | Format `gsk_<id>_<secret>`; shown once at creation, stored hashed |

Both carry a **role**:

- `ADMIN` sees hidden stats, manages settings/pins, creates API keys, changes own password.
- `ROOT` additionally manages users and can create or revoke any key. The last ROOT user can
  never be demoted or deleted.

The first account is created in-game by an operator: `/grimstats setup <username> <password>`.

## Visibility rules

- If `display.publicDashboard` is `false`, all read endpoints except `/api/health`, `/api/ui`
  and `/api/admin/login` require authentication.
- Stat types listed in `display.hiddenStatTypes` and objectives in `display.hiddenObjectives`
  are stripped from responses for unauthenticated callers. Admins always see everything.
- The world seed is `null` unless `display.exposeSeed` is enabled and the caller is an admin.

## Read endpoints

All are `GET` and return JSON.

### `GET /api/health`

Liveness probe. Never requires auth.

```json
{"status": "ok", "mod": "grimstats", "snapshotAge": 512, "players": 14}
```

`snapshotAge` is milliseconds since the stats snapshot was built (they rebuild every
`collection.snapshotIntervalSeconds`, default 10).

### `GET /api/ui`

Public bootstrap for the dashboard shell. Never requires auth.

```json
{"defaultTheme": "grimwald", "publicDashboard": true}
```

### `GET /api/world`

```json
{
  "serverName": "My Server", "motd": "Welcome", "minecraftVersion": "1.21.11",
  "onlinePlayers": 3, "maxPlayers": 20,
  "gameTime": 812000, "dayTime": 6000,
  "difficulty": "normal", "hardcore": false, "seed": null,
  "dimensions": [{"id": "minecraft:overworld", "time": 812000}]
}
```

### `GET /api/players`

Array of player summaries. `stats` is empty here; fetch a single player for the full map.
`highlights` (derived headline metrics) is always present, so lists can be ranked cheaply.

```json
[{
  "uuid": "9d9f64ec-...", "name": "ThisIsRoc", "online": true, "lastSeenEpochMs": null,
  "highlights": {
    "playTime": 9021, "deaths": 5, "deathsToPlayers": 0, "mobKills": 0, "playerKills": 0,
    "distanceCm": 23509, "blocksMined": 0, "itemsCrafted": 0, "damageDealt": 0, "damageTaken": 270
  },
  "stats": {}, "scores": []
}]
```

Notes: `playTime` is in ticks (20/s), distances in centimeters, damage in tenths of a heart.
`deathsToPlayers` counts PvP deaths (`minecraft:killed_by` -> `minecraft:player`).

### `GET /api/players/{uuid}`

One player with the full stat map: stat type id to (stat key to value). Modded and datapack
stat types appear automatically because collection iterates the game registry.

```json
{
  "uuid": "...", "name": "...", "online": false, "lastSeenEpochMs": 1782782834344,
  "highlights": { "...": 0 },
  "stats": {
    "minecraft:custom":   {"minecraft:play_time": 9021, "minecraft:jump": 12},
    "minecraft:mined":    {"minecraft:stone": 300},
    "minecraft:killed_by": {"minecraft:slime": 1}
  },
  "scores": [{"objective": "kills", "value": 7}]
}
```

### `GET /api/scoreboard`

All objectives with ranked entries:

```json
[{
  "name": "kills", "displayName": "Kills", "criterion": "playerKillCount", "renderType": "integer",
  "entries": [{"holder": "ThisIsRoc", "value": 7}]
}]
```

### `GET /api/leaderboard?type=<statType>&key=<statKey>&limit=<n>`

On-demand ranking for any single stat (default limit 50):

```
GET /api/leaderboard?type=minecraft:mined&key=minecraft:diamond_ore&limit=10
```

```json
{
  "statType": "minecraft:mined", "statKey": "minecraft:diamond_ore",
  "count": 12, "total": 4183,
  "entries": [{"rank": 1, "uuid": "...", "name": "...", "value": 913}]
}
```

### `GET /api/pinned`

The admin-pinned stats with their top entries, as shown on the dashboard homepage.

### `GET /api/stats/registry`

Every stat type the server knows (including modded), with all keys, for building pickers:

```json
[{"id": "minecraft:mined", "translationKey": "stat_type.minecraft.mined", "modded": false, "keys": ["minecraft:stone", "..."]}]
```

## Live stream (SSE)

### `GET /api/stream`

A Server-Sent Events stream. Each event's `data:` is a full snapshot (world, players with
highlights, objectives, pinned stats), pushed whenever a new snapshot is built. Because
`EventSource` cannot set headers, the endpoint also accepts `?token=<token or API key>`.

```js
const es = new EventSource('http://host:8765/api/stream');
es.onmessage = (e) => console.log(JSON.parse(e.data));
```

## Seasons

Archived worlds. A season is a portable JSON document with full per-player stat maps.
See the README section "Seasons" for the workflow.

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/seasons` | public* | list of summaries (newest first) |
| GET | `/api/seasons/{id}` | public* | full archive; `?download=1` adds an attachment header |
| POST | `/api/admin/seasons/export` | admin | body `{"name": "Season 3"}`; archives the live snapshot |
| POST | `/api/admin/seasons/import` | admin | body is a season JSON document (max 32 MB) |
| DELETE | `/api/admin/seasons/{id}` | admin | removes the archive file |

*subject to `publicDashboard` and hidden-stat filtering, like all reads.

Summary shape:

```json
{
  "id": "season-1-mr2mk7jb", "name": "Season 1", "createdAtEpochMs": 1782943559975,
  "serverName": "world", "minecraftVersion": "1.21.11", "gameTime": 24287,
  "playerCount": 14, "totalPlayTimeTicks": 1284000
}
```

The full document adds `formatVersion`, `players` (same shape as `/api/players/{uuid}`) and
`objectives`. On import, ids are regenerated server-side and missing highlights are recomputed,
so files exported by any GrimStats server (or built by hand) can be shared freely.

## Admin endpoints

All require a bearer token unless noted. `ROOT`-only rows are marked.

| Method | Path | Notes |
|---|---|---|
| POST | `/api/admin/login` | `{"username", "password"}` -> `{"token", "username", "role"}` (no auth) |
| POST | `/api/admin/logout` | revokes the current session token |
| GET | `/api/admin/me` | `{"name", "role", "kind"}`; `kind` is `SESSION` or `API_KEY` |
| PUT | `/api/admin/password` | `{"currentPassword", "newPassword"}`; sessions only |
| GET | `/api/admin/users` | list `{username, role}` |
| POST | `/api/admin/users` | ROOT. `{"username", "password", "role"}` |
| PUT | `/api/admin/users/{username}` | ROOT. `{"role"?, "password"?}`; cannot demote the last ROOT |
| DELETE | `/api/admin/users/{username}` | ROOT. cannot remove the last ROOT |
| GET | `/api/admin/apikeys` | list key metadata (never the secret) |
| POST | `/api/admin/apikeys` | `{"name", "role"}` -> `{"key": "gsk_..."}`; ROOT keys need ROOT; the full key is returned exactly once |
| DELETE | `/api/admin/apikeys/{id}` | ROOT revokes any; an admin only keys they created |
| POST | `/api/admin/refresh` | force a snapshot rebuild now |
| GET/PUT | `/api/admin/config` | read or update settings: http, display (theme, visibility, pins), collection, session TTL |

## Link preview images

Server-rendered 1200x630 PNG cards used by Discord and other platforms when links are shared.
Public by design, but player data only appears when `publicDashboard` is true.

| Path | Content |
|---|---|
| `/og/player/{uuid}.png` | player card: name, face, headline stats |
| `/og/leaderboard.png` | top players by play time |
| `/og/site.png` | generic server card |

## CORS

By default (`http.corsAllowAll: true`) every origin is allowed via a wildcard. This is safe
because authentication is header-based (no cookies), so a malicious site has no ambient
credentials to ride on. Set `corsAllowAll` to `false` and list `corsOrigins` explicitly to
restrict, which also enables credentialed requests for those origins.

## Errors

Errors are JSON with an HTTP status:

```json
{"error": "invalid credentials"}
```

| Status | Meaning |
|---|---|
| 400 | malformed request or body |
| 401 | missing or invalid credentials |
| 403 | authenticated but not allowed (role, private dashboard, hidden stat) |
| 404 | unknown player, season, user or key |
| 405 | wrong HTTP method |
| 409 | conflict (duplicate user, last-ROOT protection) |
| 413 | request body over the size cap (1 MiB, or 32 MiB for season import) |
| 429 | too many login attempts from your address; wait and retry |
| 503 | too many concurrent live-stream connections; retry shortly |

Hardening notes: login is rate limited per client address and runs equal-cost work whether or not
the username exists (no timing enumeration). Session tokens are accepted in `?token=` only on
`/api/stream`; use the `Authorization` header everywhere else.
