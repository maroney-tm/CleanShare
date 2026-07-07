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

    @Test
    fun migrate5To6_createsOfflineVideoTable() {
        val db = helper.createDatabase("migration_test_5_6", 5)
        db.close()

        val migrated = helper.runMigrationsAndValidate(
            "migration_test_5_6", 6, true, ShareDatabase.MIGRATION_5_6
        )

        migrated.execSQL(
            "INSERT INTO offline_video (shareRecordId, status, localFilePath, fileSizeBytes, savedAt, errorMessage) " +
                "VALUES (1, 'COMPLETE', '/x/1.mp4', 42, 1000, NULL)"
        )
        val cursor = migrated.query("SELECT status, fileSizeBytes FROM offline_video WHERE shareRecordId = 1")
        assertTrue("Expected one row", cursor.moveToFirst())
        assertEquals("COMPLETE", cursor.getString(cursor.getColumnIndexOrThrow("status")))
        assertEquals(42L, cursor.getLong(cursor.getColumnIndexOrThrow("fileSizeBytes")))
        cursor.close()
    }

    @Test
    fun migrate6To7_addsTagsColumnWithDefaultEmptyArray() {
        val db = helper.createDatabase("migration_test_6_7", 6)
        db.execSQL(
            "INSERT INTO share_history (originalText, cleanedText, sharedAt, notes, sync_id, updated_at, source) " +
                "VALUES ('orig', 'clean', 12345, NULL, 'uuid-1', 12345, 'MOBILE')"
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(
            "migration_test_6_7", 7, true, ShareDatabase.MIGRATION_6_7
        )

        val cursor = migrated.query("SELECT tags FROM share_history WHERE sync_id = 'uuid-1'")
        assertTrue("Expected one row", cursor.moveToFirst())
        assertEquals("[]", cursor.getString(cursor.getColumnIndexOrThrow("tags")))
        cursor.close()
    }
}
