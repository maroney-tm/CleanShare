# CleanShare Android Sync Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire Android's existing share history into the Go sync server — pushing writes immediately, pulling on resume, and streaming SSE changes while foregrounded. The app must remain fully functional when no server is configured.

**Architecture:** A new `sync/` package in the `app` module owns discovery (`NsdDiscoveryHelper`), REST/SSE communication (`CleanShareSyncClient`, `SseListener`), and orchestration (`SyncManager`). `ShareRepository` gets an optional `SyncManager` and fires push calls after every local Room write. Full sync on resume handles missed remote changes; SSE handles live updates while the app is open. Deletions propagate via SSE only (no delete step in full sync — safe for LAN personal use where the server is always available).

**Tech Stack:** OkHttp (existing), `org.json` (built-in Android — no new dep), Android NsdManager, AndroidX DataStore Preferences, Compose Material3 icons (existing).

**Run tests after every commit:**
- Unit: `./gradlew test`
- Instrumented: `./gradlew connectedDebugAndroidTest`

---

## File map

### New files
| Path | Responsibility |
|---|---|
| `data/src/main/java/com/maroney/cleanshare/data/ShareSource.kt` | `ShareSource` enum |
| `app/src/main/java/com/maroney/cleanshare/sync/ServerConfig.kt` | Config data class + DataStore repo |
| `app/src/main/java/com/maroney/cleanshare/sync/NsdDiscoveryHelper.kt` | Wraps NsdManager, returns first discovered host:port |
| `app/src/main/java/com/maroney/cleanshare/sync/CleanShareSyncClient.kt` | OkHttp REST calls + `SyncRecord`/`SyncLinkMetadata` types |
| `app/src/main/java/com/maroney/cleanshare/sync/SseListener.kt` | SSE stream parser |
| `app/src/main/java/com/maroney/cleanshare/sync/SyncManager.kt` | Orchestrates discovery, push, pull, SSE |
| `app/src/main/java/com/maroney/cleanshare/ui/SyncSettingsScreen.kt` | Compose settings UI |
| `app/src/main/java/com/maroney/cleanshare/ui/SyncSettingsViewModel.kt` | ViewModel for settings screen |
| `data/src/androidTest/java/com/maroney/cleanshare/ShareRecordMigrationTest.kt` | Room v3→v4 migration test |
| `app/src/test/java/com/maroney/cleanshare/sync/CleanShareSyncClientTest.kt` | Unit tests (MockWebServer) |
| `app/src/test/java/com/maroney/cleanshare/sync/SseListenerTest.kt` | Unit tests (MockWebServer) |

### Modified files
| Path | Change |
|---|---|
| `data/src/main/java/com/maroney/cleanshare/data/ShareRecord.kt` | Add `syncId`, `updatedAt`, `source` |
| `data/src/main/java/com/maroney/cleanshare/data/Converters.kt` | Add `ShareSource` converters |
| `data/src/main/java/com/maroney/cleanshare/data/ShareDao.kt` | Add `updateNotesAndTimestamp`, `getBySyncId`, `deleteBySyncId`, `getSyncIdById`, `getAllOnce` |
| `data/src/main/java/com/maroney/cleanshare/data/ShareDatabase.kt` | Version 4, `MIGRATION_3_4` |
| `data/src/main/java/com/maroney/cleanshare/data/WorkScheduler.kt` | Add `syncId` param to `scheduleFetch` |
| `data/src/main/java/com/maroney/cleanshare/data/ShareRepository.kt` | Add `SyncManager?`, scope, push calls, use `updateNotesAndTimestamp` |
| `app/src/main/java/com/maroney/cleanshare/data/metadata/MetadataWorkScheduler.kt` | Pass `syncId` through to worker input data |
| `app/src/main/java/com/maroney/cleanshare/data/metadata/FetchMetadataWorker.kt` | Push metadata to server after local upsert |
| `app/src/main/java/com/maroney/cleanshare/data/metadata/AppWorkerFactory.kt` | Accept `CleanShareSyncClient?` |
| `app/src/main/java/com/maroney/cleanshare/CleanShareApplication.kt` | Wire up `SyncManager`, pass to repository + factory |
| `app/src/main/java/com/maroney/cleanshare/ui/HistoryViewModel.kt` | Add `onResume`, `onStart`, `onStop` for sync |
| `app/src/main/java/com/maroney/cleanshare/ui/HistoryScreen.kt` | Gear icon in TopAppBar |
| `app/src/main/java/com/maroney/cleanshare/MainActivity.kt` | Add `SyncSettingsRoute` to nav graph |
| `gradle/libs.versions.toml` | Add `datastore` version + library alias |
| `app/build.gradle.kts` | Add DataStore dependency |
| `app/src/main/AndroidManifest.xml` | Add `ACCESS_NETWORK_STATE` permission |

---

### Task 1: `ShareSource` enum + `Converters` update

**Files:**
- Create: `data/src/main/java/com/maroney/cleanshare/data/ShareSource.kt`
- Modify: `data/src/main/java/com/maroney/cleanshare/data/Converters.kt`

- [ ] **Step 1: Write the failing test**

Write `data/src/androidTest/java/com/maroney/cleanshare/ShareSourceConverterTest.kt`:
```kotlin
package com.maroney.cleanshare

import com.maroney.cleanshare.data.Converters
import com.maroney.cleanshare.data.ShareSource
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ShareSourceConverterTest {
    private val converters = Converters()

    @Test
    fun roundTrip_mobile() {
        val stored = converters.fromShareSource(ShareSource.MOBILE)
        val restored = converters.toShareSource(stored)
        assertEquals(ShareSource.MOBILE, restored)
    }

    @Test
    fun roundTrip_desktop() {
        val stored = converters.fromShareSource(ShareSource.DESKTOP)
        val restored = converters.toShareSource(stored)
        assertEquals(ShareSource.DESKTOP, restored)
    }

    @Test
    fun unknownValueFallsBackToMobile() {
        assertEquals(ShareSource.MOBILE, converters.toShareSource("UNKNOWN_FUTURE_VALUE"))
    }
}
```

- [ ] **Step 2: Run — expect compile failure (ShareSource not yet defined)**

```bash
./gradlew connectedDebugAndroidTest 2>&1 | grep -E "error:|FAILED"
```

- [ ] **Step 3: Create `ShareSource.kt`**

Write `data/src/main/java/com/maroney/cleanshare/data/ShareSource.kt`:
```kotlin
package com.maroney.cleanshare.data

enum class ShareSource { MOBILE, DESKTOP }
```

- [ ] **Step 4: Add converters to `Converters.kt`**

Edit `data/src/main/java/com/maroney/cleanshare/data/Converters.kt` — replace entire file:
```kotlin
package com.maroney.cleanshare.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun fromContentType(v: ContentType): String = v.name
    @TypeConverter fun toContentType(v: String): ContentType =
        ContentType.entries.firstOrNull { it.name == v } ?: ContentType.UNKNOWN

    @TypeConverter fun fromFetchStatus(v: FetchStatus): String = v.name
    @TypeConverter fun toFetchStatus(v: String): FetchStatus =
        FetchStatus.entries.firstOrNull { it.name == v } ?: FetchStatus.FAILED

    @TypeConverter fun fromShareSource(v: ShareSource): String = v.name
    @TypeConverter fun toShareSource(v: String): ShareSource =
        ShareSource.entries.firstOrNull { it.name == v } ?: ShareSource.MOBILE
}
```

- [ ] **Step 5: Run tests — expect pass**

```bash
./gradlew connectedDebugAndroidTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add data/src/main/java/com/maroney/cleanshare/data/ShareSource.kt \
        data/src/main/java/com/maroney/cleanshare/data/Converters.kt \
        data/src/androidTest/java/com/maroney/cleanshare/ShareSourceConverterTest.kt
git commit -m "feat: add ShareSource enum and Room type converter"
```

---

### Task 2: `ShareRecord` — add `syncId`, `updatedAt`, `source`

