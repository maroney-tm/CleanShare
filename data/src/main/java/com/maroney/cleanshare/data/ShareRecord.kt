package com.maroney.cleanshare.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "share_history")
data class ShareRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalText: String,
    val cleanedText: String,
    val sharedAt: Long = System.currentTimeMillis(),
    val notes: String? = null,
)
