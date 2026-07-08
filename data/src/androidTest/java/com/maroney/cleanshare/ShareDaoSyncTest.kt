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

    @Test
    fun updateTagsAndTimestamp_bumpsUpdatedAt() = runTest {
        val id = insertRecord()
        db.shareDao().updateTagsAndTimestamp(id, listOf("compose", "kotlin"), 9999L)
        val record = db.shareDao().getBySyncId("uuid-1")!!
        assertEquals(listOf("compose", "kotlin"), record.tags)
        assertEquals(9999L, record.updatedAt)
    }

    @Test
    fun updateNotesAndTagsAndTimestamp_appliesBothFields() = runTest {
        val id = insertRecord()
        db.shareDao().updateNotesAndTagsAndTimestamp(id, "hello", listOf("compose"), 9999L)
        val record = db.shareDao().getBySyncId("uuid-1")!!
        assertEquals("hello", record.notes)
        assertEquals(listOf("compose"), record.tags)
        assertEquals(9999L, record.updatedAt)
    }

    @Test
    fun getByIdOnce_returnsRecord() = runTest {
        val id = insertRecord("known-uuid")
        val record = db.shareDao().getByIdOnce(id)
        assertNotNull(record)
        assertEquals("known-uuid", record!!.syncId)
    }

    @Test
    fun getByIdOnce_returnsNullWhenMissing() = runTest {
        assertNull(db.shareDao().getByIdOnce(999L))
    }
}
