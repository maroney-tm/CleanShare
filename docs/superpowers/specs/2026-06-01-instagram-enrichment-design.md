# Instagram Link Enrichment — Design Spec

**Date:** 2026-06-01
**Status:** Approved

## Overview

Enrich the detail screen for Instagram (and future video platform) links using a domain-specific handler system backed by a server-side yt-dlp ingestion pipeline. The Android client displays whatever metadata is available immediately, with real-time state updates via SSE as the server downloads the video.

## Goals

- Detail screen shows rich metadata for Instagram links (username, content type, caption, thumbnail, hashtags, duration, view/like counts)
- Server automatically downloads the video for future extraction use cases (recipes, guitar tabs, etc.)
- Architecture is extensible to YouTube, TikTok, and other platforms without changing the pipeline
- Graceful degradation when the server is not configured or offline

## Non-Goals

- Video playback in the app
- Content extraction / AI analysis (future work)
- Authentication with Instagram or any other platform
- Support for private posts or stories

## Investigated & Rejected Approaches

- **Instagram HTML scraping**: Instagram returns a JS SPA with zero OG tags — no usable data
- **Meta oEmbed API**: Requires an App ID baked into the app. Returns only `author_name`, `thumbnail_url`, `title` — less data than yt-dlp and requires Meta developer app registration
- **Instagram Login OAuth**: Only supports Professional accounts; Basic Display API deprecated December 2024

**Decision**: Use yt-dlp on the server. It extracts richer metadata than oEmbed for free, stores the video for future use, and requires no credentials for public posts.

---

## Architecture

### Domain Handler System (Android)

A `DomainHandler` interface in the `app` module. Each platform gets one implementation. The detail screen queries a `DomainHandlerRegistry` by URL and renders the result.

```
DomainHandler (interface)
  ├── matches(url: String): Boolean
  ├── extractUrlMetadata(url: String): DomainUrlMetadata   // pure URL parsing, no network
  └── DetailSection()                                       // @Composable

DomainHandlerRegistry
  └── handlers: List<DomainHandler>   // first match wins

InstagramDomainHandler : DomainHandler  // initial implementation
```

`DomainUrlMetadata` is a sealed class per domain. `InstagramUrlMetadata` carries:
- `contentType`: POST / REEL / PROFILE / STORY / TV (from URL path segment)
- `shortcode`: from URL path (null for profile URLs)
- `username`: from URL path (only available in profile/story URLs)

### Data Model (Android — Room)

New `IngestionRecord` entity in the `data` module. Platform-agnostic — fields are normalized yt-dlp output, identical across YouTube, Instagram, TikTok, etc. The `DomainHandler` interprets fields for display.

```kotlin
enum class IngestionStatus { QUEUED, EXTRACTING_METADATA, DOWNLOADING, COMPLETE, FAILED }

@Entity(tableName = "ingestion_record")
data class IngestionRecord(
    @PrimaryKey val shareRecordId: Long,
    val status: IngestionStatus,
    val errorMessage: String? = null,
    val title: String? = null,
    val uploader: String? = null,
    val uploaderUrl: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val uploadDate: String? = null,     // yyyyMMdd
    val duration: Int? = null,          // seconds
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val tags: String? = null,           // JSON array string
    val mediaType: String? = null,      // "video", "photo"
    val serverVideoPath: String? = null,
)
```

`ShareRecordWithMetadata` gains an optional `ingestion: IngestionRecord?` field via Room relation. `LinkMetadata` is untouched.

### SSE Protocol

Three new event types from server → client:

**`ingestion_metadata`** (phase 1 complete, ~1s after link received):
```json
{
  "event": "ingestion_metadata",
  "shareRecordId": "abc-123",
  "status": "DOWNLOADING",
  "title": "...",
  "uploader": "natgeo",
  "uploaderUrl": "https://www.instagram.com/natgeo/",
  "description": "The Amazon at dusk...",
  "thumbnailUrl": "https://...",
  "uploadDate": "20240601",
  "duration": 47,
  "viewCount": 120000,
  "likeCount": 8400,
  "tags": "[\"nature\",\"wildlife\"]",
  "mediaType": "video"
}
```

**`ingestion_complete`** (phase 2 complete):
```json
{
  "event": "ingestion_complete",
  "shareRecordId": "abc-123",
  "status": "COMPLETE",
  "serverVideoPath": "/media/abc-123.mp4"
}
```

**`ingestion_failed`** (either phase):
```json
{
  "event": "ingestion_failed",
  "shareRecordId": "abc-123",
  "status": "FAILED",
  "errorMessage": "yt-dlp: video unavailable"
}
```

