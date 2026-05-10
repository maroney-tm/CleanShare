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
    version = 3,
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

        fun getInstance(context: Context): ShareDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShareDatabase::class.java,
                    "share_history.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
