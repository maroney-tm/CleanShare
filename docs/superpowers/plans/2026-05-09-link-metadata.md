# Link Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fetch OG/HTML metadata for every shared URL in the background via WorkManager and display title, thumbnail, description, and article snippet in adaptive history rows with shimmer loading states.

**Architecture:** A new `LinkMetadata` Room entity (keyed by `ShareRecord.id`) holds fetched data; a `MetadataFetcher` uses OkHttp + Jsoup to parse Open Graph tags; a `CoroutineWorker` runs the fetch off the main thread. The repository merges both flows so the UI reacts to metadata arriving after the fact. Rows adapt between a thumbnail-leading layout (Layout A) and a favicon-leading layout (Layout C) based on what was fetched; failed rows fall back to the current URL-only presentation with a "Retry" overflow menu item.

**Tech Stack:** Room 2.8.4, WorkManager 2.9.1, OkHttp 5.3.2 (already in build), Jsoup 1.17.2, Coil 3.4.0 (already in build), Jetpack Compose Material 3

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `app/src/main/java/com/maroney/cleanshare/data/LinkMetadata.kt` | Entity + `ContentType` + `FetchStatus` enums |
| Create | `app/src/main/java/com/maroney/cleanshare/data/Converters.kt` | Room TypeConverters for enums |
| Create | `app/src/main/java/com/maroney/cleanshare/data/LinkMetadataDao.kt` | DAO: upsert, observeAll, getPendingIds, deleteAll |
| Create | `app/src/main/java/com/maroney/cleanshare/data/ShareRecordWithMetadata.kt` | UI data model pairing record + nullable metadata |
| Create | `app/src/main/java/com/maroney/cleanshare/data/metadata/MetadataFetcher.kt` | OkHttp + Jsoup OG parser |
| Create | `app/src/main/java/com/maroney/cleanshare/data/metadata/AppWorkerFactory.kt` | Custom WorkerFactory for DI |
| Create | `app/src/main/java/com/maroney/cleanshare/data/metadata/FetchMetadataWorker.kt` | CoroutineWorker that calls MetadataFetcher |
| Create | `app/src/main/java/com/maroney/cleanshare/data/metadata/MetadataWorkScheduler.kt` | Enqueue helpers (schedule, pending, retry) |
| Create | `app/src/main/java/com/maroney/cleanshare/CleanShareApplication.kt` | App-level DI container + WorkManager config |
| Create | `app/src/main/java/com/maroney/cleanshare/ui/HistoryItem.kt` | All row composables: shimmer, Layout A, Layout C, fallback, overflow |
| Create | `app/src/androidTest/java/com/maroney/cleanshare/LinkMetadataDaoTest.kt` | Instrumented DAO tests |
| Create | `app/src/test/java/com/maroney/cleanshare/MetadataFetcherTest.kt` | JVM unit tests with MockWebServer |
| Modify | `app/src/main/java/com/maroney/cleanshare/data/ShareDao.kt` | `insert` returns `Long` |
| Modify | `app/src/main/java/com/maroney/cleanshare/data/ShareDatabase.kt` | Add entity, TypeConverters, v2 migration |
| Modify | `app/src/main/java/com/maroney/cleanshare/data/ShareRepository.kt` | Merge flows; call scheduler on insert; deleteAll clears both tables |
| Modify | `app/src/main/java/com/maroney/cleanshare/ui/HistoryViewModel.kt` | Consume ShareRecordWithMetadata; schedule pending on init; expose retryFetch |
| Modify | `app/src/main/java/com/maroney/cleanshare/ui/HistoryScreen.kt` | Use HistoryItem; pass ShareRecordWithMetadata |
| Modify | `app/src/main/java/com/maroney/cleanshare/ShareActivity.kt` | Get repository from CleanShareApplication |
| Modify | `app/src/main/AndroidManifest.xml` | Add Application name; disable default WorkManager init |
| Modify | `gradle/libs.versions.toml` | Add work, jsoup, coroutines-test, mockwebserver versions |
| Modify | `app/build.gradle.kts` | Add new deps; set versionName="0.1.0" retroactively |

---

## Task 1: Gradle setup + baseline version

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add new versions and library entries to `gradle/libs.versions.toml`**

  Add to the `[versions]` block:
  ```toml
  work = "2.9.1"
  jsoup = "1.17.2"
  kotlinxCoroutines = "1.9.0"
  ```

  Add to the `[libraries]` block:
  ```toml
  androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
  jsoup = { group = "org.jsoup", name = "jsoup", version.ref = "jsoup" }
  kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
  okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version = "5.3.2" }
  androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
  androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
  ```

- [ ] **Step 2: Update `app/build.gradle.kts`**

  Set the baseline version (retroactive label for the current feature set):
  ```kotlin
  versionCode = 1
  versionName = "0.1.0"
  ```

  Add to `dependencies { ... }`:
  ```kotlin
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.jsoup)
  implementation(libs.androidx.compose.material.icons.core)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.okhttp.mockwebserver)
  androidTestImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.androidx.room.testing)
  ```