**Files:**
- Modify: `data/src/main/java/com/maroney/cleanshare/data/ShareRecord.kt`

- [ ] **Step 1: Update `ShareRecord.kt`**

Replace the full file:
```kotlin
package com.maroney.cleanshare.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "share_history")
data class ShareRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalText: String,
    val cleanedText: String,
    val sharedAt: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: ShareSource = ShareSource.MOBILE,
)
```

- [ ] **Step 2: Build — verify no compile errors**

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL` (Room will flag the version mismatch at runtime, not compile time).

- [ ] **Step 3: Commit**

```bash
git add data/src/main/java/com/maroney/cleanshare/data/ShareRecord.kt
git commit -m "feat: add syncId, updatedAt, source to ShareRecord"
```

---

### Task 3: `ShareDao` — new queries

**Files:**
- Modify: `data/src/main/java/com/maroney/cleanshare/data/ShareDao.kt`

- [ ] **Step 1: Write the failing tests**

Write `data/src/androidTest/java/com/maroney/cleanshare/ShareDaoSyncTest.kt`:
```kotlin
package com.maroney.cleanshare

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maroney.cleanshare.data.ShareDatabase
import com.maroney.cleanshare.data.ShareRecord
import com.maroney.cleanshare.data.ShareSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareDaoSyncTest {

    private lateinit var db: ShareDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, ShareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertRecord(syncId: String = "uuid-1"): Long =
        db.shareDao().insert(ShareRecord(
            originalText = "https://x.com?utm=1",
            cleanedText = "https://x.com",
            sharedAt = 1000L,
            updatedAt = 1000L,
            syncId = syncId,
            source = ShareSource.MOBILE,
        ))

    @Test
    fun getBySyncId_findsRecord() = runTest {
        insertRecord("my-uuid")
        val found = db.shareDao().getBySyncId("my-uuid")
        assertNotNull(found)
        assertEquals("my-uuid", found!!.syncId)
    }

    @Test
    fun getBySyncId_returnsNullWhenMissing() = runTest {
        val found = db.shareDao().getBySyncId("no-such-id")
        assertNull(found)
    }

    @Test
    fun deleteBySyncId_removesRecord() = runTest {
        insertRecord("to-delete")
        db.shareDao().deleteBySyncId("to-delete")
        assertNull(db.shareDao().getBySyncId("to-delete"))
    }

    @Test
    fun getSyncIdById_returnsCorrectSyncId() = runTest {
        val id = insertRecord("known-uuid")
        val syncId = db.shareDao().getSyncIdById(id)
        assertEquals("known-uuid", syncId)
    }

    @Test
    fun updateNotesAndTimestamp_bumpsUpdatedAt() = runTest {
        val id = insertRecord()
        db.shareDao().updateNotesAndTimestamp(id, "hello", 9999L)
        val record = db.shareDao().getBySyncId("uuid-1")!!
        assertEquals("hello", record.notes)
        assertEquals(9999L, record.updatedAt)
    }

    @Test
    fun getAllOnce_returnsAllRecords() = runTest {
        insertRecord("a")
        insertRecord("b")
        val all = db.shareDao().getAllOnce()
        assertEquals(2, all.size)
    }
}
```

- [ ] **Step 2: Run — expect failure (methods not yet defined)**

```bash
./gradlew connectedDebugAndroidTest 2>&1 | grep -E "error:|FAILED"
```

- [ ] **Step 3: Add new queries to `ShareDao.kt`**

Replace the full file:
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

    // Original — kept for existing tests.
    @Query("UPDATE share_history SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String?)

    // New — bumps updatedAt for LWW tracking.
    @Query("UPDATE share_history SET notes = :notes, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateNotesAndTimestamp(id: Long, notes: String?, updatedAt: Long)

    @Query("DELETE FROM share_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM share_history")
    suspend fun deleteAll()

    // Sync queries
    @Query("SELECT * FROM share_history WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): ShareRecord?

    @Query("DELETE FROM share_history WHERE sync_id = :syncId")
    suspend fun deleteBySyncId(syncId: String)

    @Query("SELECT sync_id FROM share_history WHERE id = :id")
    suspend fun getSyncIdById(id: Long): String?

    @Query("SELECT * FROM share_history")
    suspend fun getAllOnce(): List<ShareRecord>
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
./gradlew connectedDebugAndroidTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add data/src/main/java/com/maroney/cleanshare/data/ShareDao.kt \
        data/src/androidTest/java/com/maroney/cleanshare/ShareDaoSyncTest.kt
git commit -m "feat: add sync DAO queries to ShareDao"
```

---

### Task 4: Room migration v3 → v4

**Files:**
- Modify: `data/src/main/java/com/maroney/cleanshare/data/ShareDatabase.kt`
- Create: `data/src/androidTest/java/com/maroney/cleanshare/ShareRecordMigrationTest.kt`

- [ ] **Step 1: Write the failing migration test**

Write `data/src/androidTest/java/com/maroney/cleanshare/ShareRecordMigrationTest.kt`:
```kotlin
package com.maroney.cleanshare

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maroney.cleanshare.data.ShareDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareRecordMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ShareDatabase::class.java,
    )

    @Test
    fun migrate3To4_backfillsSyncIdUpdatedAtAndSource() {
        val db = helper.createDatabase("migration_test", 3)
        db.execSQL(
            "INSERT INTO share_history (originalText, cleanedText, sharedAt, notes) VALUES ('orig', 'clean', 12345, NULL)"
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(
            "migration_test", 4, true, ShareDatabase.MIGRATION_3_4
        )

        val cursor = migrated.query("SELECT sync_id, updated_at, source FROM share_history")
        assertTrue("Expected one row", cursor.moveToFirst())

        val syncId = cursor.getString(cursor.getColumnIndexOrThrow("sync_id"))
        val updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
        val source = cursor.getString(cursor.getColumnIndexOrThrow("source"))

        assertTrue("sync_id should be non-empty UUID", syncId.isNotEmpty())
        assertEquals("updated_at should equal sharedAt", 12345L, updatedAt)
        assertEquals("source should default to MOBILE", "MOBILE", source)

        cursor.close()
    }
}
```

- [ ] **Step 2: Run — expect failure (MIGRATION_3_4 not yet defined)**

```bash
./gradlew connectedDebugAndroidTest 2>&1 | grep -E "error:|FAILED"
```

- [ ] **Step 3: Update `ShareDatabase.kt`**

Replace the full file:
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
    version = 4,
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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE share_history ADD COLUMN sync_id    TEXT    NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE share_history ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE share_history ADD COLUMN source     TEXT    NOT NULL DEFAULT 'MOBILE'")

                // Backfill sync_id with random UUIDs (SQLite randomblob approach)
                db.execSQL("""
                    UPDATE share_history
                    SET sync_id = lower(
                        hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-' ||
                        hex(randomblob(2)) || '-' || hex(randomblob(2)) || '-' ||
                        hex(randomblob(6))
                    )
                    WHERE sync_id = ''
                """.trimIndent())

                // Backfill updated_at from shared_at
                db.execSQL("UPDATE share_history SET updated_at = sharedAt WHERE updated_at = 0")
            }
        }

        fun getInstance(context: Context): ShareDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShareDatabase::class.java,
                    "share_history.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
```

- [ ] **Step 4: Run all tests — expect pass**

```bash
./gradlew test connectedDebugAndroidTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add data/src/main/java/com/maroney/cleanshare/data/ShareDatabase.kt \
        data/src/androidTest/java/com/maroney/cleanshare/ShareRecordMigrationTest.kt
git commit -m "feat: Room migration v3→v4 — add syncId, updatedAt, source columns"
```

---

### Task 5: DataStore dependency + `ServerConfig`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/maroney/cleanshare/sync/ServerConfig.kt`

- [ ] **Step 1: Add DataStore to version catalog**

