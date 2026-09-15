package com.example.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

data class NetworkDiagnostics(
    val androidVersion: String = Build.VERSION.RELEASE ?: "Unknown",
    val sdkLevel: Int = Build.VERSION.SDK_INT,
    val targetSdk: Int = 36,
    val isWifiConnected: Boolean = false,
    val localIp: String? = null,
    val gateway: String? = null,
    val nearbyWifiDevicesStatus: String = "GRANTED",
    val accessLocalNetworkStatus: String = "NOT APPLICABLE (API 36)",
    val isNsdAvailable: Boolean = true,
    val resolvedVirtualLab: String? = null,
    val exactWsUrl: String? = null
)

class NetworkDiagnosticsManager(
    private val context: Context,
    private val permissionManager: LocalNetworkPermissionManager
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val nsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val _diagnostics = MutableStateFlow(getCurrentDiagnostics())
    val diagnostics: StateFlow<NetworkDiagnostics> = _diagnostics.asStateFlow()

    private var isMonitoring = false
    private var lastDiscovered: DiscoveredVirtualLab? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            update()
        }

        override fun onLost(network: Network) {
            update()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            update()
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            update()
        }
    }

    fun startMonitoring() {
        if (isMonitoring) return
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            isMonitoring = true
        } catch (_: Exception) {
            // Fallback if registering specific request fails
        }
        update()
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
            // Ignored
        } finally {
            isMonitoring = false
        }
    }

    fun updateDiscoveredService(discovered: DiscoveredVirtualLab?) {
        lastDiscovered = discovered
        update()
    }

    fun updateManualUrl(url: String?) {
        if (lastDiscovered == null && !url.isNullOrBlank()) {
            val uriRegex = Regex("^ws://([^:/]+)(?::([0-9]+))?(.*)$")
            val match = uriRegex.find(url)
            if (match != null) {
                val host = match.groupValues[1]
                val port = match.groupValues[2].ifEmpty { "8765" }
                val path = match.groupValues[3].ifEmpty { "/sensors" }
                lastDiscovered = DiscoveredVirtualLab(
                    serviceName = "VirtualLab (Manual)",
                    hostAddress = host,
                    port = port.toIntOrNull() ?: 8765,
                    path = path,
                    wsUrl = url
                )
            }
        }
        update()
    }

    fun update() {
        _diagnostics.value = getCurrentDiagnostics()
    }

    private fun getCurrentDiagnostics(): NetworkDiagnostics {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val linkProperties = activeNetwork?.let { connectivityManager.getLinkProperties(it) }

        var localIp: String? = null
        var gateway: String? = null

        if (linkProperties != null) {
            localIp = linkProperties.linkAddresses
                .map { it.address }
                .filterIsInstance<Inet4Address>()
                .firstOrNull()?.hostAddress

            gateway = linkProperties.routes
                .filter { it.isDefaultRoute || it.destination.prefixLength == 0 }
                .mapNotNull { it.gateway }
                .filterIsInstance<Inet4Address>()
                .firstOrNull()?.hostAddress
        }

        if (localIp == null && isWifi) {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isUp && !networkInterface.isLoopback) {
                        val addresses = networkInterface.inetAddresses
                        while (addresses.hasMoreElements()) {
                            val addr = addresses.nextElement()
                            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                                localIp = addr.hostAddress
                                break
                            }
                        }
                    }
                    if (localIp != null) break
                }
            } catch (_: Exception) {}
        }

        val nearbyPerm = permissionManager.getNearbyWifiDevicesStatusString()
        val localNetPerm = permissionManager.getAccessLocalNetworkStatusString()

        val discovered = lastDiscovered
        val resolvedLab = if (discovered != null) "${discovered.hostAddress}:${discovered.port}" else null

        return NetworkDiagnostics(
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            sdkLevel = Build.VERSION.SDK_INT,
            targetSdk = context.applicationInfo.targetSdkVersion,
            isWifiConnected = isWifi,
            localIp = localIp,
            gateway = gateway,
            nearbyWifiDevicesStatus = nearbyPerm,
            accessLocalNetworkStatus = localNetPerm,
            isNsdAvailable = (nsdManager != null),
            resolvedVirtualLab = resolvedLab,
            exactWsUrl = discovered?.wsUrl
        )
    }
}
