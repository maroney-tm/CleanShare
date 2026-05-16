# CleanShare Sync — Design Spec

**Date:** 2026-05-16
**Status:** Approved

---

## Overview

Add bidirectional, LAN-only sync of share history across devices. The Go sync server is the authoritative source of truth. Android (and a future desktop TUI) are local caches that sync to it. The app remains fully functional offline — sync is a silent, additive layer.

---

## Scope

This spec covers two of three sub-projects in the sync initiative:

1. **Go sync server** — new standalone service (`CleanShareServer/` repo)
2. **Android sync integration** — changes to the existing CleanShare Android app

**Out of scope for this spec:** Desktop TUI client (separate spec + plan).

**Explicitly out of MVP scope:** Backfill of pre-existing local records on first server connection.

---

## System Architecture

```
┌─────────────────────┐        REST + SSE        ┌──────────────────────┐
│   Android Client    │ ◄──────────────────────► │   Go Sync Server     │
│  (existing app)     │                           │  (new, LAN-only)     │
└─────────────────────┘                           └──────────┬───────────┘
                                                             │ REST + SSE
                                                  ┌──────────▼───────────┐
                                                  │   Desktop TUI        │
                                                  │  (future)            │
                                                  └──────────────────────┘
```

**Source of truth:** Go server's SQLite DB.
**Local caches:** Android Room DB, future TUI local store.
**Transport:** REST for writes, SSE for real-time change broadcast.
**Discovery:** mDNS (`_cleanshare._tcp`) with manual IP+port fallback.

---

## Conflict Resolution

**Strategy: Last-Write-Wins (LWW) via `updatedAt` timestamp.**

- Every `ShareRecord` carries an `updatedAt` millisecond epoch timestamp.
- Set to `sharedAt` on creation; bumped on every notes edit.
- On `PATCH`, the server compares `request.updatedAt` vs `stored.updatedAt`. If the incoming value is newer, the update is applied and broadcast. If not, the stored record is returned and the client reconciles its local copy.
- Chosen because simultaneous edits across devices are effectively impossible for a personal, single-user tool on a LAN.

---

## Data Model Changes

### New `ShareSource` enum

```kotlin
enum class ShareSource { MOBILE, DESKTOP }
```

- Added to existing `Converters.kt` alongside `ContentType` and `FetchStatus`.
- `MOBILE`: set by the Android client (and any future iOS client).
- `DESKTOP`: set by the TUI client.
- Source is determined by the creating client and never mutated.

### `ShareRecord` — new fields

```kotlin
@Entity(tableName = "share_history")
data class ShareRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalText: String,
    val cleanedText: String,
    val sharedAt: Long = System.currentTimeMillis(),
    val notes: String? = null,
    // --- new in v4 ---
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: ShareSource = ShareSource.MOBILE,
)
```

- **`syncId`**: UUID string. Cross-device stable identity used in all network calls. Android Room continues using the integer `id` for all local relationships — `link_metadata` FK is unchanged.
- **`updatedAt`**: milliseconds since epoch. Used for LWW resolution.
- **`source`**: which client type created the record. UI may show a phone/monitor icon in history list.

### New `ShareDao` queries

```kotlin
@Query("UPDATE share_history SET updated_at = :updatedAt WHERE sync_id = :syncId")
suspend fun touchUpdatedAt(syncId: String, updatedAt: Long)

@Query("SELECT * FROM share_history WHERE sync_id = :syncId")
suspend fun getBySyncId(syncId: String): ShareRecord?

@Query("DELETE FROM share_history WHERE sync_id = :syncId")
suspend fun deleteBySyncId(syncId: String)
```

### Room migration v3 → v4

```sql
ALTER TABLE share_history ADD COLUMN sync_id    TEXT    NOT NULL DEFAULT '';
ALTER TABLE share_history ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0;
ALTER TABLE share_history ADD COLUMN source     TEXT    NOT NULL DEFAULT 'MOBILE';

-- Backfill syncId with random UUIDs for existing records
UPDATE share_history
SET sync_id = lower(
    hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-' ||
    hex(randomblob(2)) || '-' || hex(randomblob(2)) || '-' ||
    hex(randomblob(6))
)
WHERE sync_id = '';

-- Backfill updatedAt from sharedAt for existing records
UPDATE share_history SET updated_at = shared_at WHERE updated_at = 0;
```

