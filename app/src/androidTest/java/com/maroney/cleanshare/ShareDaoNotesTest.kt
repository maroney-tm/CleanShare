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
