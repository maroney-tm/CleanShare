# Detail Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a detail screen reachable by tapping a history row, showing URL comparison, full description, a persisted notes field, and action CTAs.

**Architecture:** Jetpack Compose Navigation 3 (`NavDisplay` + typed routes) replaces the current single-screen setup in `MainActivity`. A new `DetailViewModel` owns notes debounce/persistence. Row tap navigates; long-tap is removed.

**Tech Stack:** Compose, Navigation 3 (alpha), Room, Coroutines/Flow, Material 3

---

## File Map

| Action | File |
|---|---|
| Modify | `gradle/libs.versions.toml` |
| Modify | `app/build.gradle.kts` |
| Modify | `app/src/main/java/com/maroney/cleanshare/data/ShareRecord.kt` |
| Modify | `app/src/main/java/com/maroney/cleanshare/data/ShareDatabase.kt` |
| Modify | `app/src/main/java/com/maroney/cleanshare/data/ShareDao.kt` |
| Modify | `app/src/main/java/com/maroney/cleanshare/data/LinkMetadataDao.kt` |
| Modify | `app/src/main/java/com/maroney/cleanshare/data/ShareRepository.kt` |
| Create | `app/src/main/java/com/maroney/cleanshare/ui/AppRoutes.kt` |
| Create | `app/src/main/java/com/maroney/cleanshare/ui/DetailViewModel.kt` |
| Create | `app/src/main/java/com/maroney/cleanshare/ui/DetailScreen.kt` |
| Modify | `app/src/main/java/com/maroney/cleanshare/MainActivity.kt` |
| Modify | `app/src/main/java/com/maroney/cleanshare/ui/HistoryScreen.kt` |
| Modify | `app/src/main/java/com/maroney/cleanshare/ui/HistoryItem.kt` |
| Create | `app/src/androidTest/java/com/maroney/cleanshare/ShareDaoNotesTest.kt` |

---

### Task 1: Add Navigation 3 dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add nav3 version and library entries to the version catalog**

In `gradle/libs.versions.toml`, add to `[versions]`:
```toml
navigation3 = "1.0.0-alpha01"
```

> **Note:** Verify the latest alpha on https://developer.android.com/jetpack/androidx/releases/navigation before building. The artifact group is `androidx.navigation`, artifacts are `navigation3-ui` and `navigation3-runtime`.

Add to `[libraries]`:
```toml
androidx-navigation3-ui = { group = "androidx.navigation", name = "navigation3-ui", version.ref = "navigation3" }
```

- [ ] **Step 2: Add the dependency to app/build.gradle.kts**

In the `dependencies { }` block, after the existing lifecycle lines, add:
```kotlin
implementation(libs.androidx.navigation3.ui)
```

- [ ] **Step 3: Sync Gradle and confirm it resolves**