In `gradle/libs.versions.toml`, add under `[versions]`:
```toml
datastore = "1.1.4"
```
And under `[libraries]`:
```toml
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

- [ ] **Step 2: Add DataStore to `app/build.gradle.kts`**

Inside the `dependencies { }` block, after `implementation(libs.okhttp)`:
```kotlin
implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 3: Write `ServerConfig.kt`**

Write `app/src/main/java/com/maroney/cleanshare/sync/ServerConfig.kt`:
```kotlin
package com.maroney.cleanshare.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ServerConfig(
    val manualHost: String? = null,
    val port: Int = 8765,
    val autoDiscover: Boolean = true,
    val resolvedHost: String? = null,
) {
    /** The host to actually connect to: manual override wins, else last mDNS result. */
    val effectiveHost: String?
        get() = manualHost ?: resolvedHost
}

private val Context.serverConfigDataStore by preferencesDataStore(name = "server_config")

class ServerConfigRepository(private val context: Context) {

    companion object {
        private val KEY_MANUAL_HOST   = stringPreferencesKey("manual_host")
        private val KEY_PORT          = intPreferencesKey("port")
        private val KEY_AUTO_DISCOVER = booleanPreferencesKey("auto_discover")
        private val KEY_RESOLVED_HOST = stringPreferencesKey("resolved_host")
    }

    val config: Flow<ServerConfig> = context.serverConfigDataStore.data.map { prefs ->
        ServerConfig(
            manualHost    = prefs[KEY_MANUAL_HOST],
            port          = prefs[KEY_PORT] ?: 8765,
            autoDiscover  = prefs[KEY_AUTO_DISCOVER] ?: true,
            resolvedHost  = prefs[KEY_RESOLVED_HOST],
        )
    }

    suspend fun setManualHost(host: String?) {
        context.serverConfigDataStore.edit { prefs ->
            if (host == null) prefs.remove(KEY_MANUAL_HOST) else prefs[KEY_MANUAL_HOST] = host
        }
    }

    suspend fun setPort(port: Int) {
        context.serverConfigDataStore.edit { it[KEY_PORT] = port }
    }

    suspend fun setAutoDiscover(enabled: Boolean) {
        context.serverConfigDataStore.edit { it[KEY_AUTO_DISCOVER] = enabled }
    }

    suspend fun setResolvedHost(host: String?) {
        context.serverConfigDataStore.edit { prefs ->
            if (host == null) prefs.remove(KEY_RESOLVED_HOST)
            else prefs[KEY_RESOLVED_HOST] = host
        }
    }
}
```

- [ ] **Step 4: Build — verify compile**

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/main/java/com/maroney/cleanshare/sync/ServerConfig.kt
git commit -m "feat: add DataStore-backed ServerConfigRepository"
```

---

### Task 6: `NsdDiscoveryHelper`

**Files:**
- Create: `app/src/main/java/com/maroney/cleanshare/sync/NsdDiscoveryHelper.kt`

- [ ] **Step 1: Write `NsdDiscoveryHelper.kt`**

Write `app/src/main/java/com/maroney/cleanshare/sync/NsdDiscoveryHelper.kt`:
```kotlin
package com.maroney.cleanshare.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val SERVICE_TYPE = "_cleanshare._tcp"

class NsdDiscoveryHelper(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    /**
     * Discovers the first _cleanshare._tcp service on the LAN.
     * Returns (host, port) or null if none found within [timeoutMs].
     */
    suspend fun discover(timeoutMs: Long = 5_000L): Pair<String, Int>? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Pair<String, Int>?> { cont ->
                val discoveryListener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {}
                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    override fun onServiceLost(service: NsdServiceInfo) {}
                    override fun onServiceFound(service: NsdServiceInfo) {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(s: NsdServiceInfo, errorCode: Int) {
                                // Ignore — keep waiting for other services
                            }
                            override fun onServiceResolved(s: NsdServiceInfo) {
                                val host = s.host?.hostAddress ?: return
                                if (cont.isActive) cont.resume(host to s.port)
                            }
                        })
                    }
                }

                nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

                cont.invokeOnCancellation {
                    try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
                }
            }
        }
}
```

- [ ] **Step 2: Build — verify compile**

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/maroney/cleanshare/sync/NsdDiscoveryHelper.kt
git commit -m "feat: add NsdDiscoveryHelper for mDNS service discovery"
```

---

### Task 7: `CleanShareSyncClient`

**Files:**
- Create: `app/src/main/java/com/maroney/cleanshare/sync/CleanShareSyncClient.kt`
- Create: `app/src/test/java/com/maroney/cleanshare/sync/CleanShareSyncClientTest.kt`

- [ ] **Step 1: Write the failing tests**

Write `app/src/test/java/com/maroney/cleanshare/sync/CleanShareSyncClientTest.kt`:
```kotlin
package com.maroney.cleanshare.sync

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CleanShareSyncClientTest {

    private val server = MockWebServer()
    private lateinit var client: CleanShareSyncClient

    @Before
    fun setUp() {
        server.start()
        client = CleanShareSyncClient(OkHttpClient())
        client.configure(server.hostName, server.port)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `health returns true on 200`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertTrue(client.health())
    }

    @Test
    fun `health returns false on 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertFalse(client.health())
    }

    @Test
    fun `health returns false when not configured`() = runTest {
        val unconfigured = CleanShareSyncClient(OkHttpClient())
        assertFalse(unconfigured.health())
    }

    @Test
    fun `getAllRecords parses list`() = runTest {
        val body = """[
            {"syncId":"u1","originalText":"o","cleanedText":"c","sharedAt":1000,"updatedAt":1000,
             "notes":null,"source":"MOBILE","linkMetadata":null}
        ]"""
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))
        val records = client.getAllRecords()
        assertEquals(1, records.size)
        assertEquals("u1", records[0].syncId)
        assertNull(records[0].notes)
        assertNull(records[0].linkMetadata)
    }

    @Test
    fun `getAllRecords returns empty on server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(client.getAllRecords().isEmpty())
    }

    @Test
    fun `postRecord sends correct body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"syncId":"u1","originalText":"o","cleanedText":"c","sharedAt":1000,"updatedAt":1000,"notes":null,"source":"MOBILE","linkMetadata":null}"""
        ))
        val record = SyncRecord("u1", "o", "c", 1000L, 1000L, null, "MOBILE", null)
        assertTrue(client.postRecord(record))

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/records", req.path)
        val body = JSONObject(req.body.readUtf8())
        assertEquals("u1", body.getString("syncId"))
        assertEquals("MOBILE", body.getString("source"))
    }

    @Test
    fun `patchRecord sends notes and updatedAt`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"syncId":"u1","originalText":"o","cleanedText":"c","sharedAt":1000,"updatedAt":2000,"notes":"hi","source":"MOBILE","linkMetadata":null}"""
        ))
        assertTrue(client.patchRecord("u1", "hi", 2000L))

        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertEquals("/records/u1", req.path)
        val body = JSONObject(req.body.readUtf8())
        assertEquals("hi", body.getString("notes"))
        assertEquals(2000L, body.getLong("updatedAt"))
    }

    @Test
    fun `deleteRecord sends DELETE`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(client.deleteRecord("u1"))
        assertEquals("DELETE", server.takeRequest().method)
        assertEquals("/records/u1", server.takeRequest()?.path ?: "/records/u1")
    }

    @Test
    fun `putMetadata sends correct body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"syncId":"u1","originalText":"o","cleanedText":"c","sharedAt":1000,"updatedAt":1000,"notes":null,"source":"MOBILE","linkMetadata":{"title":"T","thumbnailUrl":null,"description":null,"articleSnippet":null,"contentType":"ARTICLE","fetchStatus":"SUCCESS"}}"""
        ))
        val meta = SyncLinkMetadata(
            title = "T", thumbnailUrl = null, description = null,
            articleSnippet = null, contentType = "ARTICLE", fetchStatus = "SUCCESS"
        )
        assertTrue(client.putMetadata("u1", meta))

        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/records/u1/metadata", req.path)
        val body = JSONObject(req.body.readUtf8())
        assertEquals("ARTICLE", body.getString("contentType"))
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

```bash
./gradlew test 2>&1 | grep -E "error:|FAILED"
```

- [ ] **Step 3: Write `CleanShareSyncClient.kt`**

Write `app/src/main/java/com/maroney/cleanshare/sync/CleanShareSyncClient.kt`:
```kotlin
package com.maroney.cleanshare.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

