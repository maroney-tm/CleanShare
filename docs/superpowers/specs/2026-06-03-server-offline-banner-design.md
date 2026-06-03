# Server Offline Banner — Design Spec

**Date:** 2026-06-03
**Status:** Approved

## Overview

When a server address is configured and the user has connected before, but the server is currently unreachable, display a non-invasive status row beneath the toolbar in `HistoryScreen`. It reads something like:

> ⬤ Server offline · last seen 3 hours ago

The banner is purely informational — no tap target, no dismiss.

## Conditions for display

All three must be true:

1. `ServerConfig.manualHost != null` — a server address is configured
2. `ServerConfig.lastSeenAt != null` — the user has successfully connected at least once
3. `ConnectionStatus == Disconnected` — the server is currently unreachable

When `ConnectionStatus == Searching`, the banner is hidden (the check is in progress). When `ConnectionStatus == Connected`, the banner is hidden.

## Data layer — `ServerConfigRepository`

Add one DataStore key: `last_seen_at` (Long, epoch milliseconds).

- Add `lastSeenAt: Long?` field to `ServerConfig` data class (nullable, defaults to null)
- Add `setLastSeenAt(ts: Long)` suspend method to `ServerConfigRepository`
- No migration needed — absent key reads as null

## `SyncManager`

In `resolveAndSync()`, after the health check passes (just before returning `true`), call:

```kotlin
configRepo.setLastSeenAt(System.currentTimeMillis())
```

No other changes to `SyncManager`.

## `HistoryViewModel`

Combine `syncManager.connectionStatus` and `configRepo.config` into a new derived flow:

```kotlin
val offlineBannerText: StateFlow<String?>
```

- `null` → banner hidden
- Non-null → banner text to display

Logic: emit a non-null string when `status == Disconnected && config.manualHost != null && config.lastSeenAt != null`. The string is formatted as `"Server offline · last seen X"` where X comes from `DateUtils.getRelativeTimeSpanString(lastSeenAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)`.

`configRepo.config` is already available as a constructor parameter on `SyncManager`, so inject it into `HistoryViewModel` via the existing factory pattern (the application-level `serverConfigRepository` instance).

## `HistoryScreen`

Add a small private composable `ServerOfflineBanner(text: String)` inside `HistoryScreen.kt`. Collect `offlineBannerText` from the viewmodel. When non-null, render it between the `TopAppBar` scaffold area and the `PullToRefreshBox`.

Visual spec:
- Row, `Spacing.sm` vertical padding, `Spacing.md` horizontal padding
- Leading `Icons.Default.Circle` icon, tinted `LocalColors.current.status.off` (grey), sized `IconSize.statusDot`
- `Spacing.sm` gap between icon and text
- `bodySmall` text style, `onSurfaceVariant` color
- No background, no border — sits flush against the app bar

Currently the Scaffold's lambda passes `innerPadding` directly to `PullToRefreshBox`. Change it to wrap both the banner and `PullToRefreshBox` in a `Column` that consumes `innerPadding`, so the banner appears immediately below the app bar:

```kotlin
Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
    if (bannerText != null) ServerOfflineBanner(bannerText)
    PullToRefreshBox(...) { ... }
}
```

## No-flash guarantee

`resolveAndSync()` is called inside the `init` coroutine of `HistoryViewModel`, which sets `ConnectionStatus` to `Searching` before the health check runs. The banner is hidden during `Searching`. The brief gap between ViewModel creation and coroutine start is sub-frame and inconsequential in practice.

## Files changed

| File | Change |
|------|--------|
| `sync/ServerConfig.kt` | Add `lastSeenAt: Long?` to `ServerConfig`; add `setLastSeenAt()` to repo |
| `sync/SyncManager.kt` | Write `lastSeenAt` on successful `resolveAndSync()` |
| `ui/HistoryViewModel.kt` | Add `offlineBannerText` derived flow; inject `configRepo` |
| `ui/HistoryScreen.kt` | Add `ServerOfflineBanner` composable; collect + render |

## Out of scope

- `SyncSettingsScreen` / `SyncSettingsViewModel` — untouched
- `ConnectionStatus` sealed class — untouched
- No new files needed
