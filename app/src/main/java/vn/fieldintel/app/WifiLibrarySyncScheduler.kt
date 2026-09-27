package vn.fieldintel.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Runs library sync only on validated Wi‑Fi and never overlaps two sync jobs. */
class WifiLibrarySyncScheduler(
    context: Context,
    private val scope: CoroutineScope,
    private val sync: suspend () -> Unit,
) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val running = AtomicBoolean(false)
    private var loop: Job? = null
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { startLoop() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (isWifi(caps)) startLoop() else stopLoop()
        }
        override fun onLost(network: Network) { if (!wifiAvailable()) stopLoop() }
    }

    fun start() {
        connectivity?.registerDefaultNetworkCallback(callback)
        if (wifiAvailable()) startLoop()
    }

    fun stop() {
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
        stopLoop()
    }

    private fun startLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (wifiAvailable() && running.compareAndSet(false, true)) {
                    try { sync() } finally { running.set(false) }
                }
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private fun stopLoop() { loop?.cancel(); loop = null }

    private fun wifiAvailable(): Boolean {
        val network = connectivity?.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(network)?.let(::isWifi) == true
    }

    private fun isWifi(caps: NetworkCapabilities): Boolean =
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private companion object { const val SYNC_INTERVAL_MS = 6L * 60L * 60L * 1000L }
}