// ---- Sync-layer data types (separate from Room entities) ----

data class SyncRecord(
    val syncId: String,
    val originalText: String,
    val cleanedText: String,
    val sharedAt: Long,
    val updatedAt: Long,
    val notes: String?,
    val source: String,
    val linkMetadata: SyncLinkMetadata?,
)

data class SyncLinkMetadata(
    val title: String?,
    val thumbnailUrl: String?,
    val description: String?,
    val articleSnippet: String?,
    val contentType: String,
    val fetchStatus: String,
)

// ---- REST client ----

class CleanShareSyncClient(private val okHttpClient: OkHttpClient) {

    @Volatile private var baseUrl: String? = null

    fun configure(host: String, port: Int) { baseUrl = "http://$host:$port" }
    fun clear() { baseUrl = null }
    fun isConfigured(): Boolean = baseUrl != null

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        val url = baseUrl ?: return@withContext false
        try {
            okHttpClient.newCall(Request.Builder().url("$url/health").build())
                .execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }

    suspend fun getAllRecords(): List<SyncRecord> = withContext(Dispatchers.IO) {
        val url = baseUrl ?: return@withContext emptyList()
        try {
            okHttpClient.newCall(Request.Builder().url("$url/records").build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    parseRecordList(resp.body?.string() ?: "[]")
                }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun postRecord(record: SyncRecord): Boolean = withContext(Dispatchers.IO) {
        val url = baseUrl ?: return@withContext false
        try {
            val body = buildRecordJson(record).toRequestBody(JSON_MT)
            okHttpClient.newCall(Request.Builder().url("$url/records").post(body).build())
                .execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }

    suspend fun patchRecord(syncId: String, notes: String?, updatedAt: Long): Boolean =
        withContext(Dispatchers.IO) {
            val url = baseUrl ?: return@withContext false
            try {
                val json = JSONObject().apply {
                    if (notes != null) put("notes", notes) else put("notes", JSONObject.NULL)
                    put("updatedAt", updatedAt)
                }.toString().toRequestBody(JSON_MT)
                okHttpClient.newCall(
                    Request.Builder().url("$url/records/$syncId").patch(json).build()
                ).execute().use { it.isSuccessful }
            } catch (_: Exception) { false }
        }

    suspend fun deleteRecord(syncId: String): Boolean = withContext(Dispatchers.IO) {
        val url = baseUrl ?: return@withContext false
        try {
            okHttpClient.newCall(
                Request.Builder().url("$url/records/$syncId").delete().build()
            ).execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }

    suspend fun putMetadata(syncId: String, meta: SyncLinkMetadata): Boolean =
        withContext(Dispatchers.IO) {
            val url = baseUrl ?: return@withContext false
            try {
                val json = buildMetadataJson(meta).toRequestBody(JSON_MT)
                okHttpClient.newCall(
                    Request.Builder().url("$url/records/$syncId/metadata").put(json).build()
                ).execute().use { it.isSuccessful }
            } catch (_: Exception) { false }
        }

    // ---- JSON helpers ----

    private fun parseRecordList(json: String): List<SyncRecord> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { parseRecord(arr.getJSONObject(it)) }
    }

    private fun parseRecord(obj: JSONObject): SyncRecord {
        val meta = if (!obj.isNull("linkMetadata"))
            parseMetadata(obj.getJSONObject("linkMetadata")) else null
        return SyncRecord(
            syncId       = obj.getString("syncId"),
            originalText = obj.getString("originalText"),
            cleanedText  = obj.getString("cleanedText"),
            sharedAt     = obj.getLong("sharedAt"),
            updatedAt    = obj.getLong("updatedAt"),
            notes        = if (obj.isNull("notes")) null else obj.getString("notes"),
            source       = obj.getString("source"),
            linkMetadata = meta,
        )
    }

    private fun parseMetadata(obj: JSONObject) = SyncLinkMetadata(
        title          = obj.nullableString("title"),
        thumbnailUrl   = obj.nullableString("thumbnailUrl"),
        description    = obj.nullableString("description"),
        articleSnippet = obj.nullableString("articleSnippet"),
        contentType    = obj.getString("contentType"),
        fetchStatus    = obj.getString("fetchStatus"),
    )

    private fun buildRecordJson(r: SyncRecord) = JSONObject().apply {
        put("syncId",       r.syncId)
        put("originalText", r.originalText)
        put("cleanedText",  r.cleanedText)
        put("sharedAt",     r.sharedAt)
        put("updatedAt",    r.updatedAt)
        if (r.notes != null) put("notes", r.notes) else put("notes", JSONObject.NULL)
        put("source",       r.source)
    }.toString()

    private fun buildMetadataJson(m: SyncLinkMetadata) = JSONObject().apply {
        putNullable("title",          m.title)
        putNullable("thumbnailUrl",   m.thumbnailUrl)
        putNullable("description",    m.description)
        putNullable("articleSnippet", m.articleSnippet)
        put("contentType",  m.contentType)
        put("fetchStatus",  m.fetchStatus)
    }.toString()

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (value != null) put(key, value) else put(key, JSONObject.NULL)
    }

    companion object {
        private val JSON_MT = "application/json".toMediaType()
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
./gradlew test 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/maroney/cleanshare/sync/CleanShareSyncClient.kt \
        app/src/test/java/com/maroney/cleanshare/sync/CleanShareSyncClientTest.kt
git commit -m "feat: add CleanShareSyncClient with REST calls and JSON parsing"
```

---

### Task 8: `SseListener`

**Files:**
- Create: `app/src/main/java/com/maroney/cleanshare/sync/SseListener.kt`
- Create: `app/src/test/java/com/maroney/cleanshare/sync/SseListenerTest.kt`

- [ ] **Step 1: Write the failing test**

Write `app/src/test/java/com/maroney/cleanshare/sync/SseListenerTest.kt`:
```kotlin
package com.maroney.cleanshare.sync

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SseListenerTest {

    private val server = MockWebServer()

    @Before fun setUp() = server.start()
    @After  fun tearDown() = server.shutdown()

    private fun sseBody(vararg events: Pair<String, String>): MockResponse {
        val buf = Buffer()
        for ((type, data) in events) {
            buf.writeUtf8("event: $type\ndata: $data\n\n")
        }
        return MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "text/event-stream")
            .setBody(buf)
    }

    @Test
    fun `parses record_created event`() = runTest {
        server.enqueue(sseBody("record_created" to """{"syncId":"u1"}"""))

        val received = mutableListOf<Pair<String, String>>()
        val listener = SseListener(OkHttpClient()) { type, data -> received.add(type to data) }
        val baseUrl = "http://${server.hostName}:${server.port}"

        listener.start(baseUrl, this)
        delay(300)
        listener.stop()

        assertEquals(1, received.size)
        assertEquals("record_created", received[0].first)
        assertTrue(received[0].second.contains("u1"))
    }

    @Test
    fun `ignores comment lines`() = runTest {
        val buf = Buffer().apply {
            writeUtf8(": ping\n\n")
            writeUtf8("event: record_deleted\ndata: {\"syncId\":\"u2\"}\n\n")
        }
        server.enqueue(
            MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "text/event-stream")
                .setBody(buf)
        )

        val received = mutableListOf<String>()
        val listener = SseListener(OkHttpClient()) { type, _ -> received.add(type) }
        listener.start("http://${server.hostName}:${server.port}", this)
        delay(300)
        listener.stop()

        assertEquals(listOf("record_deleted"), received)
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

```bash
./gradlew test 2>&1 | grep -E "error:|FAILED"
```

- [ ] **Step 3: Write `SseListener.kt`**

Write `app/src/main/java/com/maroney/cleanshare/sync/SseListener.kt`:
```kotlin
package com.maroney.cleanshare.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Opens a persistent GET /events connection and delivers (eventType, dataJson)
 * pairs to [onEvent]. Call [start] to begin streaming; [stop] to cancel.
 *
 * SSE format expected:
 *   event: record_created
 *   data: {...}
 *   (blank line)
 */
class SseListener(
    private val okHttpClient: OkHttpClient,
    private val onEvent: suspend (type: String, data: String) -> Unit,
) {
    private var job: Job? = null

    fun start(baseUrl: String, scope: CoroutineScope) {
        job = scope.launch(Dispatchers.IO) {
            val request = Request.Builder().url("$baseUrl/events").build()
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val source = response.body?.source() ?: return@launch
                    var eventType = ""
                    var dataLine  = ""
                    while (isActive && !source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.startsWith("event: ") -> eventType = line.removePrefix("event: ")
                            line.startsWith("data: ")  -> dataLine  = line.removePrefix("data: ")
                            line.isEmpty() && eventType.isNotEmpty() -> {
                                onEvent(eventType, dataLine)
                                eventType = ""
                                dataLine  = ""
                            }
                            // Lines starting with ':' are SSE comments — ignore.
                        }
                    }
                }
            } catch (_: Exception) {
                // Connection dropped — caller (SyncManager) can restart on next resume.
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
./gradlew test 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/maroney/cleanshare/sync/SseListener.kt \
        app/src/test/java/com/maroney/cleanshare/sync/SseListenerTest.kt
git commit -m "feat: add SseListener for real-time server events"
```

---

### Task 9: `SyncManager`

**Files:**
- Create: `app/src/main/java/com/maroney/cleanshare/sync/SyncManager.kt`

- [ ] **Step 1: Write `SyncManager.kt`**

Write `app/src/main/java/com/maroney/cleanshare/sync/SyncManager.kt`:
```kotlin
package com.maroney.cleanshare.sync

import android.content.Context
import com.maroney.cleanshare.data.ContentType
import com.maroney.cleanshare.data.FetchStatus
import com.maroney.cleanshare.data.LinkMetadata
import com.maroney.cleanshare.data.LinkMetadataDao
import com.maroney.cleanshare.data.ShareDao
import com.maroney.cleanshare.data.ShareRecord
import com.maroney.cleanshare.data.ShareSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

sealed class ConnectionStatus {
    data object Disconnected : ConnectionStatus()
    data object Searching    : ConnectionStatus()
    data class  Connected(val host: String, val port: Int) : ConnectionStatus()
}

class SyncManager(
    private val context: Context,
    private val syncClient: CleanShareSyncClient,
    private val configRepo: ServerConfigRepository,
    private val shareDao: ShareDao,
    private val metadataDao: LinkMetadataDao,
) {
    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private var sseListener: SseListener? = null

    // ---- Discovery + connection ----

    /**
     * Resolves the server address (manual override → mDNS → give up),
     * verifies with /health, and configures [syncClient].
     * Returns true if a live server was found.
     */
    suspend fun resolveAndSync(): Boolean = withContext(Dispatchers.IO) {
        _status.value = ConnectionStatus.Searching
        val config = configRepo.config.first()
        val port = config.port

        val host: String? = when {
            config.manualHost != null -> config.manualHost
            config.autoDiscover -> {
                val discovered = NsdDiscoveryHelper(context).discover()
                if (discovered != null) {
                    configRepo.setResolvedHost(discovered.first)
                    discovered.first
                } else null
            }
            else -> config.resolvedHost
        }

        if (host == null) {
            _status.value = ConnectionStatus.Disconnected
            syncClient.clear()
            return@withContext false
        }

        syncClient.configure(host, port)

        if (!syncClient.health()) {
            // Cached host is stale — clear it and give up
            configRepo.setResolvedHost(null)
            syncClient.clear()
            _status.value = ConnectionStatus.Disconnected
            return@withContext false
        }

        _status.value = ConnectionStatus.Connected(host, port)
        fullSync()
        true
    }

    // ---- Full pull sync ----

    /**
     * Fetches all records from the server and upserts them locally (LWW).
     * NOTE: Deletions propagate via SSE only; this method never deletes local records.
     */
    suspend fun fullSync() = withContext(Dispatchers.IO) {
        val serverRecords = syncClient.getAllRecords()
        for (sr in serverRecords) {
            val local = shareDao.getBySyncId(sr.syncId)
            if (local == null) {
                // Record originated on another client (e.g. future TUI) — insert locally.
                val id = shareDao.insert(sr.toShareRecord())
                sr.linkMetadata?.let { metadataDao.upsert(it.toLinkMetadata(id)) }
            } else if (sr.updatedAt > local.updatedAt) {
                shareDao.updateNotesAndTimestamp(local.id, sr.notes, sr.updatedAt)
                sr.linkMetadata?.let { metadataDao.upsert(it.toLinkMetadata(local.id)) }
            }
        }
    }

    // ---- SSE ----

    fun startListening(scope: CoroutineScope) {
        if (!syncClient.isConfigured()) return
        val baseUrl = (_status.value as? ConnectionStatus.Connected)
            ?.let { "http://${it.host}:${it.port}" } ?: return

        sseListener = SseListener(buildOkHttp()) { type, data ->
            handleSseEvent(type, data)
        }
        sseListener?.start(baseUrl, scope)
    }

    fun stopListening() {
        sseListener?.stop()
        sseListener = null
    }

    private suspend fun handleSseEvent(type: String, data: String) = withContext(Dispatchers.IO) {
        try {
            val obj = org.json.JSONObject(data)
            when (type) {
                "record_created", "record_updated" -> {
                    val sr = parseSyncRecord(obj)
                    val local = shareDao.getBySyncId(sr.syncId)
                    if (local == null) {
                        val id = shareDao.insert(sr.toShareRecord())
                        sr.linkMetadata?.let { metadataDao.upsert(it.toLinkMetadata(id)) }
                    } else if (sr.updatedAt > local.updatedAt) {
                        shareDao.updateNotesAndTimestamp(local.id, sr.notes, sr.updatedAt)
                        sr.linkMetadata?.let { metadataDao.upsert(it.toLinkMetadata(local.id)) }
                    }
                }
                "record_metadata_updated" -> {
                    val sr = parseSyncRecord(obj)
                    val local = shareDao.getBySyncId(sr.syncId) ?: return@withContext
                    sr.linkMetadata?.let { metadataDao.upsert(it.toLinkMetadata(local.id)) }
                }
                "record_deleted" -> {
                    val syncId = obj.getString("syncId")
                    val local = shareDao.getBySyncId(syncId) ?: return@withContext
                    metadataDao.deleteByShareRecordId(local.id)
                    shareDao.deleteBySyncId(syncId)
                }
            }
        } catch (_: Exception) { /* malformed event — ignore */ }
    }

    // ---- Push helpers (fire-and-forget, called from ShareRepository) ----

    suspend fun pushInsert(record: ShareRecord) {
        syncClient.postRecord(record.toSyncRecord())
    }

    suspend fun pushNoteUpdate(syncId: String, notes: String?, updatedAt: Long) {
        syncClient.patchRecord(syncId, notes, updatedAt)
    }

    suspend fun pushDelete(syncId: String) {
        syncClient.deleteRecord(syncId)
    }

    suspend fun pushMetadata(syncId: String, meta: LinkMetadata) {
        syncClient.putMetadata(syncId, meta.toSyncLinkMetadata())
    }

    // ---- Conversion helpers ----

    private fun SyncRecord.toShareRecord() = ShareRecord(
        originalText = originalText,
        cleanedText  = cleanedText,
        sharedAt     = sharedAt,
        updatedAt    = updatedAt,
        notes        = notes,
        syncId       = syncId,
        source       = runCatching { ShareSource.valueOf(source) }.getOrDefault(ShareSource.MOBILE),
    )

    private fun ShareRecord.toSyncRecord() = SyncRecord(
        syncId       = syncId,
        originalText = originalText,
        cleanedText  = cleanedText,
        sharedAt     = sharedAt,
        updatedAt    = updatedAt,
        notes        = notes,
        source       = source.name,
        linkMetadata = null,
    )

    private fun SyncLinkMetadata.toLinkMetadata(shareRecordId: Long) = LinkMetadata(
        shareRecordId  = shareRecordId,
        title          = title,
        thumbnailUrl   = thumbnailUrl,
        description    = description,
        articleSnippet = articleSnippet,
        contentType    = runCatching { ContentType.valueOf(contentType) }.getOrDefault(ContentType.UNKNOWN),
        fetchStatus    = runCatching { FetchStatus.valueOf(fetchStatus) }.getOrDefault(FetchStatus.FAILED),
    )

    private fun LinkMetadata.toSyncLinkMetadata() = SyncLinkMetadata(
        title          = title,
        thumbnailUrl   = thumbnailUrl,
        description    = description,
        articleSnippet = articleSnippet,
        contentType    = contentType.name,
        fetchStatus    = fetchStatus.name,
    )

    private fun parseSyncRecord(obj: org.json.JSONObject): SyncRecord {
        val meta = if (!obj.isNull("linkMetadata")) {
            val m = obj.getJSONObject("linkMetadata")
            SyncLinkMetadata(
                title          = if (m.isNull("title")) null else m.getString("title"),
                thumbnailUrl   = if (m.isNull("thumbnailUrl")) null else m.getString("thumbnailUrl"),
                description    = if (m.isNull("description")) null else m.getString("description"),
                articleSnippet = if (m.isNull("articleSnippet")) null else m.getString("articleSnippet"),
                contentType    = m.getString("contentType"),
                fetchStatus    = m.getString("fetchStatus"),
            )
        } else null
        return SyncRecord(
            syncId       = obj.getString("syncId"),
            originalText = obj.getString("originalText"),
            cleanedText  = obj.getString("cleanedText"),
            sharedAt     = obj.getLong("sharedAt"),
            updatedAt    = obj.getLong("updatedAt"),
            notes        = if (obj.isNull("notes")) null else obj.getString("notes"),
            source       = obj.getString("source"),
            linkMetadata = meta,
        )
    }

    private fun buildOkHttp() = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS) // no read timeout for SSE
        .build()
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/maroney/cleanshare/sync/SyncManager.kt
git commit -m "feat: add SyncManager — discovery, pull, SSE, push helpers"
```

---

### Task 10: `WorkScheduler` + `ShareRepository` sync integration

**Files:**
- Modify: `data/src/main/java/com/maroney/cleanshare/data/WorkScheduler.kt`
- Modify: `app/src/main/java/com/maroney/cleanshare/data/metadata/MetadataWorkScheduler.kt`
- Modify: `data/src/main/java/com/maroney/cleanshare/data/ShareRepository.kt`

- [ ] **Step 1: Update `WorkScheduler.kt`**

Replace the full file:
```kotlin
package com.maroney.cleanshare.data

interface WorkScheduler {
    fun scheduleFetch(shareRecordId: Long, url: String, syncId: String)
}
```

- [ ] **Step 2: Update `MetadataWorkScheduler.kt`**

Replace the full file:
```kotlin
package com.maroney.cleanshare.data.metadata

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.maroney.cleanshare.data.ShareRecordWithMetadata
import com.maroney.cleanshare.data.WorkScheduler

class MetadataWorkScheduler(private val workManager: WorkManager) : WorkScheduler {

    override fun scheduleFetch(shareRecordId: Long, url: String, syncId: String) {
        enqueue(shareRecordId, url, syncId, ExistingWorkPolicy.KEEP)
    }

    fun schedulePendingFetches(records: List<ShareRecordWithMetadata>) {
        records
            .filter { it.metadata == null }
            .forEach { item ->
                val url = extractUrl(item.record.cleanedText) ?: return@forEach
                scheduleFetch(item.record.id, url, item.record.syncId)
            }
    }

    fun retryFetch(shareRecordId: Long, url: String, syncId: String) {
        enqueue(shareRecordId, url, syncId, ExistingWorkPolicy.REPLACE)
    }

    private fun enqueue(shareRecordId: Long, url: String, syncId: String, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<FetchMetadataWorker>()
            .setInputData(workDataOf(
                FetchMetadataWorker.KEY_SHARE_RECORD_ID to shareRecordId,
                FetchMetadataWorker.KEY_URL to url,
                FetchMetadataWorker.KEY_SYNC_ID to syncId,
            ))
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        workManager.enqueueUniqueWork("metadata_$shareRecordId", policy, request)
    }

    private fun extractUrl(text: String): String? =
        text.split("\\s+".toRegex())
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
}
```

- [ ] **Step 3: Update `ShareRepository.kt`**

Replace the full file:
```kotlin
package com.maroney.cleanshare.data

import com.maroney.cleanshare.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ShareRepository(
    private val shareDao: ShareDao,
    private val metadataDao: LinkMetadataDao,
    private val workScheduler: WorkScheduler,
    private val syncManager: SyncManager? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
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
        if (url != null) workScheduler.scheduleFetch(id, url, record.syncId)
        scope.launch { syncManager?.pushInsert(record) }
    }

    suspend fun updateNotes(id: Long, notes: String?) {
        val now = System.currentTimeMillis()
        shareDao.updateNotesAndTimestamp(id, notes, now)
        val syncId = shareDao.getSyncIdById(id) ?: return
        scope.launch { syncManager?.pushNoteUpdate(syncId, notes, now) }
    }

    suspend fun deleteById(id: Long) {
        val syncId = shareDao.getSyncIdById(id)
        metadataDao.deleteByShareRecordId(id)
        shareDao.deleteById(id)
        if (syncId != null) scope.launch { syncManager?.pushDelete(syncId) }
    }

    suspend fun deleteAll() {
        shareDao.deleteAll()
        metadataDao.deleteAll()
    }
}
```

- [ ] **Step 4: Fix `DetailViewModel` — `retryFetch` now needs `syncId`**

In `app/src/main/java/com/maroney/cleanshare/ui/DetailViewModel.kt`, update `retryMetadataFetch`:
```kotlin
fun retryMetadataFetch() {
    val record = _uiState.value?.record ?: return
    workScheduler.retryFetch(record.id, record.cleanedText, record.syncId)
}
```

- [ ] **Step 5: Fix `HistoryViewModel` — `retryFetch` signature**

In `app/src/main/java/com/maroney/cleanshare/ui/HistoryViewModel.kt`, update `retryFetch`:
```kotlin
fun retryFetch(shareRecordId: Long, url: String, syncId: String) {
    workScheduler.retryFetch(shareRecordId, url, syncId)
}
```

- [ ] **Step 6: Build**

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Run all tests**

```bash
./gradlew test connectedDebugAndroidTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add data/src/main/java/com/maroney/cleanshare/data/WorkScheduler.kt \
        data/src/main/java/com/maroney/cleanshare/data/ShareRepository.kt \
        app/src/main/java/com/maroney/cleanshare/data/metadata/MetadataWorkScheduler.kt \
        app/src/main/java/com/maroney/cleanshare/ui/DetailViewModel.kt \
        app/src/main/java/com/maroney/cleanshare/ui/HistoryViewModel.kt
git commit -m "feat: wire SyncManager push calls into ShareRepository"
```

---

### Task 11: `FetchMetadataWorker` + `AppWorkerFactory` — metadata push

**Files:**
- Modify: `app/src/main/java/com/maroney/cleanshare/data/metadata/FetchMetadataWorker.kt`
- Modify: `app/src/main/java/com/maroney/cleanshare/data/metadata/AppWorkerFactory.kt`

- [ ] **Step 1: Update `FetchMetadataWorker.kt`**

Replace the full file:
```kotlin
package com.maroney.cleanshare.data.metadata

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.maroney.cleanshare.data.ContentType
import com.maroney.cleanshare.data.FetchStatus
import com.maroney.cleanshare.data.LinkMetadata
import com.maroney.cleanshare.data.LinkMetadataDao
import com.maroney.cleanshare.sync.CleanShareSyncClient
import com.maroney.cleanshare.sync.SyncLinkMetadata
import com.maroney.cleanshare.widget.RecentSharesWidget

class FetchMetadataWorker(
    context: Context,
    params: WorkerParameters,
    private val fetcher: MetadataFetcher,
    private val dao: LinkMetadataDao,
    private val syncClient: CleanShareSyncClient?,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SHARE_RECORD_ID = "share_record_id"
        const val KEY_URL             = "url"
        const val KEY_SYNC_ID         = "sync_id"
    }

    override suspend fun doWork(): Result {
        val shareRecordId = inputData.getLong(KEY_SHARE_RECORD_ID, -1L)
        val url    = inputData.getString(KEY_URL)    ?: return Result.failure()
        val syncId = inputData.getString(KEY_SYNC_ID) ?: ""
        if (shareRecordId == -1L) return Result.failure()

        val fetched = fetcher.fetch(url)
        val metadata = fetched?.copy(shareRecordId = shareRecordId)
            ?: LinkMetadata(
                shareRecordId  = shareRecordId,
                title          = null,
                thumbnailUrl   = null,
                description    = null,
                articleSnippet = null,
                contentType    = ContentType.UNKNOWN,
                fetchStatus    = FetchStatus.FAILED,
            )
        dao.upsert(metadata)

        // Push metadata to sync server if we have a syncId and the fetch succeeded.
        if (syncId.isNotEmpty() && metadata.fetchStatus == FetchStatus.SUCCESS) {
            syncClient?.putMetadata(syncId, metadata.toSyncLinkMetadata())
        }

        RecentSharesWidget().updateAll(applicationContext)
        return Result.success()
    }

    private fun LinkMetadata.toSyncLinkMetadata() = SyncLinkMetadata(
        title          = title,
        thumbnailUrl   = thumbnailUrl,
        description    = description,
        articleSnippet = articleSnippet,
        contentType    = contentType.name,
        fetchStatus    = fetchStatus.name,
    )
}
```

- [ ] **Step 2: Update `AppWorkerFactory.kt`**

Replace the full file:
```kotlin
package com.maroney.cleanshare.data.metadata

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.maroney.cleanshare.data.LinkMetadataDao
import com.maroney.cleanshare.sync.CleanShareSyncClient

class AppWorkerFactory(
    private val fetcher: MetadataFetcher,
    private val dao: LinkMetadataDao,
    private val syncClient: CleanShareSyncClient?,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        FetchMetadataWorker::class.java.name ->
            FetchMetadataWorker(appContext, workerParameters, fetcher, dao, syncClient)
        else -> null
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run all tests**

```bash
./gradlew test connectedDebugAndroidTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/maroney/cleanshare/data/metadata/FetchMetadataWorker.kt \
        app/src/main/java/com/maroney/cleanshare/data/metadata/AppWorkerFactory.kt
git commit -m "feat: push link metadata to sync server after fetch"
```

---

### Task 12: Wire up `CleanShareApplication`

**Files:**
- Modify: `app/src/main/java/com/maroney/cleanshare/CleanShareApplication.kt`

- [ ] **Step 1: Update `CleanShareApplication.kt`**

Replace the full file:
```kotlin
package com.maroney.cleanshare

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.maroney.cleanshare.data.ShareDatabase
import com.maroney.cleanshare.data.ShareRepository
import com.maroney.cleanshare.data.metadata.AppWorkerFactory
import com.maroney.cleanshare.data.metadata.MetadataFetcher
import com.maroney.cleanshare.data.metadata.MetadataWorkScheduler
import com.maroney.cleanshare.sync.CleanShareSyncClient
import com.maroney.cleanshare.sync.ServerConfigRepository
import com.maroney.cleanshare.sync.SyncManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CleanShareApplication : Application(), Configuration.Provider {

    val database by lazy { ShareDatabase.getInstance(this) }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .callTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                        .build()
                )
            }
            .build()
    }

    val metadataFetcher by lazy { MetadataFetcher(okHttpClient) }

    val workScheduler by lazy {
        MetadataWorkScheduler(WorkManager.getInstance(this))
    }

    val serverConfigRepository by lazy { ServerConfigRepository(this) }

    val syncClient by lazy { CleanShareSyncClient(okHttpClient) }

    val syncManager by lazy {
        SyncManager(
            context     = this,
            syncClient  = syncClient,
            configRepo  = serverConfigRepository,
            shareDao    = database.shareDao(),
            metadataDao = database.linkMetadataDao(),
        )
    }

    val shareRepository by lazy {
        ShareRepository(
            shareDao      = database.shareDao(),
            metadataDao   = database.linkMetadataDao(),
            workScheduler = workScheduler,
            syncManager   = syncManager,
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(AppWorkerFactory(metadataFetcher, database.linkMetadataDao(), syncClient))
            .build()
}
```

- [ ] **Step 2: Build + run all tests**

```bash
./gradlew test connectedDebugAndroidTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/maroney/cleanshare/CleanShareApplication.kt
git commit -m "feat: wire SyncManager and SyncClient into Application"
```

---

### Task 13: `SyncSettingsViewModel` + `SyncSettingsScreen`

**Files:**
- Create: `app/src/main/java/com/maroney/cleanshare/ui/SyncSettingsViewModel.kt`
- Create: `app/src/main/java/com/maroney/cleanshare/ui/SyncSettingsScreen.kt`

- [ ] **Step 1: Write `SyncSettingsViewModel.kt`**

Write `app/src/main/java/com/maroney/cleanshare/ui/SyncSettingsViewModel.kt`:
```kotlin
package com.maroney.cleanshare.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.maroney.cleanshare.CleanShareApplication
import com.maroney.cleanshare.sync.ConnectionStatus
import com.maroney.cleanshare.sync.ServerConfig
import com.maroney.cleanshare.sync.ServerConfigRepository
import com.maroney.cleanshare.sync.SyncManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SyncSettingsViewModel(
    private val configRepo: ServerConfigRepository,
    private val syncManager: SyncManager,
) : ViewModel() {

    val config: StateFlow<ServerConfig> = configRepo.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerConfig())

    val connectionStatus: StateFlow<ConnectionStatus> = syncManager.connectionStatus

    fun setAutoDiscover(enabled: Boolean) {
        viewModelScope.launch { configRepo.setAutoDiscover(enabled) }
    }

    fun setManualHost(raw: String) {
        viewModelScope.launch {
            val cleaned = raw.trim().ifBlank { null }
            configRepo.setManualHost(cleaned)
        }
    }

    fun testConnection() {
        viewModelScope.launch { syncManager.resolveAndSync() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[APPLICATION_KEY] as CleanShareApplication
                return SyncSettingsViewModel(app.serverConfigRepository, app.syncManager) as T
            }
        }
    }
}
```

- [ ] **Step 2: Write `SyncSettingsScreen.kt`**

Write `app/src/main/java/com/maroney/cleanshare/ui/SyncSettingsScreen.kt`:
```kotlin
package com.maroney.cleanshare.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maroney.cleanshare.sync.ConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(onNavigateBack: () -> Unit) {
    val viewModel: SyncSettingsViewModel = viewModel(factory = SyncSettingsViewModel.Factory)
    val config by viewModel.config.collectAsStateWithLifecycle()
    val status by viewModel.connectionStatus.collectAsStateWithLifecycle()

    var draftHost by remember(config.manualHost) {
        mutableStateOf(config.manualHost ?: config.resolvedHost ?: "")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync") },
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
                .padding(innerPadding)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            // Connection status
            ConnectionStatusRow(status)

            Spacer(modifier = Modifier.height(Spacing.md))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Spacing.md))

