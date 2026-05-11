package com.maroney.cleanshare

import com.maroney.cleanshare.ui.DetailRoute
import com.maroney.cleanshare.ui.HistoryRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityBackStackTest {

    @Test fun `initialBackStack with null id returns history only`() {
        val result = initialBackStack(null)
        assertEquals(listOf(HistoryRoute), result)
    }

    @Test fun `initialBackStack with id returns history then detail`() {
        val result = initialBackStack(42L)
        assertEquals(listOf(HistoryRoute, DetailRoute(42L)), result)
    }
}
