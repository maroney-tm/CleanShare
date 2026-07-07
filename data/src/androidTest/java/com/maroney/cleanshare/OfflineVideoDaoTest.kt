package com.maroney.cleanshare

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maroney.cleanshare.data.OfflineVideoDao
import com.maroney.cleanshare.data.OfflineVideoRecord
import com.maroney.cleanshare.data.OfflineVideoStatus
import com.maroney.cleanshare.data.ShareDao
import com.maroney.cleanshare.data.ShareDatabase
import com.maroney.cleanshare.data.ShareRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineVideoDaoTest {

    private lateinit var db: ShareDatabase
    private lateinit var shareDao: ShareDao
    private lateinit var offlineVideoDao: OfflineVideoDao

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, ShareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        shareDao = db.shareDao()
        offlineVideoDao = db.offlineVideoDao()
    }

    @After fun teardown() { db.close() }

    private suspend fun insertRecord(text: String = "https://example.com"): Long =
        shareDao.insert(ShareRecord(originalText = text, cleanedText = text))

    @Test fun upsert_stores_and_observeById_emits_it() = runTest {
        val id = insertRecord()
        offlineVideoDao.upsert(
            OfflineVideoRecord(id, status = OfflineVideoStatus.COMPLETE, localFilePath = "/x/$id.mp4", fileSizeBytes = 42)
        )
        val found = offlineVideoDao.observeById(id).first()
        assertNotNull(found)
        assertEquals(OfflineVideoStatus.COMPLETE, found!!.status)
        assertEquals(42L, found.fileSizeBytes)
    }

    @Test fun upsert_replaces_existing_row() = runTest {
        val id = insertRecord()
        offlineVideoDao.upsert(OfflineVideoRecord(id, status = OfflineVideoStatus.DOWNLOADING))
        offlineVideoDao.upsert(OfflineVideoRecord(id, status = OfflineVideoStatus.COMPLETE, fileSizeBytes = 10))
        val all = offlineVideoDao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(OfflineVideoStatus.COMPLETE, all[0].status)
    }

    @Test fun deleteByShareRecordId_removesRow() = runTest {
        val id = insertRecord()
        offlineVideoDao.upsert(OfflineVideoRecord(id, status = OfflineVideoStatus.COMPLETE))
        offlineVideoDao.deleteByShareRecordId(id)
        assertNull(offlineVideoDao.getByIdOnce(id))
    }
}