            // Auto-discover toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Discover automatically",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = config.autoDiscover,
                    onCheckedChange = { viewModel.setAutoDiscover(it) },
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Manual host field (enabled only when auto-discover is off)
            Text(
                text = "Server address",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            OutlinedTextField(
                value = draftHost,
                onValueChange = { draftHost = it },
                enabled = !config.autoDiscover,
                placeholder = { Text("192.168.1.x:8765") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Button(
                onClick = {
                    viewModel.setManualHost(draftHost)
                    viewModel.testConnection()
                },
                enabled = !config.autoDiscover,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Test connection")
            }
        }
    }
}

@Composable
private fun ConnectionStatusRow(status: ConnectionStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val (dotColor, label) = when (status) {
            is ConnectionStatus.Connected ->
                Color(0xFF4CAF50) to "Connected  ${status.host}:${status.port}"
            is ConnectionStatus.Searching ->
                Color(0xFFFFC107) to "Searching…"
            is ConnectionStatus.Disconnected ->
                Color(0xFF9E9E9E) to "Not connected"
        }
        Icon(
            imageVector = Icons.Default.Circle,
            contentDescription = null,
            tint = dotColor,
            modifier = Modifier.size(IconSize.favicon / 2),
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/maroney/cleanshare/ui/SyncSettingsViewModel.kt \
        app/src/main/java/com/maroney/cleanshare/ui/SyncSettingsScreen.kt
git commit -m "feat: add SyncSettingsScreen and ViewModel"
```

---

### Task 14: `HistoryViewModel` sync lifecycle + `HistoryScreen` gear icon + navigation

**Files:**
- Modify: `app/src/main/java/com/maroney/cleanshare/ui/HistoryViewModel.kt`
- Modify: `app/src/main/java/com/maroney/cleanshare/ui/HistoryScreen.kt`
- Modify: `app/src/main/java/com/maroney/cleanshare/MainActivity.kt`

- [ ] **Step 1: Update `HistoryViewModel.kt`**

Replace the full file:
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
import com.maroney.cleanshare.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repository: ShareRepository,
    private val workScheduler: MetadataWorkScheduler,
    private val syncManager: SyncManager,
) : ViewModel() {

    val history: StateFlow<List<ShareRecordWithMetadata>> = repository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = repository.getAll().first()
            workScheduler.schedulePendingFetches(snapshot)
            syncManager.resolveAndSync()
        }
    }

    /** Called from HistoryScreen when the lifecycle moves to ON_START. */
    fun onStart() {
        syncManager.startListening(viewModelScope)
    }

    /** Called from HistoryScreen when the lifecycle moves to ON_STOP. */
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
                return HistoryViewModel(app.shareRepository, app.workScheduler, app.syncManager) as T
            }
        }
    }
}
```

- [ ] **Step 2: Update `HistoryScreen.kt`**

Replace the full file:
```kotlin
package com.maroney.cleanshare.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateToDetail: (id: Long) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val history by viewModel.history.collectAsStateWithLifecycle()

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

- [ ] **Step 3: Update `MainActivity.kt`** — add `SyncSettingsRoute`

Replace the full file:
```kotlin
package com.maroney.cleanshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.maroney.cleanshare.ui.DetailRoute
import com.maroney.cleanshare.ui.DetailScreen
import com.maroney.cleanshare.ui.HistoryRoute
import com.maroney.cleanshare.ui.HistoryScreen
import com.maroney.cleanshare.ui.HistoryViewModel
import com.maroney.cleanshare.ui.SyncSettingsRoute
import com.maroney.cleanshare.ui.SyncSettingsScreen
import com.maroney.cleanshare.ui.theme.CleanShareTheme
import com.maroney.cleanshare.widget.RecentSharesWidget

data object SyncSettingsRoute

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val detailId = intent?.getLongExtra(RecentSharesWidget.EXTRA_DETAIL_ID, -1L)?.takeIf { it >= 0L }

        setContent {
            CleanShareTheme {
                val backStack = remember {
                    mutableStateListOf(*initialBackStack(detailId).toTypedArray())
                }
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = { key ->
                        when (key) {
                            HistoryRoute -> NavEntry(key) {
                                val viewModel: HistoryViewModel =
                                    viewModel(factory = HistoryViewModel.Factory)
                                HistoryScreen(
                                    viewModel = viewModel,
                                    onNavigateToDetail = { id -> backStack.add(DetailRoute(id)) },
                                    onNavigateToSettings = { backStack.add(SyncSettingsRoute) },
                                )
                            }
                            is DetailRoute -> NavEntry(key) {
                                DetailScreen(
                                    id = key.id,
                                    onNavigateBack = { backStack.removeLastOrNull() },
                                )
                            }
                            SyncSettingsRoute -> NavEntry(key) {
                                SyncSettingsScreen(
                                    onNavigateBack = { backStack.removeLastOrNull() },
                                )
                            }
                            else -> NavEntry(key) { }
                        }
                    },
                )
            }
        }
    }
}

