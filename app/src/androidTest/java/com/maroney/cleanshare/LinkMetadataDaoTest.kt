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
        val id = insertRecord()
        val meta = LinkMetadata(id, "Title", null, "Desc", null, ContentType.UNKNOWN, FetchStatus.SUCCESS)
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

    @Test fun deleteAll_clears_metadata_table() = runTest {
        val id = insertRecord()
        metadataDao.upsert(LinkMetadata(id, "T", null, null, null, ContentType.UNKNOWN, FetchStatus.SUCCESS))
        metadataDao.deleteAll()
        assertTrue(metadataDao.observeAll().first().isEmpty())
    }
}
