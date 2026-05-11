# Home Screen Widget — Design Spec

**Date:** 2026-05-11  
**Status:** Approved

## Overview

A full-width home screen widget that displays the user's 5 most recent shared items as a horizontal strip of thumbnails or favicons. Tapping an item opens the CleanShare detail screen for that item.

## Interaction Model

| Action | Result |
|--------|--------|
| Tap item cell | Opens `MainActivity` → `DetailScreen` for that item |
| (no secondary action) | — |

Deep-link: `MainActivity` is launched with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` and an intent extra `EXTRA_DETAIL_ID: Long`. On `onCreate`, if the extra is present, the backStack is seeded as `[HistoryRoute, DetailRoute(id)]` so the user can press Back to reach the history list.

## Layout

**Height:** 2 launcher cells (header row + image row)  
**Width:** 5 launcher cells (full screen width)  
**Resize:** horizontal only

```
┌─────────────────────────────────────────────┐
│ 🔗 CleanShare                               │  ← header (Row)
├─────────────────────────────────────────────┤
│ [img] [img] [img] [img] [img]               │  ← items (Row, weight=1 each)
└─────────────────────────────────────────────┘
```

Image row height: fixed at `IconSize.thumbnail` (64dp) — Glance has no `aspectRatio` modifier, so height is set explicitly on the Row and each cell fills it.

Each cell:
- `weight(1f)` width, full row height (64dp)
- Shows thumbnail bitmap if available; otherwise favicon bitmap (`https://www.google.com/s2/favicons?sz=64&domain=…`)
- Falls back to a grey placeholder box if both fail to load
- `ContentScale.Crop`

**Empty state:** when no items exist, a single `Text` replaces the image row:
> "Nothing shared yet - share a link to CleanShare to get started"

## Architecture

### New files

| File | Purpose |
|------|---------|
| `widget/RecentSharesWidget.kt` | `GlanceAppWidget` — UI and `provideGlance` logic |
| `widget/RecentSharesWidgetReceiver.kt` | `GlanceAppWidgetReceiver` — registered in manifest |
| `widget/WidgetBitmapLoader.kt` | Downloads and caches bitmaps from URLs |
| `res/xml/widget_info.xml` | Widget metadata (cell size, resize mode, preview) |
| `res/layout/widget_preview.xml` | Static preview layout shown in the widget picker |

### Modified files

| File | Change |
|------|--------|
| `ShareActivity.kt` | Call `RecentSharesWidget().updateAll(context)` after insert |
| `data/metadata/FetchMetadataWorker.kt` | Call `RecentSharesWidget().updateAll(context)` after metadata stored |
| `AndroidManifest.xml` | Register `RecentSharesWidgetReceiver` with `APPWIDGET_UPDATE` intent filter |
| `app/build.gradle.kts` | Add `androidx.glance:glance-appwidget` dependency |
| `MainActivity.kt` | Read `EXTRA_DETAIL_ID` from intent; seed backStack with `DetailRoute` if present |

### Dependencies

```kotlin
implementation(libs.androidx.glance.appwidget)
```

Add version alias to `libs.versions.toml`.

## Data Flow

Each widget update runs in `provideGlance` (a suspend function):

1. `shareRepository.getAll().first().take(5)` — one-shot DB read, 5 most recent items
2. For each item, `WidgetBitmapLoader.load(url)` concurrently — `url` is `thumbnailUrl` if non-null, else the Google favicon URL
3. `provideContent { RecentSharesWidgetUi(items, bitmaps) }`

## Update Triggers

| Trigger | How |
|---------|-----|
| New share inserted | `ShareActivity` calls `RecentSharesWidget().updateAll(context)` |
| Metadata fetched | `FetchMetadataWorker` calls `RecentSharesWidget().updateAll(context)` |
| System periodic | `updatePeriodMillis=0` — disabled; app-driven only |

## WidgetBitmapLoader

- Uses the app's existing `OkHttpClient` (via `context.applicationContext as CleanShareApplication`)
- Decodes response body to `Bitmap`, scales to 64dp × screen density square
- `LruCache<String, Bitmap>` capped at 4 MB
- Returns `null` on any failure (network error, decode error, HTTP non-2xx)

## widget_info.xml

```xml
<appwidget-provider
    targetCellWidth="5"
    targetCellHeight="2"
    minWidth="250dp"
    minHeight="100dp"
    resizeMode="horizontal"
    updatePeriodMillis="0"
    widgetCategory="home_screen"
    previewLayout="@layout/widget_preview" />
```

## Error Handling

| Failure | Behaviour |
|---------|-----------|
| Bitmap load fails | `null` returned → grey placeholder box rendered |
| DB query throws | Caught in `provideGlance` → empty state message shown |
| `updateAll()` with no widget placed | No-op — safe to call unconditionally |

## Testing

- **Unit:** `WidgetBitmapLoaderTest` — uses `MockWebServer` (already a test dependency) to verify successful decode, cache hit on second call, and `null` return on HTTP error
- **Unit:** `MainActivityIntentTest` — verify backStack seeded correctly when intent carries `EXTRA_DETAIL_ID`
- **Manual:** place widget, share several items, verify strip updates; tap a cell, verify detail screen opens with correct item
