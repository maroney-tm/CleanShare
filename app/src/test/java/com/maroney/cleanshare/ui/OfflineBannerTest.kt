package com.maroney.cleanshare.ui

import com.maroney.cleanshare.sync.ConnectionStatus
import com.maroney.cleanshare.sync.ServerConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineBannerTest {

    private val host = "192.168.1.1"
    private val lastSeen = 1_000_000L

    @Test
    fun `returns null when Connected`() {
        assertNull(computeOfflineBannerTimestamp(
            status = ConnectionStatus.Connected(host, 8765),
            config = ServerConfig(manualHost = host, lastSeenAt = lastSeen),
        ))
    }

    @Test
    fun `returns null when Searching`() {
        assertNull(computeOfflineBannerTimestamp(
            status = ConnectionStatus.Searching,
            config = ServerConfig(manualHost = host, lastSeenAt = lastSeen),
        ))
    }

    @Test
    fun `returns null when Disconnected but no host configured`() {
        assertNull(computeOfflineBannerTimestamp(
            status = ConnectionStatus.Disconnected,
            config = ServerConfig(manualHost = null, lastSeenAt = lastSeen),
        ))
    }

    @Test
    fun `returns null when Disconnected but never connected before`() {
        assertNull(computeOfflineBannerTimestamp(
            status = ConnectionStatus.Disconnected,
            config = ServerConfig(manualHost = host, lastSeenAt = null),
        ))
    }

    @Test
    fun `returns lastSeenAt when Disconnected with host and prior connection`() {
        val result = computeOfflineBannerTimestamp(
            status = ConnectionStatus.Disconnected,
            config = ServerConfig(manualHost = host, lastSeenAt = lastSeen),
        )
        assertNotNull(result)
        assert(result == lastSeen)
    }
}
