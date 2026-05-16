package com.maroney.cleanshare

import com.maroney.cleanshare.data.Converters
import com.maroney.cleanshare.data.ShareSource
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ShareSourceConverterTest {
    private val converters = Converters()

    @Test
    fun roundTrip_mobile() {
        val stored = converters.fromShareSource(ShareSource.MOBILE)
        val restored = converters.toShareSource(stored)
        assertEquals(ShareSource.MOBILE, restored)
    }

    @Test
    fun roundTrip_desktop() {
        val stored = converters.fromShareSource(ShareSource.DESKTOP)
        val restored = converters.toShareSource(stored)
        assertEquals(ShareSource.DESKTOP, restored)
    }

    @Test
    fun unknownValueFallsBackToMobile() {
        assertEquals(ShareSource.MOBILE, converters.toShareSource("UNKNOWN_FUTURE_VALUE"))
    }
}
