# Detail Screen Design

**Date:** 2026-05-09  
**Status:** Approved

## Overview

Add a detail screen reachable by tapping any row in the history list. The screen displays the full URL comparison, complete metadata description, a user-editable notes field (auto-saved), and CTAs for all actions currently in the overflow menu. Long-tap on rows is removed; tap now navigates instead of opening the link.

---

## Navigation

**Library:** Jetpack Compose Navigation 3 (`androidx.navigation:navigation3-ui`)

**Routes:**
```kotlin
data object HistoryRoute
data class DetailRoute(val id: Long)
```

**Back stack:** `rememberMutableStateListOf<Any>(HistoryRoute)` held in `MainActivity`. `NavDisplay` renders the current entry.

**Navigate to detail:** `backStack.add(DetailRoute(id = item.id))`  
**Navigate back:** handled automatically by Nav3 system back; Delete CTA calls `backStack.removeLast()` then triggers deletion.

---

## Data Layer Changes

### `ShareRecord` — new field
```kotlin
val notes: String? = null
```
Requires a Room migration (version increment, `ALTER TABLE share_history ADD COLUMN notes TEXT`).

### `ShareDao` — new query
```kotlin
@Query("UPDATE share_history SET notes = :notes WHERE id = :id")
suspend fun updateNotes(id: Long, notes: String?)
```

### `ShareRepository`
Expose `suspend fun updateNotes(id: Long, notes: String?)` delegating to the DAO.

---

## DetailViewModel

Scoped to the detail destination. Receives the item `id` via a factory or `SavedStateHandle`.

**State:**
- `uiState: StateFlow<ShareRecordWithMetadata?>` — loaded by ID, null while loading or if deleted
- `notes: StateFlow<String>` — local draft, initialised from `ShareRecord.notes`

**Notes saving:**
- `onNotesChanged(text: String)` — updates local `notes` state, cancels previous debounce job, launches a 500 ms debounce coroutine to call `repository.updateNotes()`
- `onNotesFocusLost()` — cancels debounce, saves immediately
- `onCleared()` — cancels debounce, saves immediately (guards against back-swipe mid-edit)

**Delete:**
- `deleteItem()` — calls `repository.delete(id)`, emits a `Deleted` side effect consumed by the screen to pop back

**Retry:**
- `retryMetadataFetch()` — delegates to existing repository retry logic

---

## DetailScreen Composable

**File:** `ui/DetailScreen.kt`

### Layout (scrollable `Column` inside `Scaffold`)

1. **TopAppBar** — title "Link Details", back arrow (navigates up)
2. **Header card** — 64×64 thumbnail (or 32×32 favicon fallback, or nothing if neither available) + title (max 2 lines) + relative timestamp
3. **URLs section** — labelled "URLS"
   - "CLEANED" sub-label + `cleanedText` in blue tinted monospace box
   - "ORIGINAL" sub-label + `originalText` in grey monospace box
   - Both boxes use `word-break: break-all` equivalent (`softWrap = true`)
   - Section hidden entirely if `cleanedText == originalText` (nothing was removed)
4. **Description section** — labelled "DESCRIPTION"
   - Shows `metadata.description` if non-null/non-blank, else `metadata.articleSnippet`
   - Section hidden entirely if both are null/blank
5. **Notes section** — labelled "NOTES"
   - `OutlinedTextField` with placeholder "Add notes…", `minLines = 3`
   - Wired to `viewModel.notes` and `viewModel.onNotesChanged()`
   - `onFocusChanged` calls `viewModel.onNotesFocusLost()` when focus is lost
   - No "auto-saved" indicator (implicit behaviour)
6. **CTA buttons** — full-width row at the bottom of the scroll content (not pinned)
   - **Open Link** (filled) — launches `Intent.ACTION_VIEW` with `cleanedText`
   - **Copy Link** (outlined) — copies `cleanedText` to clipboard + haptic
   - **Delete** (outlined, error colour) — calls `viewModel.deleteItem()`
   - **Retry Metadata Fetch** (outlined, neutral) — only shown when `metadata.fetchStatus == FAILED`; calls `viewModel.retryMetadataFetch()`

### Deletion flow
`DetailViewModel` exposes a `SharedFlow<Unit>` called `deleted`. `DetailScreen` collects it with `LaunchedEffect` and calls the `onNavigateBack` lambda when it fires.

---

## HistoryItem Changes

- Remove `onLongClick` from `combinedClickable` on all row variants (LayoutA, LayoutC, FallbackRow, ShimmerRow)
- Change `onClick` from launching `Intent.ACTION_VIEW` to calling an `onTap: (id: Long) -> Unit` lambda passed from the parent
- Overflow menu items remain unchanged (Copy, Open, Delete, Retry)

---

## Sizing & Colours

All sizing uses tokens from `Dimensions.kt`. No raw dp values.

| Element | Token |
|---|---|
| Header thumbnail | `IconSize.thumbnail` (64 dp) |
| Header favicon fallback | `IconSize.favicon` (32 dp) |
| Standard padding | `Spacing.md` (16 dp) |
| Gap between URL sub-sections | `Spacing.sm` (8 dp) |
| URL box corner radius | `Radius.sm` (4 dp) |
| Button corner radius | `Radius.md` (8 dp) |

Colours from `LocalColors.current` and Material 3 theme only. No hard-coded `Color(...)` values.

---

## Out of Scope

- Sharing from the detail screen
- Editing the URL
- Pinning or favouriting items