internal fun initialBackStack(detailId: Long?): List<Any> =
    if (detailId != null) listOf(HistoryRoute, DetailRoute(detailId))
    else listOf(HistoryRoute)
```

Note: `SyncSettingsRoute` is defined in `MainActivity.kt`. Move the import in `HistoryScreen.kt` to reference it if the compiler complains — alternatively define it in a shared `Routes.kt` file in the `ui` package.

- [ ] **Step 4: Fix `SyncSettingsRoute` visibility**

If `SyncSettingsScreen.kt` needs the route (it doesn't — only `MainActivity` references it), no change needed. Verify compile:

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

If there's an `Unresolved reference: SyncSettingsRoute` in `SyncSettingsScreen`, move the `data object SyncSettingsRoute` declaration to a new file `app/src/main/java/com/maroney/cleanshare/ui/Routes.kt` and remove it from `MainActivity.kt`.

- [ ] **Step 5: Run all tests**

```bash
./gradlew test connectedDebugAndroidTest 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/maroney/cleanshare/ui/HistoryViewModel.kt \
        app/src/main/java/com/maroney/cleanshare/ui/HistoryScreen.kt \
        app/src/main/java/com/maroney/cleanshare/MainActivity.kt
git commit -m "feat: add sync lifecycle hooks to HistoryViewModel and gear icon to HistoryScreen"
```

---

### Task 15: `AndroidManifest.xml` permissions

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add network permissions**

Open `app/src/main/AndroidManifest.xml`. Inside the `<manifest>` tag but before `<application>`, add if not already present:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

`INTERNET` is required for OkHttp. `ACCESS_NETWORK_STATE` is required for NsdManager service discovery on some devices.

- [ ] **Step 2: Build + run full test suite**

```bash
./gradlew test connectedDebugAndroidTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL` — all unit and instrumented tests pass.

- [ ] **Step 3: Final commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add INTERNET and ACCESS_NETWORK_STATE permissions for sync"
```

---

## Self-review checklist (completed inline)

| Spec requirement | Task |
|---|---|
| `ShareSource` enum (MOBILE / DESKTOP) | Task 1 |
| Room migration v3→v4 (syncId, updatedAt, source) | Tasks 2–4 |
| DataStore-backed `ServerConfig` | Task 5 |
| mDNS discovery with 5 s timeout | Task 6 |
| REST client (health, getAllRecords, postRecord, patchRecord, deleteRecord, putMetadata) | Task 7 |
| SSE stream parsing | Task 8 |
| LWW on full sync and SSE events | Task 9 |
| Push on write (insert, updateNotes, deleteById) | Task 10 |
| Metadata push after fetch | Task 11 |
| `SyncManager` nullable / offline-safe | Tasks 10, 12 |
| `SyncSettingsScreen` with toggle + manual field + test button | Task 13 |
| Gear icon in `HistoryScreen` | Task 14 |
| SSE start/stop tied to lifecycle | Task 14 |
| `SyncSettingsRoute` in nav graph | Task 14 |
| Permissions | Task 15 |
