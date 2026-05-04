package com.maroney.androidsharesanitizer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ShareRecord::class],
    version = 1,
    exportSchema = false,
)
abstract class ShareDatabase : RoomDatabase() {

    abstract fun shareDao(): ShareDao

    companion object {
        @Volatile private var instance: ShareDatabase? = null

        fun getInstance(context: Context): ShareDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShareDatabase::class.java,
                    "share_history.db",
                ).build().also { instance = it }
            }
    }
}
