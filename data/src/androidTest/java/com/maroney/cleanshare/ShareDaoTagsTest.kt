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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareDaoTagsTest {

    private lateinit var db: ShareDatabase
    private lateinit var shareDao: ShareDao

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, ShareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        shareDao = db.shareDao()
    }

    @After fun teardown() { db.close() }

    private suspend fun insertRecord(text: String = "https://example.com"): Long =
        shareDao.insert(ShareRecord(originalText = text, cleanedText = text))

    @Test fun tags_defaultToEmptyList() = runTest {
        val id = insertRecord()
        val record = shareDao.getById(id).first()
        assertEquals(emptyList<String>(), record?.tags)
    }

    @Test fun updateTags_persistsList() = runTest {
        val id = insertRecord()
        shareDao.updateTags(id, listOf("compose", "reading-list"))
        val record = shareDao.getById(id).first()
        assertEquals(listOf("compose", "reading-list"), record?.tags)
    }

    @Test fun updateTags_canClearBackToEmpty() = runTest {
        val id = insertRecord()
        shareDao.updateTags(id, listOf("compose"))
        shareDao.updateTags(id, emptyList())
        val record = shareDao.getById(id).first()
        assertEquals(emptyList<String>(), record?.tags)
    }

    @Test fun updateTags_roundTripsSpecialCharacters() = runTest {
        val id = insertRecord()
        val tricky = listOf("comma,tag", "quote\"tag")
        shareDao.updateTags(id, tricky)
        val record = shareDao.getById(id).first()
        assertEquals(tricky, record?.tags)
    }
}