- [ ] **Step 3: Sync and verify it compiles**

  ```bash
  cd /Users/pat/AndroidStudioProjects/CleanShare && ./gradlew assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

  ```bash
  git add gradle/libs.versions.toml app/build.gradle.kts
  git commit -m "chore: baseline v0.1.0, add WorkManager + Jsoup deps"
  ```

---

## Task 2: LinkMetadata entity + TypeConverters

**Files:**
- Create: `app/src/main/java/com/maroney/cleanshare/data/LinkMetadata.kt`
- Create: `app/src/main/java/com/maroney/cleanshare/data/Converters.kt`

- [ ] **Step 1: Create `data/LinkMetadata.kt`**

  ```kotlin
  package com.maroney.cleanshare.data

  import androidx.room.Entity
  import androidx.room.PrimaryKey

  enum class ContentType { VIDEO, ARTICLE, UNKNOWN }
  enum class FetchStatus { SUCCESS, FAILED }

  @Entity(tableName = "link_metadata")
  data class LinkMetadata(
      @PrimaryKey val shareRecordId: Long,
      val title: String?,
      val thumbnailUrl: String?,
      val description: String?,
      val articleSnippet: String?,
      val contentType: ContentType,
      val fetchStatus: FetchStatus,
  )
  ```

- [ ] **Step 2: Create `data/Converters.kt`**

  ```kotlin
  package com.maroney.cleanshare.data

  import androidx.room.TypeConverter

  class Converters {
      @TypeConverter fun fromContentType(v: ContentType): String = v.name
      @TypeConverter fun toContentType(v: String): ContentType = ContentType.valueOf(v)
      @TypeConverter fun fromFetchStatus(v: FetchStatus): String = v.name
      @TypeConverter fun toFetchStatus(v: String): FetchStatus = FetchStatus.valueOf(v)
  }
  ```

- [ ] **Step 3: Verify compilation**

  ```bash
  ./gradlew compileDebugKotlin
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/maroney/cleanshare/data/LinkMetadata.kt \
          app/src/main/java/com/maroney/cleanshare/data/Converters.kt
  git commit -m "feat: add LinkMetadata entity and Room TypeConverters"
  ```

---

## Task 3: LinkMetadataDao + instrumented tests

**Files:**
- Create: `app/src/main/java/com/maroney/cleanshare/data/LinkMetadataDao.kt`
- Create: `app/src/androidTest/java/com/maroney/cleanshare/LinkMetadataDaoTest.kt`

- [ ] **Step 1: Write the failing tests first — create `LinkMetadataDaoTest.kt`**

  ```kotlin
  package com.maroney.cleanshare

  import android.content.Context
  import androidx.room.Room
  import androidx.test.core.app.ApplicationProvider
  import androidx.test.ext.junit.runners.AndroidJUnit4
  import com.maroney.cleanshare.data.*
  import kotlinx.coroutines.flow.first
  import kotlinx.coroutines.test.runTest
  import org.junit.After
  import org.junit.Assert.*
  import org.junit.Before
  import org.junit.Test
  import org.junit.runner.RunWith

  @RunWith(AndroidJUnit4::class)
  class LinkMetadataDaoTest {

      private lateinit var db: ShareDatabase
      private lateinit var shareDao: ShareDao
      private lateinit var metadataDao: LinkMetadataDao

      @Before fun setup() {
          val ctx = ApplicationProvider.getApplicationContext<Context>()
          db = Room.inMemoryDatabaseBuilder(ctx, ShareDatabase::class.java)
              .allowMainThreadQueries()
              .build()
          shareDao = db.shareDao()
          metadataDao = db.linkMetadataDao()
      }

      @After fun teardown() { db.close() }

      private suspend fun insertRecord(text: String = "https://example.com"): Long =
          shareDao.insert(ShareRecord(originalText = text, cleanedText = text))

      @Test fun upsert_stores_and_observeAll_emits_it() = runTest {
          insertRecord()
          val meta = LinkMetadata(1L, "Title", null, "Desc", null, ContentType.UNKNOWN, FetchStatus.SUCCESS)
          metadataDao.upsert(meta)
          val all = metadataDao.observeAll().first()
          assertEquals(1, all.size)
          assertEquals("Title", all[0].title)
      }

      @Test fun upsert_replaces_existing_row() = runTest {
          insertRecord()
          metadataDao.upsert(LinkMetadata(1L, "Old", null, null, null, ContentType.UNKNOWN, FetchStatus.SUCCESS))
          metadataDao.upsert(LinkMetadata(1L, "New", null, null, null, ContentType.UNKNOWN, FetchStatus.SUCCESS))
          val all = metadataDao.observeAll().first()
          assertEquals(1, all.size)
          assertEquals("New", all[0].title)
      }

      @Test fun getPendingIds_returns_ids_without_metadata() = runTest {
          val id1 = insertRecord("https://a.com")
          val id2 = insertRecord("https://b.com")
          metadataDao.upsert(LinkMetadata(id1, null, null, null, null, ContentType.UNKNOWN, FetchStatus.SUCCESS))
          val pending = metadataDao.getPendingIds()
          assertEquals(listOf(id2), pending)
      }

      @Test fun getPendingIds_empty_when_all_have_metadata() = runTest {
          val id = insertRecord()
          metadataDao.upsert(LinkMetadata(id, null, null, null, null, ContentType.UNKNOWN, FetchStatus.SUCCESS))
          assertTrue(metadataDao.getPendingIds().isEmpty())
      }

      @Test fun deleteAll_clears_metadata_table() = runTest {
          insertRecord()
          metadataDao.upsert(LinkMetadata(1L, "T", null, null, null, ContentType.UNKNOWN, FetchStatus.SUCCESS))
          metadataDao.deleteAll()
          assertTrue(metadataDao.observeAll().first().isEmpty())
      }
  }
  ```

- [ ] **Step 2: Run tests and confirm they fail (class not found)**

  ```bash
  ./gradlew connectedDebugAndroidTest --tests "com.maroney.cleanshare.LinkMetadataDaoTest"
  ```
  Expected: compilation error — `LinkMetadataDao` and `db.linkMetadataDao()` do not exist yet.

- [ ] **Step 3: Create `data/LinkMetadataDao.kt`**

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

      @Query("SELECT * FROM link_metadata")
      fun observeAll(): Flow<List<LinkMetadata>>

      @Query("""
          SELECT id FROM share_history
          WHERE id NOT IN (SELECT shareRecordId FROM link_metadata)
      """)
      suspend fun getPendingIds(): List<Long>

      @Query("DELETE FROM link_metadata")
      suspend fun deleteAll()
  }
  ```

  Note: `ShareDatabase` must declare this DAO and include `LinkMetadata` as an entity before the tests can run — that is done in Task 4. The tests will remain unrunnable until Task 4 is complete. Proceed to Task 4 before running them.

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/maroney/cleanshare/data/LinkMetadataDao.kt \
          app/src/androidTest/java/com/maroney/cleanshare/LinkMetadataDaoTest.kt
  git commit -m "feat: add LinkMetadataDao and DAO instrumented tests"
  ```

---

## Task 4: ShareRecordWithMetadata + DB schema update

**Files:**
- Create: `app/src/main/java/com/maroney/cleanshare/data/ShareRecordWithMetadata.kt`
- Modify: `app/src/main/java/com/maroney/cleanshare/data/ShareDao.kt`
- Modify: `app/src/main/java/com/maroney/cleanshare/data/ShareDatabase.kt`

- [ ] **Step 1: Create `data/ShareRecordWithMetadata.kt`**

  ```kotlin
  package com.maroney.cleanshare.data

  data class ShareRecordWithMetadata(
      val record: ShareRecord,
      val metadata: LinkMetadata?,   // null = fetch pending
  )
  ```

- [ ] **Step 2: Update `ShareDao` — `insert` must return the new row's ID**

  Replace the `insert` declaration in `app/src/main/java/com/maroney/cleanshare/data/ShareDao.kt`:

  ```kotlin
  @Insert
  suspend fun insert(record: ShareRecord): Long
  ```

- [ ] **Step 3: Update `ShareDatabase` — add entity, TypeConverters, and migration**

  Full replacement of `app/src/main/java/com/maroney/cleanshare/data/ShareDatabase.kt`:

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
      version = 2,
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

          fun getInstance(context: Context): ShareDatabase =
              instance ?: synchronized(this) {
                  instance ?: Room.databaseBuilder(
                      context.applicationContext,
                      ShareDatabase::class.java,
                      "share_history.db",
                  )
                      .addMigrations(MIGRATION_1_2)
                      .build()
                      .also { instance = it }
              }
      }
  }
  ```

