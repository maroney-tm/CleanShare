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
