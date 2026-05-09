# Link Metadata Fetching — Design Spec
**Date:** 2026-05-09  
**Status:** Approved

---

## Overview

When a URL is shared through CleanShare, fetch its metadata (title, thumbnail, description, article snippet) in the background using WorkManager and display it in the history list. If the fetch doesn't complete before the user opens MainActivity, missing metadata is fetched then. Rows show a shimmer while loading and adapt their layout based on what metadata is available.

---

## Data Layer

### New Room Entity: `LinkMetadata`

```kotlin
enum class ContentType { VIDEO, ARTICLE, UNKNOWN }
enum class FetchStatus { SUCCESS, FAILED }

@Entity(tableName = "link_metadata")
data class LinkMetadata(
    @PrimaryKey val shareRecordId: Long,
    val title: String?,
    val thumbnailUrl: String?,
    val description: String?,
    val articleSnippet: String?,   // populated for ARTICLE, null otherwise (~300 chars max)
    val contentType: ContentType,
    val fetchStatus: FetchStatus,
)
```

**Fetch state machine:**
- Row absent → pending (never attempted)
- Row present, `fetchStatus = SUCCESS` → metadata available
- Row present, `fetchStatus = FAILED` → fetch failed, retry available

### `LinkMetadataDao`

| Method | Signature | Notes |
|--------|-----------|-------|
| `upsert` | `suspend fun upsert(metadata: LinkMetadata)` | `@Upsert` — inserts or replaces |
| `observeAll` | `fun observeAll(): Flow<List<LinkMetadata>>` | Emits on every change |
| `getPendingIds` | `suspend fun getPendingIds(): List<Long>` | Cross-table query: `SELECT id FROM share_history WHERE id NOT IN (SELECT shareRecordId FROM link_metadata)` |
| `deleteAll` | `suspend fun deleteAll()` | Called with ShareDao.deleteAll |

### `ShareDatabase` Changes

- Bump version: 1 → 2
- Add `LinkMetadata` to `@Database(entities = [...])` 
- Add `Migration(1, 2)` that creates the `link_metadata` table

### Combined UI Model

```kotlin
data class ShareRecordWithMetadata(
    val record: ShareRecord,
    val metadata: LinkMetadata?,   // null = loading (no row yet)
)
```

`ShareRepository` combines `ShareDao.getAll()` and `LinkMetadataDao.observeAll()` via `combine()`, keying metadata by `shareRecordId`.

---

## Metadata Fetcher

**Class:** `MetadataFetcher` (no Android deps — pure Kotlin, injectable/testable)

**Dependencies:** OkHttp (already in build via Coil), Jsoup (new)

**Algorithm:**

1. GET the URL with OkHttp (follow redirects, 10s timeout)
2. On non-200 or network error → return `null` (caller writes `FAILED` row)
3. Parse with Jsoup:
   - `og:title` → `title`
   - `og:image` → `thumbnailUrl`
   - `og:description` → `description`
   - `og:type` → determines `contentType`:
     - starts with `"video"` → `VIDEO`, skip snippet
     - `"article"` or absent but `<article>` tag present → `ARTICLE`, extract snippet
     - otherwise → `UNKNOWN`
4. Article snippet: first ~300 chars of text from `<article>` element, or first 2–3 `<p>` tags if no `<article>`
5. Returns populated `LinkMetadata` with `fetchStatus = SUCCESS`

**New Gradle dependency:** `org.jsoup:jsoup:1.17.2`

---

## WorkManager

**New Gradle dependency:** `androidx.work:work-runtime-ktx:2.9.1`

### `FetchMetadataWorker : CoroutineWorker`

| Input key | Type | Description |
|-----------|------|-------------|
| `KEY_SHARE_RECORD_ID` | `Long` | ID of the `ShareRecord` |
| `KEY_URL` | `String` | Cleaned URL to fetch |