- [ ] **Step 4: Run DAO tests — they should now pass**

  ```bash
  ./gradlew connectedDebugAndroidTest --tests "com.maroney.cleanshare.LinkMetadataDaoTest"
  ```
  Expected: all 5 tests PASS.

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/maroney/cleanshare/data/ShareRecordWithMetadata.kt \
          app/src/main/java/com/maroney/cleanshare/data/ShareDao.kt \
          app/src/main/java/com/maroney/cleanshare/data/ShareDatabase.kt
  git commit -m "feat: Room v2 migration, LinkMetadata entity, ShareRecordWithMetadata model"
  ```

---

## Task 5: MetadataFetcher (TDD)

**Files:**
- Create: `app/src/test/java/com/maroney/cleanshare/MetadataFetcherTest.kt`
- Create: `app/src/main/java/com/maroney/cleanshare/data/metadata/MetadataFetcher.kt`

- [ ] **Step 1: Write the failing tests**

  Create `app/src/test/java/com/maroney/cleanshare/MetadataFetcherTest.kt`:

  ```kotlin
  package com.maroney.cleanshare

  import com.maroney.cleanshare.data.ContentType
  import com.maroney.cleanshare.data.FetchStatus
  import com.maroney.cleanshare.data.metadata.MetadataFetcher
  import kotlinx.coroutines.test.runTest
  import okhttp3.OkHttpClient
  import okhttp3.mockwebserver.MockResponse
  import okhttp3.mockwebserver.MockWebServer
  import org.junit.After
  import org.junit.Assert.*
  import org.junit.Before
  import org.junit.Test

  class MetadataFetcherTest {

      private val server = MockWebServer()
      private val fetcher = MetadataFetcher(OkHttpClient())

      @Before fun setUp() { server.start() }
      @After fun tearDown() { server.shutdown() }

      private fun enqueue(body: String, code: Int = 200) {
          server.enqueue(
              MockResponse()
                  .setResponseCode(code)
                  .addHeader("Content-Type", "text/html; charset=utf-8")
                  .setBody(body)
          )
      }

      @Test fun `parses og tags for video`() = runTest {
          enqueue("""
              <html><head>
              <meta property="og:title" content="My Video"/>
              <meta property="og:image" content="https://example.com/thumb.jpg"/>
              <meta property="og:description" content="A great video"/>
              <meta property="og:type" content="video.other"/>
              </head><body></body></html>
          """.trimIndent())

          val result = fetcher.fetch(server.url("/").toString())

          assertNotNull(result)
          assertEquals("My Video", result!!.title)
          assertEquals("https://example.com/thumb.jpg", result.thumbnailUrl)
          assertEquals("A great video", result.description)
          assertEquals(ContentType.VIDEO, result.contentType)
          assertNull(result.articleSnippet)
          assertEquals(FetchStatus.SUCCESS, result.fetchStatus)
      }

      @Test fun `parses article type and extracts snippet`() = runTest {
          enqueue("""
              <html><head>
              <meta property="og:title" content="Article Title"/>
              <meta property="og:type" content="article"/>
              </head><body>
              <article><p>First paragraph of article text.</p><p>Second paragraph.</p></article>
              </body></html>
          """.trimIndent())

          val result = fetcher.fetch(server.url("/").toString())

          assertNotNull(result)
          assertEquals(ContentType.ARTICLE, result!!.contentType)
          assertNotNull(result.articleSnippet)
          assertTrue(result.articleSnippet!!.contains("First paragraph"))
          assertTrue(result.articleSnippet.length <= 300)
      }

      @Test fun `detects article from article tag when og type is absent`() = runTest {
          enqueue("""
              <html><head><title>No OG</title></head>
              <body><article><p>Body text here.</p></article></body></html>
          """.trimIndent())

          val result = fetcher.fetch(server.url("/").toString())

          assertNotNull(result)
          assertEquals(ContentType.ARTICLE, result!!.contentType)
          assertNotNull(result.articleSnippet)
      }

      @Test fun `falls back to p tags when no article element`() = runTest {
          enqueue("""
              <html><head><meta property="og:type" content="article"/></head>
              <body><p>Lead paragraph text.</p><p>More content.</p></body></html>
          """.trimIndent())

          val result = fetcher.fetch(server.url("/").toString())

          assertNotNull(result)
          assertNotNull(result!!.articleSnippet)
          assertTrue(result.articleSnippet!!.contains("Lead paragraph"))
      }

      @Test fun `returns null on non-200 response`() = runTest {
          enqueue("", 404)
          assertNull(fetcher.fetch(server.url("/").toString()))
      }

      @Test fun `returns null on network error`() = runTest {
          server.shutdown()
          assertNull(fetcher.fetch("http://localhost:1/bad"))
      }

      @Test fun `handles missing og tags - returns result with null fields`() = runTest {
          enqueue("<html><head><title>Plain</title></head><body><p>Text</p></body></html>")
          val result = fetcher.fetch(server.url("/").toString())
          assertNotNull(result)
          assertNull(result!!.title)
          assertNull(result.thumbnailUrl)
          assertEquals(ContentType.UNKNOWN, result.contentType)
      }

      @Test fun `snippet is capped at 300 chars`() = runTest {
          val longText = "word ".repeat(200)
          enqueue("""
              <html><head><meta property="og:type" content="article"/></head>
              <body><article><p>$longText</p></article></body></html>
          """.trimIndent())

          val result = fetcher.fetch(server.url("/").toString())
          assertNotNull(result!!.articleSnippet)
          assertTrue(result.articleSnippet!!.length <= 300)
      }
  }
  ```

- [ ] **Step 2: Run tests — confirm they fail**

  ```bash
  ./gradlew test --tests "com.maroney.cleanshare.MetadataFetcherTest"
  ```
  Expected: compilation error — `MetadataFetcher` does not exist yet.

- [ ] **Step 3: Create the metadata package directory and `MetadataFetcher.kt`**

  Create `app/src/main/java/com/maroney/cleanshare/data/metadata/MetadataFetcher.kt`:

  ```kotlin
  package com.maroney.cleanshare.data.metadata

  import com.maroney.cleanshare.data.ContentType
  import com.maroney.cleanshare.data.FetchStatus
  import com.maroney.cleanshare.data.LinkMetadata
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.withContext
  import okhttp3.OkHttpClient
  import okhttp3.Request
  import org.jsoup.Jsoup
  import org.jsoup.nodes.Document

  class MetadataFetcher(private val okHttpClient: OkHttpClient) {

      suspend fun fetch(url: String): LinkMetadata? = withContext(Dispatchers.IO) {
          try {
              val request = Request.Builder().url(url).build()
              val response = okHttpClient.newCall(request).execute()
              if (!response.isSuccessful) return@withContext null
              val body = response.body?.string() ?: return@withContext null
              parse(body, url)
          } catch (_: Exception) {
              null
          }
      }

      private fun parse(html: String, baseUrl: String): LinkMetadata {
          val doc = Jsoup.parse(html, baseUrl)

          val title = doc.ogContent("og:title")
          val thumbnailUrl = doc.ogContent("og:image")
          val description = doc.ogContent("og:description")
          val ogType = doc.ogContent("og:type") ?: ""

          val contentType = when {
              ogType.startsWith("video") -> ContentType.VIDEO
              ogType == "article" || doc.selectFirst("article") != null -> ContentType.ARTICLE
              else -> ContentType.UNKNOWN
          }

          val articleSnippet = if (contentType == ContentType.ARTICLE) extractSnippet(doc) else null

          return LinkMetadata(
              shareRecordId = 0L,   // caller must set this before upserting
              title = title,
              thumbnailUrl = thumbnailUrl,
              description = description,
              articleSnippet = articleSnippet,
              contentType = contentType,
              fetchStatus = FetchStatus.SUCCESS,
          )
      }

      private fun Document.ogContent(property: String): String? =
          selectFirst("meta[property=$property]")
              ?.attr("content")
              ?.takeIf { it.isNotBlank() }

      private fun extractSnippet(doc: Document): String? {
          val source = doc.selectFirst("article") ?: doc
          return source.select("p")
              .take(3)
              .joinToString(" ") { it.text() }
              .take(300)
              .takeIf { it.isNotBlank() }
      }
  }
  ```

- [ ] **Step 4: Run tests — confirm they pass**

  ```bash
  ./gradlew test --tests "com.maroney.cleanshare.MetadataFetcherTest"
  ```
  Expected: all 8 tests PASS.

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/maroney/cleanshare/data/metadata/MetadataFetcher.kt \
          app/src/test/java/com/maroney/cleanshare/MetadataFetcherTest.kt
  git commit -m "feat: MetadataFetcher with OkHttp + Jsoup OG parsing (TDD)"
  ```

