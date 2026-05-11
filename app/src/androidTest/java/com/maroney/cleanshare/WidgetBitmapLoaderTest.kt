package com.maroney.cleanshare

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maroney.cleanshare.widget.WidgetBitmapLoader
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class WidgetBitmapLoaderTest {

    private val server = MockWebServer()
    private val loader = WidgetBitmapLoader(OkHttpClient())

    @Before fun setUp() { server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun pngBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    @Test fun returnsNonNullBitmapOn200() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(pngBytes())))
        val result = loader.load(server.url("/img.png").toString())
        assertNotNull(result)
    }

    @Test fun returnsNullOn404() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = loader.load(server.url("/missing.png").toString())
        assertNull(result)
    }

    @Test fun returnsCachedBitmapOnSecondCall() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(pngBytes())))
        val url = server.url("/cached.png").toString()
        val first = loader.load(url)
        val second = loader.load(url)
        assertEquals(1, server.requestCount)
        assertNotNull(first)
        assertSame(first, second)
    }
}