- Existing records default to `source = 'MOBILE'` — correct, the app has been Android-only.
- `LinkMetadata` and its DAO are unchanged.

---

## Go Sync Server

### Repository

New standalone repo: `CleanShareServer/` (sibling to the Android project directory). Go tooling expects its own module root.

### Project structure

```
CleanShareServer/
├── main.go        — flag parsing, startup, graceful shutdown
├── server.go      — HTTP router and handlers
├── store.go       — SQLite CRUD (modernc.org/sqlite — pure Go, no CGo)
├── hub.go         — SSE pub/sub hub (channel-based, one goroutine)
├── mdns.go        — mDNS service registration
└── schema.sql     — embedded via go:embed
```

### SQLite schema

```sql
CREATE TABLE share_records (
    sync_id       TEXT    PRIMARY KEY,
    original_text TEXT    NOT NULL,
    cleaned_text  TEXT    NOT NULL,
    shared_at     INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL,
    notes         TEXT,
    source        TEXT    NOT NULL DEFAULT 'MOBILE'
);

CREATE TABLE link_metadata (
    sync_id         TEXT PRIMARY KEY REFERENCES share_records(sync_id),
    title           TEXT,
    thumbnail_url   TEXT,
    description     TEXT,
    article_snippet TEXT,
    content_type    TEXT NOT NULL,
    fetch_status    TEXT NOT NULL
);
```

### REST API

| Method   | Path                  | Purpose                                                                 |
|----------|-----------------------|-------------------------------------------------------------------------|
| `GET`    | `/records`            | Full record list — used on reconnect/resume for full reconciliation     |
| `POST`   | `/records`            | Create record; broadcast SSE `record_created`                           |
| `PATCH`  | `/records/{syncId}`   | Update notes (LWW check); broadcast SSE `record_updated` if applied     |
| `DELETE` | `/records/{syncId}`   | Hard delete; broadcast SSE `record_deleted`                             |
| `GET`    | `/events`             | SSE stream (`text/event-stream`)                                        |
| `PUT`    | `/records/{syncId}/metadata` | Upsert link metadata; broadcast SSE `record_metadata_updated`  |
| `GET`    | `/health`             | 200 OK — used by clients to test connectivity                           |

**LWW on PATCH:** If `request.updatedAt > stored.updatedAt`, apply and broadcast. Otherwise return stored record — client reconciles.

**Deletes:** Hard delete from SQLite + SSE `record_deleted` broadcast. Clients that missed the event reconcile on next resume via full pull (absent records are deleted locally).

**Metadata:** `GET /records` returns each record with its `linkMetadata` object embedded (nullable). No LWW needed for metadata — it is fetched deterministically from the URL and only ever written by Android's `FetchMetadataWorker`.

### SSE hub

- Single goroutine owns a `map[clientID]chan Event`.
- HTTP handlers send to the hub via a central channel; hub fans out to all connected clients.
- Client disconnect detected via `r.Context().Done()`; stale clients pruned immediately.

### SSE event types

```
record_created          — full record JSON (linkMetadata null at creation)
record_updated          — full record JSON
record_deleted          — { "syncId": "..." }
record_metadata_updated — full record JSON with linkMetadata populated
```

### mDNS

- Library: `github.com/hashicorp/mdns` (pure Go, well-maintained).
- Service type: `_cleanshare._tcp`.
- Instance name: `CleanShare`.
- Port: configurable via `--port` flag, default `8765`.

### Startup flags

```
--port   int     HTTP listen port (default 8765)
--db     string  Path to SQLite file (default ./cleanshare.db)
```

---

## Android Sync Integration

### New package

`app/src/main/java/com/maroney/cleanshare/sync/`