---

## Task 6: CleanShareApplication + AppWorkerFactory + Manifest

**Files:**
- Create: `app/src/main/java/com/maroney/cleanshare/CleanShareApplication.kt`
- Create: `app/src/main/java/com/maroney/cleanshare/data/metadata/AppWorkerFactory.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `AppWorkerFactory.kt`**

  ```kotlin
  package com.maroney.cleanshare.data.metadata

  import android.content.Context
  import androidx.work.ListenableWorker
  import androidx.work.WorkerFactory
  import androidx.work.WorkerParameters
  import com.maroney.cleanshare.data.LinkMetadataDao

  class AppWorkerFactory(
      private val fetcher: MetadataFetcher,
      private val dao: LinkMetadataDao,
  ) : WorkerFactory() {
      override fun createWorker(
          appContext: Context,
          workerClassName: String,
          workerParameters: WorkerParameters,
      ): ListenableWorker? = when (workerClassName) {
          FetchMetadataWorker::class.java.name ->
              FetchMetadataWorker(appContext, workerParameters, fetcher, dao)
          else -> null
      }
  }
  ```

  Note: `FetchMetadataWorker` is created in Task 7 — this file will not compile until then. That is expected; both files are committed together at the end of Task 7.

- [ ] **Step 2: Create `CleanShareApplication.kt`**

  ```kotlin
  package com.maroney.cleanshare

  import android.app.Application
  import androidx.work.Configuration
  import com.maroney.cleanshare.data.ShareDatabase
  import com.maroney.cleanshare.data.metadata.AppWorkerFactory
  import com.maroney.cleanshare.data.metadata.MetadataFetcher
  import com.maroney.cleanshare.data.metadata.MetadataWorkScheduler
  import com.maroney.cleanshare.data.ShareRepository
  import okhttp3.OkHttpClient
  import java.util.concurrent.TimeUnit

  class CleanShareApplication : Application(), Configuration.Provider {

      val database by lazy { ShareDatabase.getInstance(this) }

      private val okHttpClient by lazy {
          OkHttpClient.Builder()
              .connectTimeout(10, TimeUnit.SECONDS)
              .readTimeout(10, TimeUnit.SECONDS)
              .followRedirects(true)
              .build()
      }

      val metadataFetcher by lazy { MetadataFetcher(okHttpClient) }

      val workScheduler by lazy {
          MetadataWorkScheduler(androidx.work.WorkManager.getInstance(this))
      }

      val shareRepository by lazy {
          ShareRepository(database.shareDao(), database.linkMetadataDao(), workScheduler)
      }

      override val workManagerConfiguration: Configuration
          get() = Configuration.Builder()
              .setWorkerFactory(AppWorkerFactory(metadataFetcher, database.linkMetadataDao()))
              .build()
  }
  ```

  Note: `MetadataWorkScheduler` and `ShareRepository`'s new constructor are created in Tasks 7 and 8 respectively. This file will not compile until all three tasks are done. Hold the commit until the end of Task 8.

- [ ] **Step 3: Update `AndroidManifest.xml`**

  Full replacement:

  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <manifest xmlns:android="http://schemas.android.com/apk/res/android"
      xmlns:tools="http://schemas.android.com/tools">

      <uses-permission android:name="android.permission.INTERNET" />

      <application
          android:name=".CleanShareApplication"
          android:allowBackup="true"
          android:dataExtractionRules="@xml/data_extraction_rules"
          android:fullBackupContent="@xml/backup_rules"
          android:icon="@mipmap/ic_launcher"
          android:label="@string/app_name"
          android:roundIcon="@mipmap/ic_launcher_round"
          android:supportsRtl="true"
          android:theme="@style/Theme.CleanShare">

          <!-- Disable WorkManager's default initializer so our custom factory is used -->
          <provider
              android:name="androidx.startup.InitializationProvider"
              android:authorities="${applicationId}.androidx-startup"
              android:exported="false"
              tools:node="merge">
              <meta-data
                  android:name="androidx.work.WorkManagerInitializer"
                  android:value="androidx.startup.InitializationProvider"
                  tools:node="remove" />
          </provider>

          <activity
              android:name="com.maroney.cleanshare.MainActivity"
              android:exported="true">
              <intent-filter>
                  <action android:name="android.intent.action.MAIN" />
                  <category android:name="android.intent.category.LAUNCHER" />
              </intent-filter>
          </activity>

          <activity
              android:name="com.maroney.cleanshare.ShareActivity"
              android:exported="true"
              android:theme="@style/Theme.Transparent"
              android:excludeFromRecents="true"
              android:noHistory="true">
              <intent-filter>
                  <action android:name="android.intent.action.SEND" />
                  <category android:name="android.intent.category.DEFAULT" />
                  <data android:mimeType="text/plain" />
              </intent-filter>
          </activity>

      </application>

  </manifest>
  ```

