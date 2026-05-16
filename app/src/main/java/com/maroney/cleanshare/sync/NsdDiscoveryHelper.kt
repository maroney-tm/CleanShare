package com.maroney.cleanshare.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val SERVICE_TYPE = "_cleanshare._tcp"

class NsdDiscoveryHelper(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    /**
     * Discovers the first _cleanshare._tcp service on the LAN.
     * Returns (host, port) or null if none found within [timeoutMs].
     */
    suspend fun discover(timeoutMs: Long = 5_000L): Pair<String, Int>? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Pair<String, Int>?> { cont ->
                val discoveryListener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {}
                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    override fun onServiceLost(service: NsdServiceInfo) {}
                    override fun onServiceFound(service: NsdServiceInfo) {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(s: NsdServiceInfo, errorCode: Int) {
                                // Ignore — keep waiting for other services
                            }
                            override fun onServiceResolved(s: NsdServiceInfo) {
                                val host = s.host?.hostAddress ?: return
                                if (cont.isActive) cont.resume(host to s.port)
                            }
                        })
                    }
                }

                nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

                cont.invokeOnCancellation {
                    try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
                }
            }
        }
}
