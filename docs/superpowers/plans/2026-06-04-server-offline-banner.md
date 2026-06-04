# Server Offline Banner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display a non-invasive "Server offline · last seen X" banner below the toolbar in HistoryScreen when a configured server is unreachable and the user has connected before.

**Architecture:** Add a `lastSeenAt: Long?` field to `ServerConfig` (persisted in DataStore), write it on successful health check in `SyncManager`, combine `connectionStatus` + `config` in `HistoryViewModel` into an `offlineBannerText: StateFlow<String?>`, and render a thin banner row in `HistoryScreen` when non-null.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore Preferences, `kotlinx.coroutines`, `android.text.format.DateUtils`, JUnit4 + `kotlinx-coroutines-test`

---

## File Map

| File | Change |
|------|--------|
| `app/src/main/java/com/maroney/cleanshare/sync/ServerConfig.kt` | Add `lastSeenAt: Long?` to `ServerConfig`; add `KEY_LAST_SEEN_AT` key and `setLastSeenAt()` to repo |
| `app/src/main/java/com/maroney/cleanshare/sync/SyncManager.kt` | Call `configRepo.setLastSeenAt(System.currentTimeMillis())` after successful health check |
| `app/src/main/java/com/maroney/cleanshare/ui/HistoryViewModel.kt` | Add `configRepo: ServerConfigRepository` param; add `computeOfflineBannerTimestamp()` top-level function; add `offlineBannerText: StateFlow<String?>` |
| `app/src/main/java/com/maroney/cleanshare/ui/HistoryScreen.kt` | Wrap content in `Column`; collect `offlineBannerText`; add private `ServerOfflineBanner` composable |
| `app/src/test/java/com/maroney/cleanshare/ui/OfflineBannerTest.kt` | Unit tests for `computeOfflineBannerTimestamp()` |

---

### Task 1: Add `lastSeenAt` to `ServerConfig` and `ServerConfigRepository`

**Files:**
- Modify: `app/src/main/java/com/maroney/cleanshare/sync/ServerConfig.kt`

- [ ] **Step 1: Add `lastSeenAt` field to `ServerConfig` and persistence to the repository**

Replace the entire content of `ServerConfig.kt` with:

```kotlin
package com.maroney.cleanshare.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ServerConfig(
    val manualHost: String? = null,
    val port: Int? = null,
    val lastSeenAt: Long? = null,
)

private val Context.serverConfigDataStore by preferencesDataStore(name = "server_config")

class ServerConfigRepository(private val context: Context) {

    companion object {
        private val KEY_MANUAL_HOST  = stringPreferencesKey("manual_host")
        private val KEY_PORT         = intPreferencesKey("port")
        private val KEY_LAST_SEEN_AT = longPreferencesKey("last_seen_at")
    }

    val config: Flow<ServerConfig> = context.serverConfigDataStore.data.map { prefs ->
        ServerConfig(
            manualHost = prefs[KEY_MANUAL_HOST],
            port       = prefs[KEY_PORT],
            lastSeenAt = prefs[KEY_LAST_SEEN_AT],
        )
    }

    suspend fun setManualHost(host: String?) {
        context.serverConfigDataStore.edit { prefs ->
            if (host == null) prefs.remove(KEY_MANUAL_HOST) else prefs[KEY_MANUAL_HOST] = host
        }
    }

    suspend fun setPort(port: Int?) {
        context.serverConfigDataStore.edit { prefs ->
            if (port == null) prefs.remove(KEY_PORT) else prefs[KEY_PORT] = port
        }
    }

    suspend fun setLastSeenAt(ts: Long) {
        context.serverConfigDataStore.edit { prefs ->
            prefs[KEY_LAST_SEEN_AT] = ts
        }
    }
}
```

- [ ] **Step 2: Run tests to confirm nothing broke**

```bash
cd /home/patrick/zcode/cleanshare/CleanShare && ./gradlew test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
cd /home/patrick/zcode/cleanshare/CleanShare && git add app/src/main/java/com/maroney/cleanshare/sync/ServerConfig.kt && git commit -m "feat: add lastSeenAt field to ServerConfig and repo"
```