- [ ] **Step 4: Stage files (do not commit yet — waits for Tasks 7 and 8)**

  ```bash
  git add app/src/main/java/com/maroney/cleanshare/CleanShareApplication.kt \
          app/src/main/java/com/maroney/cleanshare/data/metadata/AppWorkerFactory.kt \
          app/src/main/AndroidManifest.xml
  ```

---

## Task 7: FetchMetadataWorker + MetadataWorkScheduler

**Files:**
- Create: `app/src/main/java/com/maroney/cleanshare/data/metadata/FetchMetadataWorker.kt`
- Create: `app/src/main/java/com/maroney/cleanshare/data/metadata/MetadataWorkScheduler.kt`

- [ ] **Step 1: Create `FetchMetadataWorker.kt`**

  ```kotlin
  package com.maroney.cleanshare.data.metadata

  import android.content.Context
  import androidx.work.CoroutineWorker
  import androidx.work.WorkerParameters
  import com.maroney.cleanshare.data.ContentType
  import com.maroney.cleanshare.data.FetchStatus
  import com.maroney.cleanshare.data.LinkMetadata
  import com.maroney.cleanshare.data.LinkMetadataDao

  class FetchMetadataWorker(
      context: Context,
      params: WorkerParameters,
      private val fetcher: MetadataFetcher,
      private val dao: LinkMetadataDao,
  ) : CoroutineWorker(context, params) {

      companion object {
          const val KEY_SHARE_RECORD_ID = "share_record_id"
          const val KEY_URL = "url"
      }

      override suspend fun doWork(): Result {
          val shareRecordId = inputData.getLong(KEY_SHARE_RECORD_ID, -1L)
          val url = inputData.getString(KEY_URL) ?: return Result.success()
          if (shareRecordId == -1L) return Result.success()

          val fetched = fetcher.fetch(url)
          val metadata = if (fetched != null) {
              fetched.copy(shareRecordId = shareRecordId)
          } else {
              LinkMetadata(
                  shareRecordId = shareRecordId,
                  title = null,
                  thumbnailUrl = null,
                  description = null,
                  articleSnippet = null,
                  contentType = ContentType.UNKNOWN,
                  fetchStatus = FetchStatus.FAILED,
              )
          }
          dao.upsert(metadata)
          return Result.success()
      }
  }
  ```

