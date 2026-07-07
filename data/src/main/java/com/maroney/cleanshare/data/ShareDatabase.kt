package com.maroney.cleanshare.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ShareRecord::class, LinkMetadata::class, IngestionRecord::class, OfflineVideoRecord::class],
    version = 8,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ShareDatabase : RoomDatabase() {

    abstract fun shareDao(): ShareDao
    abstract fun linkMetadataDao(): LinkMetadataDao
    abstract fun ingestionDao(): IngestionDao
    abstract fun offlineVideoDao(): OfflineVideoDao

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

                // Backfill updated_at from sharedAt
                db.execSQL("UPDATE share_history SET updated_at = sharedAt WHERE updated_at = 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ingestion_record (
                        shareRecordId INTEGER NOT NULL PRIMARY KEY,
                        status TEXT NOT NULL,
                        errorMessage TEXT,
                        title TEXT,
                        uploader TEXT,
                        uploaderUrl TEXT,
                        description TEXT,
                        thumbnailUrl TEXT,
                        uploadDate TEXT,
                        duration INTEGER,
                        viewCount INTEGER,
                        likeCount INTEGER,
                        tags TEXT,
                        mediaType TEXT,
                        serverVideoPath TEXT
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS offline_video (
                        shareRecordId INTEGER NOT NULL PRIMARY KEY,
                        status TEXT NOT NULL,
                        localFilePath TEXT,
                        fileSizeBytes INTEGER NOT NULL,
                        savedAt INTEGER NOT NULL,
                        errorMessage TEXT
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE share_history ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ingestion_record ADD COLUMN thumbnailReady INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): ShareDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShareDatabase::class.java,
                    "share_history.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    // Only a safety net for genuine downgrades (e.g. sideloading an older
                    // build over a device that already ran a newer one during development) —
                    // there is no valid forward migration path for those, so without this Room
                    // crashes on launch instead of just recreating the local cache tables.
                    // Forward upgrades still go through the explicit migrations above and keep
                    // the user's data.
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
