package com.maroney.cleanshare.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "share_history")
data class ShareRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalText: String,
    val cleanedText: String,
    val sharedAt: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: ShareSource = ShareSource.MOBILE,
)