- [ ] **Step 2: Create `MetadataWorkScheduler.kt`**

  ```kotlin
  package com.maroney.cleanshare.data.metadata

  import androidx.work.Constraints
  import androidx.work.ExistingWorkPolicy
  import androidx.work.NetworkType
  import androidx.work.OneTimeWorkRequestBuilder
  import androidx.work.WorkManager
  import androidx.work.workDataOf
  import com.maroney.cleanshare.data.ShareRecordWithMetadata

  class MetadataWorkScheduler(private val workManager: WorkManager) {

      fun scheduleFetch(shareRecordId: Long, url: String) {
          enqueue(shareRecordId, url, ExistingWorkPolicy.KEEP)
      }

      fun schedulePendingFetches(records: List<ShareRecordWithMetadata>) {
          records
              .filter { it.metadata == null }
              .forEach { item ->
                  val url = extractUrl(item.record.cleanedText) ?: return@forEach
                  scheduleFetch(item.record.id, url)
              }
      }

      fun retryFetch(shareRecordId: Long, url: String) {
          enqueue(shareRecordId, url, ExistingWorkPolicy.REPLACE)
      }

      private fun enqueue(shareRecordId: Long, url: String, policy: ExistingWorkPolicy) {
          val request = OneTimeWorkRequestBuilder<FetchMetadataWorker>()
              .setInputData(workDataOf(
                  FetchMetadataWorker.KEY_SHARE_RECORD_ID to shareRecordId,
                  FetchMetadataWorker.KEY_URL to url,
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

- [ ] **Step 3: Verify compilation**

  ```bash
  ./gradlew compileDebugKotlin
  ```
  Expected: `BUILD SUCCESSFUL` (note: `ShareRepository` still has its old constructor at this point — compile errors there are expected and are resolved in Task 8).

- [ ] **Step 4: Commit tasks 6 and 7 together**

  ```bash
  git add app/src/main/java/com/maroney/cleanshare/data/metadata/FetchMetadataWorker.kt \
          app/src/main/java/com/maroney/cleanshare/data/metadata/MetadataWorkScheduler.kt
  git commit -m "feat: WorkManager worker, scheduler, custom factory, and Application class"
  ```

---

## Task 8: ShareRepository update

**Files:**
- Modify: `app/src/main/java/com/maroney/cleanshare/data/ShareRepository.kt`

- [ ] **Step 1: Replace `ShareRepository.kt` entirely**

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

      suspend fun insert(record: ShareRecord) {
          val id = shareDao.insert(record)
          val url = record.cleanedText
              .split("\\s+".toRegex())
              .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
          if (url != null) workScheduler.scheduleFetch(id, url)
      }

      suspend fun deleteAll() {
          shareDao.deleteAll()
          metadataDao.deleteAll()
      }
  }
  ```

- [ ] **Step 2: Verify compilation**

  ```bash
  ./gradlew compileDebugKotlin
  ```
  Expected: `BUILD SUCCESSFUL` (note: call sites in `ShareActivity` and `HistoryViewModel` will have type errors because they still construct the old `ShareRepository` — resolved in Tasks 9 and 10).

- [ ] **Step 3: Commit**

  ```bash
  git add app/src/main/java/com/maroney/cleanshare/data/ShareRepository.kt
  git commit -m "feat: ShareRepository merges flows and schedules metadata fetch on insert"
  ```

---

## Task 9: HistoryViewModel update

**Files:**
- Modify: `app/src/main/java/com/maroney/cleanshare/ui/HistoryViewModel.kt`

- [ ] **Step 1: Replace `HistoryViewModel.kt` entirely**

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
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.flow.SharingStarted
  import kotlinx.coroutines.flow.StateFlow
  import kotlinx.coroutines.flow.stateIn
  import kotlinx.coroutines.launch

  class HistoryViewModel(
      private val repository: ShareRepository,
      private val workScheduler: MetadataWorkScheduler,
  ) : ViewModel() {

      val history: StateFlow<List<ShareRecordWithMetadata>> = repository.getAll()
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5_000),
              initialValue = emptyList(),
          )

      init {
          // Schedule fetches for any records missing metadata — covers existing
          // rows on first launch after upgrade and workers that didn't finish.
          viewModelScope.launch(Dispatchers.IO) {
              val snapshot = repository.getAll().first()
              workScheduler.schedulePendingFetches(snapshot)
          }
      }

      fun clearHistory() {
          viewModelScope.launch { repository.deleteAll() }
      }

      fun retryFetch(shareRecordId: Long, url: String) {
          workScheduler.retryFetch(shareRecordId, url)
      }

      companion object {
          val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
              @Suppress("UNCHECKED_CAST")
              override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                  val app = extras[APPLICATION_KEY] as CleanShareApplication
                  return HistoryViewModel(app.shareRepository, app.workScheduler) as T
              }
          }
      }
  }
  ```

- [ ] **Step 2: Verify compilation**

  ```bash
  ./gradlew compileDebugKotlin
  ```
  Expected: `BUILD SUCCESSFUL` (ShareActivity still has a type error — resolved in Task 10).

- [ ] **Step 3: Commit**

  ```bash
  git add app/src/main/java/com/maroney/cleanshare/ui/HistoryViewModel.kt
  git commit -m "feat: HistoryViewModel consumes ShareRecordWithMetadata, schedules pending fetches"
  ```

---

## Task 10: ShareActivity update

**Files:**
- Modify: `app/src/main/java/com/maroney/cleanshare/ShareActivity.kt`

- [ ] **Step 1: Update `ShareActivity.kt` to get the repository from `CleanShareApplication`**

  Replace the repository construction block inside `lifecycleScope.launch`:

  ```kotlin
  package com.maroney.cleanshare

  import android.content.ComponentName
  import android.content.Intent
  import android.os.Bundle
  import androidx.activity.ComponentActivity
  import androidx.lifecycle.lifecycleScope
  import com.maroney.cleanshare.data.ShareRecord
  import com.maroney.cleanshare.domain.UrlSanitizer
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.launch
  import kotlinx.coroutines.withContext

  class ShareActivity : ComponentActivity() {

      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)

          val raw = intent
              ?.takeIf { it.action == Intent.ACTION_SEND }
              ?.getStringExtra(Intent.EXTRA_TEXT)
              ?.trim()

          if (raw.isNullOrEmpty()) {
              finish()
              return
          }

          val cleaned = UrlSanitizer.cleanText(raw)

          lifecycleScope.launch {
              withContext(Dispatchers.IO) {
                  (application as CleanShareApplication).shareRepository
                      .insert(ShareRecord(originalText = raw, cleanedText = cleaned))
              }

              val send = Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_TEXT, cleaned)
                  addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
              val chooser = Intent.createChooser(send, null).apply {
                  putExtra(
                      Intent.EXTRA_EXCLUDE_COMPONENTS,
                      arrayOf(ComponentName(this@ShareActivity, ShareActivity::class.java)),
                  )
              }
              startActivity(chooser)
              finish()
          }
      }
  }
  ```

- [ ] **Step 2: Verify full project compiles cleanly**

  ```bash
  ./gradlew assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

  ```bash
  git add app/src/main/java/com/maroney/cleanshare/ShareActivity.kt
  git commit -m "feat: ShareActivity uses CleanShareApplication repository"
  ```

