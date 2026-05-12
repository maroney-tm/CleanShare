package com.maroney.cleanshare.data

data class ShareRecordWithMetadata(
    val record: ShareRecord,
    val metadata: LinkMetadata?,   // null = fetch pending
)
