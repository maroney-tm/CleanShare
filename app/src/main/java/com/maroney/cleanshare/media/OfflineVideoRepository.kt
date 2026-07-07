package com.maroney.cleanshare.media

import android.content.Context
import com.maroney.cleanshare.data.OfflineVideoDao
import com.maroney.cleanshare.data.OfflineVideoRecord
import com.maroney.cleanshare.data.OfflineVideoStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Downloads a full local copy of a video so it's watchable offline. Stored under
 * [Context.getFilesDir] — deliberately not [Context.getCacheDir], which is where
 * [VideoCacheManager]'s LRU streaming cache lives — so saved-offline videos are never
 * evicted to make room for the streaming cache and never count against its size limit.
 */
class OfflineVideoRepository(
    private val context: Context,
    private val dao: OfflineVideoDao,
    private val okHttpClient: OkHttpClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    fun observeById(shareRecordId: Long): Flow<OfflineVideoRecord?> = dao.observeById(shareRecordId)

    fun observeAll(): Flow<List<OfflineVideoRecord>> = dao.observeAll()

    /** Fire-and-forget: downloads [videoUrl] in the background, updating the DB row as it
     * progresses so observers (e.g. the detail screen) can reflect DOWNLOADING/COMPLETE/FAILED. */
    fun saveOffline(shareRecordId: Long, videoUrl: String) {
        scope.launch {
            dao.upsert(OfflineVideoRecord(shareRecordId, status = OfflineVideoStatus.DOWNLOADING))
            val destFile = offlineFile(shareRecordId)
            try {
                destFile.parentFile?.mkdirs()
                val request = Request.Builder().url(videoUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body.byteStream().use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                dao.upsert(
                    OfflineVideoRecord(
                        shareRecordId = shareRecordId,
                        status = OfflineVideoStatus.COMPLETE,
                        localFilePath = destFile.absolutePath,
                        fileSizeBytes = destFile.length(),
                    )
                )
            } catch (e: IOException) {
                Timber.e(e, "Failed to save video offline for share $shareRecordId")
                destFile.delete()
                dao.upsert(
                    OfflineVideoRecord(shareRecordId, status = OfflineVideoStatus.FAILED, errorMessage = e.message)
                )
            }
        }
    }

    /** Deletes the local file and its DB row. Safe to call even if nothing was ever saved. */
    fun removeOffline(shareRecordId: Long) {
        scope.launch {
            offlineFile(shareRecordId).delete()
            dao.deleteByShareRecordId(shareRecordId)
        }
    }

    private fun offlineFile(shareRecordId: Long): File =
        File(File(context.filesDir, "offline_videos"), "$shareRecordId.mp4")
}
