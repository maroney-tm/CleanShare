package com.maroney.cleanshare.data

interface WorkScheduler {
    fun scheduleFetch(shareRecordId: Long, url: String)
}