---

### Task 2: Write `lastSeenAt` on successful connection in `SyncManager`

**Files:**
- Modify: `app/src/main/java/com/maroney/cleanshare/sync/SyncManager.kt`

- [ ] **Step 1: Record last-seen timestamp after a successful health check**

In `SyncManager.resolveAndSync()`, find this block (around line 64–72):

```kotlin
        if (!syncClient.health()) {
            syncClient.clear()
            _status.value = ConnectionStatus.Disconnected
            return@withContext false
        }

        _status.value = ConnectionStatus.Connected(host, config.port)
        fullSync()
        true
```

Replace with:

```kotlin
        if (!syncClient.health()) {
            syncClient.clear()
            _status.value = ConnectionStatus.Disconnected
            return@withContext false
        }

        configRepo.setLastSeenAt(System.currentTimeMillis())
        _status.value = ConnectionStatus.Connected(host, config.port)
        fullSync()
        true
```

- [ ] **Step 2: Run tests**

```bash
cd /home/patrick/zcode/cleanshare/CleanShare && ./gradlew test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
cd /home/patrick/zcode/cleanshare/CleanShare && git add app/src/main/java/com/maroney/cleanshare/sync/SyncManager.kt && git commit -m "feat: record lastSeenAt timestamp on successful server connection"
```

---

### Task 3: Add `offlineBannerText` to `HistoryViewModel` (TDD)

**Files:**
- Modify: `app/src/main/java/com/maroney/cleanshare/ui/HistoryViewModel.kt`
- Create: `app/src/test/java/com/maroney/cleanshare/ui/OfflineBannerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/maroney/cleanshare/ui/OfflineBannerTest.kt`:

```kotlin
package com.maroney.cleanshare.ui

import com.maroney.cleanshare.sync.ConnectionStatus
import com.maroney.cleanshare.sync.ServerConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineBannerTest {

    private val host = "192.168.1.1"
    private val lastSeen = 1_000_000L

    @Test
    fun `returns null when Connected`() {
        assertNull(computeOfflineBannerTimestamp(
            status = ConnectionStatus.Connected(host, 8765),
            config = ServerConfig(manualHost = host, lastSeenAt = lastSeen),
        ))
    }

    @Test
    fun `returns null when Searching`() {
        assertNull(computeOfflineBannerTimestamp(
            status = ConnectionStatus.Searching,
            config = ServerConfig(manualHost = host, lastSeenAt = lastSeen),
        ))
    }

    @Test
    fun `returns null when Disconnected but no host configured`() {
        assertNull(computeOfflineBannerTimestamp(
            status = ConnectionStatus.Disconnected,
            config = ServerConfig(manualHost = null, lastSeenAt = lastSeen),
        ))
    }

    @Test
    fun `returns null when Disconnected but never connected before`() {
        assertNull(computeOfflineBannerTimestamp(
            status = ConnectionStatus.Disconnected,
            config = ServerConfig(manualHost = host, lastSeenAt = null),
        ))
    }

    @Test
    fun `returns lastSeenAt when Disconnected with host and prior connection`() {
        val result = computeOfflineBannerTimestamp(
            status = ConnectionStatus.Disconnected,
            config = ServerConfig(manualHost = host, lastSeenAt = lastSeen),
        )
        assertNotNull(result)
        assert(result == lastSeen)
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd /home/patrick/zcode/cleanshare/CleanShare && ./gradlew test 2>&1 | grep -E "FAILED|error:|Unresolved"
```

Expected: compilation error — `computeOfflineBannerTimestamp` is not defined yet.

- [ ] **Step 3: Add `computeOfflineBannerTimestamp`, `offlineBannerText`, and inject `configRepo` into `HistoryViewModel`**

Replace the entire content of `HistoryViewModel.kt` with:

