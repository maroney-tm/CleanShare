package com.maroney.cleanshare.data

data class ShareRecordWithMetadata(
    val record: ShareRecord,
    val metadata: LinkMetadata?,      // null = fetch pending
    val ingestion: IngestionRecord? = null,  // null = server not configured or URL not supported
)
