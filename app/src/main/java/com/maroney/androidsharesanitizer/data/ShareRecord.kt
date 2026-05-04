package com.maroney.androidsharesanitizer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One entry in the share history.
 *
 * [originalText] — exactly what was in EXTRA_TEXT before cleaning.
 * [cleanedText]  — what was re-fired to the Sharesheet.
 * [sharedAt]     — epoch-millis timestamp recorded just before the re-fire.
 */
@Entity(tableName = "share_history")
data class ShareRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalText: String,
    val cleanedText: String,
    val sharedAt: Long = System.currentTimeMillis(),
)