Run:
```
./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep navigation3
```
Expected: one line containing `navigation3-ui`.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add Navigation 3 dependency"
```

---

### Task 2: Add `notes` column — data layer

Adds the `notes` field to `ShareRecord`, a Room migration, and the DAO/repository methods needed to read and write it.

**Files:**
- Modify: `app/src/main/java/com/maroney/cleanshare/data/ShareRecord.kt`
- Modify: `app/src/main/java/com/maroney/cleanshare/data/ShareDatabase.kt`
- Modify: `app/src/main/java/com/maroney/cleanshare/data/ShareDao.kt`
- Modify: `app/src/main/java/com/maroney/cleanshare/data/LinkMetadataDao.kt`
- Modify: `app/src/main/java/com/maroney/cleanshare/data/ShareRepository.kt`
- Create: `app/src/androidTest/java/com/maroney/cleanshare/ShareDaoNotesTest.kt`

- [ ] **Step 1: Write the failing DAO test**

Create `app/src/androidTest/java/com/maroney/cleanshare/ShareDaoNotesTest.kt`:

```kotlin
package com.maroney.cleanshare

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maroney.cleanshare.data.ShareDao
import com.maroney.cleanshare.data.ShareDatabase
import com.maroney.cleanshare.data.ShareRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareDaoNotesTest {

    private lateinit var db: ShareDatabase
    private lateinit var dao: ShareDao

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, ShareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.shareDao()
    }

    @After fun teardown() { db.close() }

    private suspend fun insertRecord(): Long =
        dao.insert(ShareRecord(originalText = "https://example.com", cleanedText = "https://example.com"))

    @Test fun notes_defaults_to_null() = runTest {
        val id = insertRecord()
        val record = dao.getById(id).first()!!
        assertNull(record.notes)
    }

    @Test fun updateNotes_persists_value() = runTest {
        val id = insertRecord()
        dao.updateNotes(id, "my note")
        val record = dao.getById(id).first()!!
        assertEquals("my note", record.notes)
    }

    @Test fun updateNotes_can_clear_to_null() = runTest {
        val id = insertRecord()
        dao.updateNotes(id, "something")
        dao.updateNotes(id, null)
        val record = dao.getById(id).first()!!
        assertNull(record.notes)
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```
./gradlew connectedDebugAndroidTest --tests "com.maroney.cleanshare.ShareDaoNotesTest"
```
Expected: FAIL — `getById` and `updateNotes` do not exist yet.

- [ ] **Step 3: Add `notes` field to ShareRecord**

Replace the full content of `ShareRecord.kt`:

```kotlin
package com.maroney.cleanshare.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "share_history")
data class ShareRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalText: String,
    val cleanedText: String,
    val sharedAt: Long = System.currentTimeMillis(),
    val notes: String? = null,
)
```

- [ ] **Step 4: Add `getById` and `updateNotes` to ShareDao**

Replace the full content of `ShareDao.kt`:

```kotlin
package com.maroney.cleanshare.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShareDao {

    @Query("SELECT * FROM share_history ORDER BY sharedAt DESC")
    fun getAll(): Flow<List<ShareRecord>>

    @Query("SELECT * FROM share_history WHERE id = :id")
    fun getById(id: Long): Flow<ShareRecord?>

    @Insert
    suspend fun insert(record: ShareRecord): Long

    @Query("UPDATE share_history SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String?)

    @Query("DELETE FROM share_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM share_history")
    suspend fun deleteAll()
}
```

- [ ] **Step 5: Add `getById` to LinkMetadataDao**

Replace the full content of `LinkMetadataDao.kt`:

```kotlin
package com.maroney.cleanshare.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkMetadataDao {

    @Upsert
    suspend fun upsert(metadata: LinkMetadata)

    @Query("SELECT * FROM link_metadata ORDER BY shareRecordId DESC")
    fun observeAll(): Flow<List<LinkMetadata>>

    @Query("SELECT * FROM link_metadata WHERE shareRecordId = :shareRecordId")
    fun getById(shareRecordId: Long): Flow<LinkMetadata?>

    @Query("DELETE FROM link_metadata WHERE shareRecordId = :shareRecordId")
    suspend fun deleteByShareRecordId(shareRecordId: Long)

    @Query("DELETE FROM link_metadata")
    suspend fun deleteAll()
}
```

- [ ] **Step 6: Add Room migration v2→v3 and bump database version**

Replace the full content of `ShareDatabase.kt`:

```kotlin
package com.maroney.cleanshare.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ShareRecord::class, LinkMetadata::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class ShareDatabase : RoomDatabase() {

    abstract fun shareDao(): ShareDao
    abstract fun linkMetadataDao(): LinkMetadataDao

    companion object {
        @Volatile private var instance: ShareDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS link_metadata (
                        shareRecordId INTEGER NOT NULL PRIMARY KEY,
                        title TEXT,
                        thumbnailUrl TEXT,
                        description TEXT,
                        articleSnippet TEXT,
                        contentType TEXT NOT NULL,
                        fetchStatus TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE share_history ADD COLUMN notes TEXT")
            }
        }

        fun getInstance(context: Context): ShareDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShareDatabase::class.java,
                    "share_history.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
```

- [ ] **Step 7: Add `getById` and `updateNotes` to ShareRepository**

Replace the full content of `ShareRepository.kt`:

```kotlin
package com.maroney.cleanshare.data

import com.maroney.cleanshare.data.metadata.MetadataWorkScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ShareRepository(
    private val shareDao: ShareDao,
    private val metadataDao: LinkMetadataDao,
    private val workScheduler: MetadataWorkScheduler,
) {

    fun getAll(): Flow<List<ShareRecordWithMetadata>> =
        combine(shareDao.getAll(), metadataDao.observeAll()) { records, metadataList ->
            val byId = metadataList.associateBy { it.shareRecordId }
            records.map { ShareRecordWithMetadata(it, byId[it.id]) }
        }

    fun getById(id: Long): Flow<ShareRecordWithMetadata?> =
        combine(shareDao.getById(id), metadataDao.getById(id)) { record, metadata ->
            record?.let { ShareRecordWithMetadata(it, metadata) }
        }

    suspend fun insert(record: ShareRecord) {
        val id = shareDao.insert(record)
        val url = record.cleanedText
            .split("\\s+".toRegex())
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
        if (url != null) workScheduler.scheduleFetch(id, url)
    }

    suspend fun updateNotes(id: Long, notes: String?) {
        shareDao.updateNotes(id, notes)
    }

    suspend fun deleteById(id: Long) {
        metadataDao.deleteByShareRecordId(id)
        shareDao.deleteById(id)
    }

    suspend fun deleteAll() {
        shareDao.deleteAll()
        metadataDao.deleteAll()
    }
}
```

- [ ] **Step 8: Run tests to confirm they pass**

```
./gradlew connectedDebugAndroidTest --tests "com.maroney.cleanshare.ShareDaoNotesTest"
```
Expected: 3 tests PASS.

Also run existing DAO tests to verify no regression:
```
./gradlew connectedDebugAndroidTest --tests "com.maroney.cleanshare.LinkMetadataDaoTest"
```
Expected: 3 tests PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/maroney/cleanshare/data/ \
        app/src/androidTest/java/com/maroney/cleanshare/ShareDaoNotesTest.kt
git commit -m "feat: add notes column and getById queries to data layer"
```

---

### Task 3: Create DetailViewModel

**Files:**
- Create: `app/src/main/java/com/maroney/cleanshare/ui/DetailViewModel.kt`

- [ ] **Step 1: Create DetailViewModel.kt**

```kotlin
package com.maroney.cleanshare.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.maroney.cleanshare.CleanShareApplication
import com.maroney.cleanshare.data.ShareRecordWithMetadata
import com.maroney.cleanshare.data.ShareRepository
import com.maroney.cleanshare.data.metadata.MetadataWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(
    private val id: Long,
    private val repository: ShareRepository,
    private val workScheduler: MetadataWorkScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShareRecordWithMetadata?>(null)
    val uiState: StateFlow<ShareRecordWithMetadata?> = _uiState.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _deleted = MutableSharedFlow<Unit>()
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    private var debounceJob: Job? = null
    private var pendingSave = false

    private var notesInitialized = false

    init {
        viewModelScope.launch {
            repository.getById(id).collect { item ->
                _uiState.value = item
                if (!notesInitialized && item != null) {
                    _notes.value = item.record.notes ?: ""
                    notesInitialized = true
                }
            }
        }
    }

    fun onNotesChanged(text: String) {
        _notes.value = text
        pendingSave = true
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(500)
            repository.updateNotes(id, text)
            pendingSave = false
        }
    }

    fun onNotesFocusLost() {
        if (!pendingSave) return
        debounceJob?.cancel()
        debounceJob = null
        pendingSave = false
        viewModelScope.launch { repository.updateNotes(id, _notes.value) }
    }

    fun deleteItem() {
        viewModelScope.launch {
            repository.deleteById(id)
            _deleted.emit(Unit)
        }
    }

    fun retryMetadataFetch() {
        val url = _uiState.value?.record?.cleanedText ?: return
        workScheduler.retryFetch(id, url)
    }

    override fun onCleared() {
        super.onCleared()
        if (pendingSave) {
            debounceJob?.cancel()
            val notesSnapshot = _notes.value
            CoroutineScope(Dispatchers.IO).launch {
                repository.updateNotes(id, notesSnapshot)
            }
        }
    }

    companion object {
        fun factory(id: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[APPLICATION_KEY] as CleanShareApplication
                return DetailViewModel(id, app.shareRepository, app.workScheduler) as T
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

```
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL, no errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/maroney/cleanshare/ui/DetailViewModel.kt
git commit -m "feat: add DetailViewModel with notes debounce"
```

---

### Task 4: Define routes and create DetailScreen

**Files:**
- Create: `app/src/main/java/com/maroney/cleanshare/ui/AppRoutes.kt`
- Create: `app/src/main/java/com/maroney/cleanshare/ui/DetailScreen.kt`

- [ ] **Step 1: Create AppRoutes.kt**

```kotlin
package com.maroney.cleanshare.ui

data object HistoryRoute
data class DetailRoute(val id: Long)
```

- [ ] **Step 2: Create DetailScreen.kt**

```kotlin
package com.maroney.cleanshare.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.maroney.cleanshare.data.FetchStatus
import com.maroney.cleanshare.data.ShareRecordWithMetadata
import com.maroney.cleanshare.ui.theme.LocalColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    id: Long,
    onNavigateBack: () -> Unit,
) {
    val vm: DetailViewModel = viewModel(factory = DetailViewModel.factory(id))
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.deleted.collect { onNavigateBack() }
    }

    val item = uiState ?: return

    val onOpen: () -> Unit = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, item.record.cleanedText.toUri()))
        } catch (_: Exception) { }
    }
    val onCopy: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            clipboard.setClipEntry(
                ClipData.newPlainText("link", item.record.cleanedText).toClipEntry()
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Link Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            HeaderSection(item)

            if (item.record.cleanedText != item.record.originalText) {
                SectionLabel("URLs")
                UrlBlock(label = "CLEANED", url = item.record.cleanedText, highlighted = true)
                Spacer(Modifier.height(Spacing.sm))
                UrlBlock(label = "ORIGINAL", url = item.record.originalText, highlighted = false)
            }

            val description = item.metadata?.description?.takeIf { it.isNotBlank() }
                ?: item.metadata?.articleSnippet?.takeIf { it.isNotBlank() }
            if (description != null) {
                SectionLabel("Description")
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }

            SectionLabel("Notes")
            OutlinedTextField(
                value = notes,
                onValueChange = { vm.onNotesChanged(it) },
                placeholder = { Text("Add notes…") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md)
                    .onFocusChanged { if (!it.isFocused) vm.onNotesFocusLost() },
            )

            Spacer(Modifier.height(Spacing.md))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Link")
                }
                OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                    Text("Copy Link")
                }
                OutlinedButton(
                    onClick = { vm.deleteItem() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = SolidColor(MaterialTheme.colorScheme.error),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete")
                }
                if (item.metadata?.fetchStatus == FetchStatus.FAILED) {
                    OutlinedButton(
                        onClick = { vm.retryMetadataFetch() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Retry Metadata Fetch")
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun HeaderSection(item: ShareRecordWithMetadata) {
    val thumbnailUrl = item.metadata?.thumbnailUrl
    val faviconUrl = remember(item.record.cleanedText) {
        runCatching { item.record.cleanedText.toUri().host }
            .getOrNull()?.takeIf { it.isNotBlank() }
            ?.let { "https://www.google.com/s2/favicons?sz=64&domain=$it" }
    }
    val title = item.metadata?.title

    if (thumbnailUrl == null && faviconUrl == null && title == null) return

    Row(
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            thumbnailUrl != null -> AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(IconSize.thumbnail)
                    .clip(RoundedCornerShape(Radius.md))
                    .border(Dp.Hairline, LocalColors.current.layout.divider, RoundedCornerShape(Radius.md)),
            )
            faviconUrl != null -> AsyncImage(
                model = faviconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(IconSize.favicon)
                    .clip(RoundedCornerShape(Radius.md))
                    .border(Dp.Hairline, LocalColors.current.layout.divider, RoundedCornerShape(Radius.md)),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            title?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            }
            Text(
                text = formatAge(item.record.sharedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.xs),
    )
}

@Composable
private fun UrlBlock(label: String, url: String, highlighted: Boolean) {
    val containerColor: Color
    val contentColor: Color
    if (highlighted) {
        containerColor = MaterialTheme.colorScheme.primaryContainer
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        containerColor = MaterialTheme.colorScheme.surfaceVariant
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor,
        modifier = Modifier.padding(horizontal = Spacing.md),
    )
    Spacer(Modifier.height(Spacing.xs))
    Text(
        text = url,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = contentColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .clip(RoundedCornerShape(Radius.sm))
            .background(containerColor)
            .padding(Spacing.sm),
    )
}
```

- [ ] **Step 3: Build to verify it compiles**

```
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/maroney/cleanshare/ui/AppRoutes.kt \
        app/src/main/java/com/maroney/cleanshare/ui/DetailScreen.kt
git commit -m "feat: add DetailScreen and route definitions"
```

---

### Task 5: Wire navigation in MainActivity

**Files:**
- Modify: `app/src/main/java/com/maroney/cleanshare/MainActivity.kt`

- [ ] **Step 1: Replace MainActivity content**

Replace the full content of `MainActivity.kt`:

```kotlin
package com.maroney.cleanshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.maroney.cleanshare.ui.DetailRoute
import com.maroney.cleanshare.ui.DetailScreen
import com.maroney.cleanshare.ui.HistoryRoute
import com.maroney.cleanshare.ui.HistoryScreen
import com.maroney.cleanshare.ui.HistoryViewModel
import com.maroney.cleanshare.ui.theme.CleanShareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CleanShareTheme {
                val backStack = rememberNavBackStack(HistoryRoute)
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                ) { key ->
                    when (key) {
                        HistoryRoute -> {
                            val viewModel: HistoryViewModel =
                                viewModel(factory = HistoryViewModel.Factory)
                            HistoryScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { id -> backStack.add(DetailRoute(id)) },
                            )
                        }
                        is DetailRoute -> {
                            DetailScreen(
                                id = key.id,
                                onNavigateBack = { backStack.removeLastOrNull() },
                            )
                        }
                    }
                }
            }
        }
    }
}
```

> **Import note:** The Nav3 package names may differ from the imports above. If `androidx.navigation3.runtime.rememberNavBackStack` or `androidx.navigation3.ui.NavDisplay` don't resolve, check the artifact's actual package by looking at the sources in Android Studio (Cmd+click the dependency in build.gradle.kts after Gradle sync, or browse `.gradle/caches`). Common alternatives: `androidx.navigation.compose3.*` or `androidx.navigation3.*`.

- [ ] **Step 2: Build**

```
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL. Fix any import errors using the note above.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/maroney/cleanshare/MainActivity.kt
git commit -m "feat: wire Navigation 3 NavDisplay in MainActivity"
```

---

### Task 6: Update HistoryItem and HistoryScreen

Replaces tap-to-open-link with tap-to-navigate, and removes long-tap.

**Files:**
- Modify: `app/src/main/java/com/maroney/cleanshare/ui/HistoryItem.kt`
- Modify: `app/src/main/java/com/maroney/cleanshare/ui/HistoryScreen.kt`

- [ ] **Step 1: Update HistoryItem — add `onNavigate`, remove long-tap**

In `HistoryItem.kt`, make two changes:

**1a.** Change the function signature — add `onNavigate: () -> Unit`, remove the intent/clipboard logic that lived on tap:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItem(
    item: ShareRecordWithMetadata,
    onNavigate: () -> Unit,
    onRetryFetch: (shareRecordId: Long, url: String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val cleanedUrl = item.record.cleanedText

    val onOpen: () -> Unit = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, cleanedUrl.toUri()))
        } catch (_: Exception) { }
    }
    val onCopy: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            clipboard.setClipEntry(ClipData.newPlainText("link", cleanedUrl).toClipEntry())
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onNavigate),   // ← tap navigates; no onLongClick
    ) {
        when {
            item.metadata == null -> ShimmerRow()
            item.metadata.fetchStatus == FetchStatus.FAILED -> FallbackRow(
                item, onCopy, onOpen, onDelete, onRetryFetch
            )
            item.metadata.thumbnailUrl != null -> LayoutA(item, onCopy, onOpen, onDelete)
            else -> LayoutC(item, onCopy, onOpen, onDelete)
        }
    }
}
```

**1b.** Update the preview at the bottom of `HistoryItem.kt` to pass the new parameter:

```kotlin
@Preview(showBackground = true, name = "HistoryItem", widthDp = 380)
@Composable
private fun HistoryItemPreview(
    @PreviewParameter(HistoryItemPreviewProvider::class)
    item: ShareRecordWithMetadata,
) {
    CleanShareTheme {
        HistoryItem(
            item = item,
            onNavigate = {},
            onRetryFetch = { _, _ -> },
            onDelete = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
```

- [ ] **Step 2: Update HistoryScreen — add `onNavigateToDetail` and pass it through**

Replace the full content of `HistoryScreen.kt`:

```kotlin
package com.maroney.cleanshare.ui

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateToDetail: (id: Long) -> Unit,
) {
    val history by viewModel.history.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Clean Share") })
        },
    ) { innerPadding ->
        if (history.isEmpty()) {
            EmptyState(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = Spacing.sm),
            ) {
                items(history, key = { it.record.id }) { item ->
                    HistoryItem(
                        item = item,
                        onNavigate = { onNavigateToDetail(item.record.id) },
                        onRetryFetch = { id, url -> viewModel.retryFetch(id, url) },
                        onDelete = { viewModel.deleteItem(item.record.id) },
                    )
                    if (item != history.last()) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                    }
                }
            }
        }
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