```
sync/
├── ServerConfig.kt          — host + port, persisted in DataStore
├── NsdDiscoveryHelper.kt    — wraps NsdManager, emits discovered host:port via callbackFlow
├── CleanShareSyncClient.kt  — OkHttp REST calls to the server
├── SyncManager.kt           — orchestrates push/pull/SSE
└── SseListener.kt           — parses SSE stream, applies events to Room
```

### `ServerConfig`

```kotlin
data class ServerConfig(
    val manualHost: String? = null,    // null = use mDNS
    val port: Int = 8765,
    val resolvedHost: String? = null,  // last mDNS-discovered host (cached)
)
```

Persisted in DataStore (proto). Exposed as `Flow<ServerConfig>`.

### Discovery flow

```
App resumes
    │
    ▼
manualHost set?
  ├── yes → use manualHost:port directly
  └── no  → run NsdManager discovery (5 s timeout)
                │
                ├── found → cache resolvedHost, verify with GET /health, connect
                │           (if health check fails → clear cache, rerun discovery)
                └── timeout → SyncManager stays dormant; app works offline
```

`NsdDiscoveryHelper` emits the first resolved `_cleanshare._tcp` service and stops discovery immediately — no continuous background scanning.

### Sync moments

**1. On write** (called from `ShareRepository` after local Room write):
- `insert()`      → `POST /records`
- `updateNotes()` → `PATCH /records/{syncId}`
- `deleteById()`  → `DELETE /records/{syncId}`

Fire-and-forget coroutines, best-effort. Failures are silently swallowed — the next resume sync reconciles.

`FetchMetadataWorker` gains one additional step after its existing local `upsert`: call `PUT /records/{syncId}/metadata` to push the fetched metadata to the server. This is how the TUI (and any future client) receives thumbnails and titles.

**2. On app resume** (called from `HistoryViewModel`):
```
GET /records → for each server record:
    local = dao.getBySyncId(syncId)
    if local == null                       → insert into Room
    if server.updatedAt > local.updatedAt  → update Room
Records present locally but absent from server response → delete from Room
```

**3. SSE while foregrounded** (managed by `HistoryViewModel`):
- `onStart()` → `SyncManager.startListening()` opens `GET /events`
- `onStop()`  → cancels the SSE coroutine (no background battery cost)
- `record_created` / `record_updated` → upsert Room
- `record_deleted` → `dao.deleteBySyncId(syncId)`

### `ShareRepository` changes

- `SyncManager` injected as a nullable dependency (null = no server configured, fully offline).
- Each mutating method launches `scope.launch { syncManager?.push(...) }` after the local Room write.
- Room remains the UI source of truth; sync never blocks the local operation.

### Offline behaviour

If no server is configured or reachable:
- `SyncManager` is a no-op; all push calls return immediately.
- All existing functionality (share, history, widget, metadata fetch) works exactly as before.
- No banners, no onboarding prompts, no permissions required for sync.

### Settings screen — `SyncSettingsScreen`

Reachable via gear icon on `HistoryScreen`.

```
Sync
──────────────────────────────────────────
Connection status
  ● Connected  192.168.1.42:8765
  ○ Searching…
  ○ Not connected

Discover automatically         [toggle]

Server address                     ← active only when toggle is OFF
  [ 192.168.1.42:8765      ]
  [ Test connection ]
──────────────────────────────────────────
```

- **Toggle ON** (default): mDNS discovery runs on resume; manual field disabled.
- **Toggle OFF**: mDNS skipped; manual field active; *Test connection* hits `GET /health`.
- Status chip tappable to force an immediate re-check.

---

## What Is Explicitly Out of Scope (MVP)

| Item | Rationale |
|---|---|
| Backfill of pre-existing local records on first server connect | Added complexity; going-forward sync is sufficient for MVP |
| TLS / HTTPS | LAN-only; no external exposure |
| Authentication | LAN-only; single user |
| Pagination on `GET /records` | Personal tool; dataset will be small |
| Multiple server instances | First mDNS result wins; acceptable for MVP |
| Tombstones / soft deletes | LWW without safety net is sufficient for single user |
| Desktop TUI | Separate spec and plan |