---

## Task 11: HistoryItem UI (shimmer + adaptive rows + overflow menu)

**Files:**
- Create: `app/src/main/java/com/maroney/cleanshare/ui/HistoryItem.kt`

- [ ] **Step 1: Create `ui/HistoryItem.kt`**

  ```kotlin
  package com.maroney.cleanshare.ui

  import android.content.ClipData
  import android.content.Intent
  import android.text.format.DateUtils
  import androidx.compose.animation.core.LinearEasing
  import androidx.compose.animation.core.RepeatMode
  import androidx.compose.animation.core.animateFloat
  import androidx.compose.animation.core.infiniteRepeatable
  import androidx.compose.animation.core.rememberInfiniteTransition
  import androidx.compose.animation.core.tween
  import androidx.compose.foundation.ExperimentalFoundationApi
  import androidx.compose.foundation.background
  import androidx.compose.foundation.combinedClickable
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.size
  import androidx.compose.foundation.shape.RoundedCornerShape
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.MoreVert
  import androidx.compose.material3.DropdownMenu
  import androidx.compose.material3.DropdownMenuItem
  import androidx.compose.material3.Icon
  import androidx.compose.material3.IconButton
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Text
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.rememberCoroutineScope
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.draw.clip
  import androidx.compose.ui.geometry.Offset
  import androidx.compose.ui.graphics.Brush
  import androidx.compose.ui.hapticfeedback.HapticFeedbackType
  import androidx.compose.ui.layout.ContentScale
  import androidx.compose.ui.platform.LocalClipboard
  import androidx.compose.ui.platform.LocalContext
  import androidx.compose.ui.platform.LocalHapticFeedback
  import androidx.compose.ui.platform.toClipEntry
  import androidx.compose.ui.text.font.FontFamily
  import androidx.compose.ui.text.style.TextOverflow
  import androidx.compose.ui.unit.dp
  import androidx.core.net.toUri
  import coil3.compose.AsyncImage
  import com.maroney.cleanshare.data.FetchStatus
  import com.maroney.cleanshare.data.ShareRecordWithMetadata
  import kotlinx.coroutines.launch

  // ── Public entry point ──────────────────────────────────────────────────────

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  fun HistoryItem(
      item: ShareRecordWithMetadata,
      onRetryFetch: (shareRecordId: Long, url: String) -> Unit,
  ) {
      val context = LocalContext.current
      val clipboard = LocalClipboard.current
      val haptic = LocalHapticFeedback.current
      val scope = rememberCoroutineScope()

      val cleanedUrl = item.record.cleanedText

      val onOpen: () -> Unit = {
          try { context.startActivity(Intent(Intent.ACTION_VIEW, cleanedUrl.toUri())) }
          catch (_: Exception) {}
      }
      val onCopy: () -> Unit = {
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          scope.launch {
              clipboard.setClipEntry(ClipData.newPlainText("link", cleanedUrl).toClipEntry())
          }
      }

      Box(
          modifier = Modifier
              .fillMaxWidth()
              .combinedClickable(onClick = onOpen, onLongClick = onCopy),
      ) {
          when {
              item.metadata == null -> ShimmerRow()
              item.metadata.fetchStatus == FetchStatus.FAILED -> FallbackRow(item, onCopy, onOpen, onRetryFetch)
              item.metadata.thumbnailUrl != null -> LayoutA(item, onCopy, onOpen)
              else -> LayoutC(item, onCopy, onOpen)
          }
      }
  }

  // ── Shimmer ─────────────────────────────────────────────────────────────────

  @Composable
  private fun ShimmerRow() {
      val transition = rememberInfiniteTransition(label = "shimmer")
      val offset by transition.animateFloat(
          initialValue = -1000f,
          targetValue = 2000f,
          animationSpec = infiniteRepeatable(
              animation = tween(1400, easing = LinearEasing),
              repeatMode = RepeatMode.Restart,
          ),
          label = "shimmer_x",
      )
      val shimmerBrush = Brush.linearGradient(
          colors = listOf(
              MaterialTheme.colorScheme.surfaceVariant,
              MaterialTheme.colorScheme.surface,
              MaterialTheme.colorScheme.surfaceVariant,
          ),
          start = Offset(offset, 0f),
          end = Offset(offset + 600f, 0f),
      )

      Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.Top,
      ) {
          Box(
              modifier = Modifier
                  .size(64.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(shimmerBrush),
          )
          Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
              Box(Modifier.fillMaxWidth(0.80f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
              Box(Modifier.fillMaxWidth(1.00f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
              Box(Modifier.fillMaxWidth(0.65f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
              Box(Modifier.fillMaxWidth(0.55f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
          }
      }
  }

  // ── Layout A: leading 64×64 thumbnail ────────────────────────────────────────

  @Composable
  private fun LayoutA(
      item: ShareRecordWithMetadata,
      onCopy: () -> Unit,
      onOpen: () -> Unit,
  ) {
      val metadata = item.metadata!!
      Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.Top,
      ) {
          AsyncImage(
              model = metadata.thumbnailUrl,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
          )
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              metadata.title?.let {
                  Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
              }
              metadata.description?.let {
                  Text(it, style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 2, overflow = TextOverflow.Ellipsis)
              }
              UrlLines(item)
          }
          OverflowMenu(onCopy = onCopy, onOpen = onOpen, onRetry = null)
      }
  }

  // ── Layout C: 32×32 favicon, no thumbnail ────────────────────────────────────

  @Composable
  private fun LayoutC(
      item: ShareRecordWithMetadata,
      onCopy: () -> Unit,
      onOpen: () -> Unit,
  ) {
      val metadata = item.metadata!!
      val faviconUrl = remember(item.record.cleanedText) {
          runCatching { item.record.cleanedText.toUri().host }
              .getOrNull()?.takeIf { it.isNotBlank() }
              ?.let { "https://www.google.com/s2/favicons?sz=64&domain=$it" }
      }
      Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.Top,
      ) {
          Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
              if (faviconUrl != null) AsyncImage(model = faviconUrl, contentDescription = null)
          }
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              metadata.title?.let {
                  Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
              }
              val snippet = metadata.articleSnippet ?: metadata.description
              snippet?.let {
                  Text(it, style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 2, overflow = TextOverflow.Ellipsis)
              }
              UrlLines(item)
          }
          OverflowMenu(onCopy = onCopy, onOpen = onOpen, onRetry = null)
      }
  }

  // ── Fallback: fetch failed ────────────────────────────────────────────────────

  @Composable
  private fun FallbackRow(
      item: ShareRecordWithMetadata,
      onCopy: () -> Unit,
      onOpen: () -> Unit,
      onRetryFetch: (shareRecordId: Long, url: String) -> Unit,
  ) {
      val faviconUrl = remember(item.record.cleanedText) {
          runCatching { item.record.cleanedText.toUri().host }
              .getOrNull()?.takeIf { it.isNotBlank() }
              ?.let { "https://www.google.com/s2/favicons?sz=64&domain=$it" }
      }
      val onRetry: () -> Unit = {
          val url = item.record.cleanedText.split("\\s+".toRegex())
              .firstOrNull { it.startsWith("http") } ?: item.record.cleanedText
          onRetryFetch(item.record.id, url)
      }
      Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.Top,
      ) {
          Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
              if (faviconUrl != null) AsyncImage(model = faviconUrl, contentDescription = null)
          }
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              UrlLines(item)
              Text(
                  text = formatAge(item.record.sharedAt),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
          }
          OverflowMenu(onCopy = onCopy, onOpen = onOpen, onRetry = onRetry)
      }
  }

  // ── Shared sub-composables ────────────────────────────────────────────────────

  @Composable
  private fun UrlLines(item: ShareRecordWithMetadata) {
      Text(
          text = item.record.cleanedText,
          style = MaterialTheme.typography.labelSmall,
          fontFamily = FontFamily.Monospace,
          color = MaterialTheme.colorScheme.outline,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      if (item.record.originalText != item.record.cleanedText) {
          Text(
              text = item.record.originalText,
              style = MaterialTheme.typography.labelSmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
      }
  }

  @Composable
  private fun OverflowMenu(
      onCopy: () -> Unit,
      onOpen: () -> Unit,
      onRetry: (() -> Unit)?,
  ) {
      var expanded by remember { mutableStateOf(false) }
      Box {
          IconButton(onClick = { expanded = true }) {
              Icon(Icons.Default.MoreVert, contentDescription = "More options")
          }
          DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
              if (onRetry != null) {
                  DropdownMenuItem(
                      text = { Text("Retry metadata fetch") },
                      onClick = { onRetry(); expanded = false },
                  )
              }
              DropdownMenuItem(
                  text = { Text("Copy link") },
                  onClick = { onCopy(); expanded = false },
              )
              DropdownMenuItem(
                  text = { Text("Open link") },
                  onClick = { onOpen(); expanded = false },
              )
          }
      }
  }

  internal fun formatAge(timestamp: Long): String {
      val delta = System.currentTimeMillis() - timestamp
      if (delta < 60_000L) return "Just now"
      return DateUtils.getRelativeTimeSpanString(
          timestamp,
          System.currentTimeMillis(),
          DateUtils.MINUTE_IN_MILLIS,
          DateUtils.FORMAT_ABBREV_RELATIVE,
      ).toString()
  }
  ```