- [ ] **Step 3: Build**

```
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/maroney/cleanshare/ui/HistoryItem.kt \
        app/src/main/java/com/maroney/cleanshare/ui/HistoryScreen.kt
git commit -m "feat: tap row navigates to detail, remove long-tap"
```

---

### Task 7: Full test run and final commit

- [ ] **Step 1: Run unit tests**

```
./gradlew test
```
Expected: BUILD SUCCESSFUL, all unit tests pass.

- [ ] **Step 2: Run instrumented tests**

```
./gradlew connectedDebugAndroidTest
```
Expected: BUILD SUCCESSFUL, all instrumented tests pass (ExampleInstrumentedTest, LinkMetadataDaoTest, ShareDaoNotesTest).

- [ ] **Step 3: Manually verify the feature on device**

Install and launch the app:
```
./gradlew installDebug
```

Check these flows:
- Tap a row → detail screen opens
- Long-press a row → nothing happens (no clipboard toast)
- Detail screen shows cleaned URL and original URL (when different), description, notes field
- Type in notes → navigate away → return → notes are still there
- "Open Link" CTA launches the browser
- "Copy Link" CTA gives haptic and copies to clipboard
- "Delete" CTA removes the item and returns to the list
- "Retry Metadata Fetch" appears only for items with failed metadata
- System back button returns to list from detail

- [ ] **Step 4: Commit (pre-commit hook will run all tests)**

```bash
git add -A
git commit -m "feat: detail screen with notes, URL comparison, and action CTAs"
```
