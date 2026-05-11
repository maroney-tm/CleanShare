package com.maroney.cleanshare.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class WidgetBitmapLoader(private val httpClient: OkHttpClient) {

    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun load(url: String): Bitmap? {
        cache[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
                if (!response.isSuccessful) { response.close(); return@withContext null }
                val bytes = response.body?.bytes() ?: return@withContext null
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@withContext null
                cache.put(url, bitmap)
                bitmap
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }
}