- [ ] **Step 2: Verify compilation**

  ```bash
  ./gradlew compileDebugKotlin
  ```
  Expected: `BUILD SUCCESSFUL` (HistoryScreen still references `ShareRecord` — fixed in Task 12).

- [ ] **Step 3: Commit**

  ```bash
  git add app/src/main/java/com/maroney/cleanshare/ui/HistoryItem.kt
  git commit -m "feat: adaptive row composables with shimmer, Layout A/C, fallback, overflow menu"
  ```

---

## Task 12: HistoryScreen update + version bump

**Files:**
- Modify: `app/src/main/java/com/maroney/cleanshare/ui/HistoryScreen.kt`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Replace `HistoryScreen.kt`**

  ```kotlin
  package com.maroney.cleanshare.ui

  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.PaddingValues
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.items
  import androidx.compose.material3.ExperimentalMaterial3Api
  import androidx.compose.material3.HorizontalDivider
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Scaffold
  import androidx.compose.material3.Text
  import androidx.compose.material3.TextButton
  import androidx.compose.material3.TopAppBar
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.text.style.TextAlign
  import androidx.compose.ui.unit.dp
  import androidx.lifecycle.compose.collectAsStateWithLifecycle

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun HistoryScreen(viewModel: HistoryViewModel) {
      val history by viewModel.history.collectAsStateWithLifecycle()

      Scaffold(
          topBar = {
              TopAppBar(
                  title = { Text("Clean Share") },
                  actions = {
                      if (history.isNotEmpty()) {
                          TextButton(onClick = { viewModel.clearHistory() }) {
                              Text("Clear all")
                          }
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
                  contentPadding = PaddingValues(vertical = 8.dp),
              ) {
                  items(history, key = { it.record.id }) { item ->
                      HistoryItem(
                          item = item,
                          onRetryFetch = { id, url -> viewModel.retryFetch(id, url) },
                      )
                      HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
              modifier = Modifier.padding(32.dp),
          )
      }
  }
  ```

- [ ] **Step 2: Bump to v0.2.0 in `app/build.gradle.kts`**

  ```kotlin
  versionCode = 2
  versionName = "0.2.0"
  ```

- [ ] **Step 3: Full build**

  ```bash
  ./gradlew assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL` with no warnings about unresolved references.

- [ ] **Step 4: Run all unit tests**

  ```bash
  ./gradlew test
  ```
  Expected: all pass including `MetadataFetcherTest` and `UrlSanitizerTest`.

- [ ] **Step 5: Run instrumented tests**

  ```bash
  ./gradlew connectedDebugAndroidTest
  ```
  Expected: `LinkMetadataDaoTest` all 5 tests pass.

- [ ] **Step 6: Final commit**

  ```bash
  git add app/src/main/java/com/maroney/cleanshare/ui/HistoryScreen.kt \
          app/build.gradle.kts
  git commit -m "feat: v0.2.0 — link metadata with shimmer, adaptive rows, WorkManager fetch"
  ```