**Behavior:**
1. Call `MetadataFetcher.fetch(url)`
2. If result is non-null → `dao.upsert(result.copy(shareRecordId = id, fetchStatus = SUCCESS))`
3. If result is null → `dao.upsert(LinkMetadata(shareRecordId = id, ..., fetchStatus = FAILED))`
4. Always return `Result.success()` — failures are recorded in Room, not retried automatically by WorkManager

**Work name:** `"metadata_$shareRecordId"` with `ExistingWorkPolicy.KEEP` (prevents duplicate enqueues).

### `MetadataWorkScheduler`

Two entry points:

```kotlin
fun scheduleFetch(shareRecordId: Long, url: String)      // called from ShareRepository.insert()
fun schedulePendingFetches(records: List<ShareRecord>)   // called from HistoryViewModel.init
```

`schedulePendingFetches` queries `getPendingIds()` and enqueues workers only for those missing metadata.

**Retry (user-initiated):** `HistoryViewModel.retryFetch(shareRecordId, url)` enqueues with `ExistingWorkPolicy.REPLACE`.

---

## UI

### Row State Machine

| Condition | Layout |
|-----------|--------|
| `metadata == null` | Shimmer (loading) |
| `metadata != null && thumbnailUrl != null` | Layout A — leading 64×64 thumbnail |
| `metadata != null && thumbnailUrl == null && fetchStatus == SUCCESS` | Layout C — 32×32 favicon |
| `metadata != null && fetchStatus == FAILED` | Fallback — current behavior |

### Layout A (thumbnail present)

```
[ 64×64 thumbnail ] Title (weight 600, 14sp)
                    Description (12sp, muted)
                    cleaned-url (11sp, monospace, tertiary)    [⋮]
```

Original URL shown below cleaned URL in tertiary style only if they differ (same as current behavior).

### Layout C (favicon only)

```
[ 32×32 favicon ] Title (weight 600, 14sp)
                  Description / snippet (12sp, muted)
                  cleaned-url (11sp, monospace, tertiary)      [⋮]
```

### Fallback (fetch failed)

```
[ 32×32 favicon ] cleaned-url (13sp, monospace, primary)
                  original-url (11sp, monospace, tertiary)     [⋮]
```

### Shimmer

Animate a left-to-right gradient sweep (`Brush.linearGradient` + `rememberInfiniteTransition`) over placeholder `Box` shapes matching the expected layout dimensions:
- Thumbnail placeholder: 64×64 rounded rect
- Title placeholder: ~80% width, 14sp height
- Description placeholders: two lines (100% + 65% width)
- URL placeholder: ~55% width

### Overflow Menu (`⋮`)

All rows:
- Copy link
- Open link

Failed rows additionally:
- Retry metadata fetch (top of menu)

---

## Files Affected

| File | Change |
|------|--------|
| `data/LinkMetadata.kt` | New entity + enums |
| `data/LinkMetadataDao.kt` | New DAO |
| `data/ShareDatabase.kt` | Add entity, bump version, add migration |
| `data/ShareRepository.kt` | Merge flows, call scheduler on insert |
| `data/metadata/MetadataFetcher.kt` | New — OkHttp + Jsoup fetcher |
| `data/metadata/FetchMetadataWorker.kt` | New — CoroutineWorker |
| `data/metadata/MetadataWorkScheduler.kt` | New — enqueue helpers |
| `ui/HistoryViewModel.kt` | Consume `ShareRecordWithMetadata`, init pending fetch, expose retry |
| `ui/HistoryScreen.kt` | Adaptive row composable, shimmer, overflow menu |
| `app/build.gradle.kts` | Add WorkManager + Jsoup dependencies |

---

## Out of Scope

- Domain-specific API fetchers (YouTube Data API, etc.) — OG tags sufficient for v1
- Automatic WorkManager retry on failure — user-initiated retry only
- Thumbnail caching beyond what Coil provides natively