The existing `SseListener` parses these events. SSE events carry `syncId` (the server's string key); the listener resolves this to the Room `Long` ID via a `ShareRepository.getIdBySyncId()` lookup before upserting `IngestionRecord`. This matches the pattern already used for `record_metadata_updated` events. `DetailViewModel` observes reactively — no polling needed.

### Detail Screen UI

New `DomainSection` slot inserted between the existing generic header and URL blocks:

```
DetailContent layout:
  ├── TopAppBar
  ├── DomainSection(handler, urlMetadata, ingestion)   ← new
  ├── URL blocks
  ├── Description (OG fallback, hidden if DomainSection present)
  ├── Notes
  └── Action buttons
```

`InstagramDomainHandler` composable renders four states:

| `ingestion` | `status` | Display |
|---|---|---|
| `null` | — | Content type badge + shortcode (URL-only data) |
| non-null | `QUEUED` / `EXTRACTING_METADATA` | Skeleton shimmer + "Fetching details…" |
| non-null | `DOWNLOADING` | Full card (header, caption, thumbnail, fields) + linear progress bar |
| non-null | `COMPLETE` / `FAILED` | Full card, no progress. FAILED shows error chip |

Full card layout (approved mockup):
1. Instagram gradient avatar + `@uploader` + content type + age
2. Caption (`description`) text
3. Thumbnail image (from `thumbnailUrl`) with content type badge overlay
4. Structured fields: shortcode (from URL), hashtags (parsed from `tags`)

The existing generic `HeaderSection` is suppressed when a domain handler matches — the handler owns the full header area.

### Server-Side Pipeline (CleanShareServer)

**New file: `ingester.go`**

```go
type Ingester struct {
    store    *Store
    hub      *Hub
    mediaDir string      // e.g. "./media"
    queue    chan string  // syncIDs
}
```

Worker pool (2 goroutines):

1. Update DB → `EXTRACTING_METADATA`, broadcast initial SSE
2. `yt-dlp --dump-json --no-download <url>` → parse stdout → update DB → broadcast `ingestion_metadata` SSE with full fields, status `DOWNLOADING`
3. `yt-dlp -o <mediaDir>/<syncId>.%(ext)s <url>` → update DB `serverVideoPath` + `COMPLETE` → broadcast `ingestion_complete` SSE
4. Any error → `FAILED` → broadcast `ingestion_failed` SSE

**`handlePostRecord`** addition (after existing broadcast):
```go
if ing.Supports(rec.CleanedText) {
    ing.Enqueue(rec.SyncID, rec.CleanedText)
}
```

`Supports()` checks hostname allowlist: `instagram.com`, `youtube.com`, `youtu.be`, `tiktok.com`. Extensible without touching the pipeline.

On startup: records stuck in `QUEUED` or `DOWNLOADING` are re-enqueued (handles server crash mid-job).

**New table in `schema.sql`:**

```sql
CREATE TABLE IF NOT EXISTS ingestion_records (
    sync_id          TEXT PRIMARY KEY REFERENCES share_records(sync_id) ON DELETE CASCADE,
    status           TEXT NOT NULL DEFAULT 'QUEUED',
    error_message    TEXT,
    title            TEXT,
    uploader         TEXT,
    uploader_url     TEXT,
    description      TEXT,
    thumbnail_url    TEXT,
    upload_date      TEXT,
    duration         INTEGER,
    view_count       INTEGER,
    like_count       INTEGER,
    tags             TEXT,
    media_type       TEXT,
    server_video_path TEXT
);
```

---

## Data Flow

```
User shares Instagram link
  → ShareActivity saves to Room
  → SyncPusher POST /records to server
  → handlePostRecord inserts + broadcasts record_created
  → Ingester.Enqueue(syncId, url)
  → worker: yt-dlp --dump-json (phase 1)
  → store.UpsertIngestion() + hub.Broadcast(ingestion_metadata)
  → SseListener → Room upsert → DetailViewModel updates → UI shows full card
  → worker: yt-dlp download (phase 2)
  → store.UpsertIngestion(serverVideoPath) + hub.Broadcast(ingestion_complete)
  → SseListener → Room upsert → DetailViewModel updates → progress bar disappears
```

## Open Questions / Future Work

- Video playback in the app (requires serving video from server)
- Content extraction pipeline (AI, transcription, structured output for recipes/tabs/chords)
- YouTube and TikTok `DomainHandler` implementations (pipeline already supports them via `Supports()` allowlist)
- Handling duplicate downloads if the same link is shared twice
