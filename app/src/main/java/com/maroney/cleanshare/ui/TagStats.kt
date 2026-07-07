package com.maroney.cleanshare.ui

import com.maroney.cleanshare.data.ShareRecordWithMetadata

/** Every distinct tag currently in use across all entries, alphabetically. */
fun allTags(records: List<ShareRecordWithMetadata>): List<String> =
    records.flatMap { it.record.tags }.distinct().sorted()

/** The [limit] most-used tags across all entries, most-used first (ties broken alphabetically). */
fun topTags(records: List<ShareRecordWithMetadata>, limit: Int = 5): List<String> =
    records.flatMap { it.record.tags }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .map { it.key }