```kotlin
package com.maroney.cleanshare.ui

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.maroney.cleanshare.CleanShareApplication
import com.maroney.cleanshare.data.ShareRecordWithMetadata
import com.maroney.cleanshare.data.ShareRepository
import com.maroney.cleanshare.data.metadata.MetadataWorkScheduler
import com.maroney.cleanshare.sync.ConnectionStatus
import com.maroney.cleanshare.sync.ServerConfig
import com.maroney.cleanshare.sync.ServerConfigRepository
import com.maroney.cleanshare.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun computeOfflineBannerTimestamp(
    status: ConnectionStatus,
    config: ServerConfig,
): Long? {
    if (status != ConnectionStatus.Disconnected) return null
    if (config.manualHost == null) return null
    return config.lastSeenAt
}

class HistoryViewModel(
    private val repository: ShareRepository,
    private val workScheduler: MetadataWorkScheduler,
    private val syncManager: SyncManager,
    private val configRepo: ServerConfigRepository,
) : ViewModel() {

    val history: StateFlow<List<ShareRecordWithMetadata>> = repository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val offlineBannerText: StateFlow<String?> =
        combine(syncManager.connectionStatus, configRepo.config) { status, config ->
            val ts = computeOfflineBannerTimestamp(status, config) ?: return@combine null
            val relative = DateUtils.getRelativeTimeSpanString(
                ts,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )
            "Server offline · last seen $relative"
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = repository.getAll().first()
            workScheduler.schedulePendingFetches(snapshot)
            syncManager.resolveAndSync()
            syncManager.startListening(viewModelScope)
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                syncManager.fullSync()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun onStart() {
        syncManager.startListening(viewModelScope)
    }

    fun onStop() {
        syncManager.stopListening()
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch { repository.deleteById(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.deleteAll() }
    }

    fun retryFetch(shareRecordId: Long, url: String, syncId: String) {
        workScheduler.retryFetch(shareRecordId, url, syncId)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[APPLICATION_KEY] as CleanShareApplication
                return HistoryViewModel(
                    app.shareRepository,
                    app.workScheduler,
                    app.syncManager,
                    app.serverConfigRepository,
                ) as T
            }
        }
    }
}
```

- [ ] **Step 4: Run tests**

```bash
cd /home/patrick/zcode/cleanshare/CleanShare && ./gradlew test
```

Expected: `BUILD SUCCESSFUL`, all `OfflineBannerTest` cases pass.

- [ ] **Step 5: Commit**

```bash
cd /home/patrick/zcode/cleanshare/CleanShare && git add app/src/main/java/com/maroney/cleanshare/ui/HistoryViewModel.kt app/src/test/java/com/maroney/cleanshare/ui/OfflineBannerTest.kt && git commit -m "feat: add offlineBannerText to HistoryViewModel with tests"
```

---

### Task 4: Render `ServerOfflineBanner` in `HistoryScreen`

**Files:**
- Modify: `app/src/main/java/com/maroney/cleanshare/ui/HistoryScreen.kt`

- [ ] **Step 1: Add banner collection and composable**

Replace the entire content of `HistoryScreen.kt` with:

```kotlin
package com.maroney.cleanshare.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maroney.cleanshare.ui.theme.LocalColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateToDetail: (id: Long) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val offlineBannerText by viewModel.offlineBannerText.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP)  { viewModel.onStop() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clean Share") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Sync settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            offlineBannerText?.let { text -> ServerOfflineBanner(text) }
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f),
            ) {
                if (history.isEmpty()) {
                    EmptyState(modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = Spacing.sm),
                    ) {
                        items(history, key = { it.record.id }) { item ->
                            HistoryItem(
                                item = item,
                                onNavigate = { onNavigateToDetail(item.record.id) },
                            )
                            if (item != history.last()) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerOfflineBanner(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Default.Circle,
            contentDescription = null,
            tint = LocalColors.current.status.off,
            modifier = Modifier.size(IconSize.statusDot),
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No shares yet.\nShare a link from any app to get started.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
```

- [ ] **Step 2: Run tests**

```bash
cd /home/patrick/zcode/cleanshare/CleanShare && ./gradlew test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
cd /home/patrick/zcode/cleanshare/CleanShare && git add app/src/main/java/com/maroney/cleanshare/ui/HistoryScreen.kt && git commit -m "feat: render ServerOfflineBanner in HistoryScreen"
```
