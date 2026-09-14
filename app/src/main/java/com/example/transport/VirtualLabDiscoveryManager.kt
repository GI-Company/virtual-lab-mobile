package com.example.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

data class DiscoveredVirtualLab(
    val serviceName: String,
    val hostAddress: String,
    val port: Int,
    val path: String = "/sensors",
    val protocol: String = "1",
    val wsUrl: String,
    val attributes: Map<String, String> = emptyMap()
)

sealed class DiscoveryState {
    object Idle : DiscoveryState()
    object Searching : DiscoveryState()
    data class Found(val devices: List<DiscoveredVirtualLab>) : DiscoveryState()
    object Empty : DiscoveryState()
    data class Error(val message: String) : DiscoveryState()
}

class VirtualLabDiscoveryManager(private val context: Context) {
    companion object {
        private const val TAG = "VirtualLabDiscovery"
        const val SERVICE_TYPE = "_virtuallab._tcp"
        private const val EMPTY_TIMEOUT_MS = 6000L
    }

    private val nsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var resolveChannel: Channel<NsdServiceInfo>? = null
    private var resolverJob: Job? = null
    private var timeoutJob: Job? = null

    private var multicastLock: WifiManager.MulticastLock? = null
    private var isDiscovering = false
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val discoveredMap = ConcurrentHashMap<String, DiscoveredVirtualLab>()

    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    fun startDiscovery() {
        if (isDiscovering) {
            Log.d(TAG, "Discovery already active")
            return
        }

        if (nsdManager == null) {
            _discoveryState.value = DiscoveryState.Error("Network Service Discovery is not available on this device")
            return
        }

        // Acquire multicast lock if available
        acquireMulticastLock()

        discoveredMap.clear()
        _discoveryState.value = DiscoveryState.Searching

        // Start timeout timer for showing Empty state if nothing discovered
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(EMPTY_TIMEOUT_MS)
            if (isDiscovering && discoveredMap.isEmpty()) {
                _discoveryState.value = DiscoveryState.Empty
            }
        }

        // Setup sequential resolution worker
        startResolutionWorker()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started for: $regType")
                isDiscovering = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName} (${serviceInfo.serviceType})")
                val serviceType = serviceInfo.serviceType ?: ""
                if (serviceType.contains("_virtuallab._tcp", ignoreCase = true)) {
                    // Queue for serialized resolution
                    resolveChannel?.trySend(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                discoveredMap.remove(serviceInfo.serviceName)
                updateDiscoveryState()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Service discovery stopped")
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed with error: $errorCode")
                isDiscovering = false
                _discoveryState.value = DiscoveryState.Error("Discovery start failed (code: $errorCode)")
                releaseMulticastLock()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed with error: $errorCode")
                isDiscovering = false
                releaseMulticastLock()
            }
        }

        discoveryListener = listener

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting service discovery", e)
            _discoveryState.value = DiscoveryState.Error(e.message ?: "Failed to start discovery")
            releaseMulticastLock()
        }
    }

    fun stopDiscovery() {
        timeoutJob?.cancel()
        timeoutJob = null

        resolverJob?.cancel()
        resolverJob = null
        resolveChannel?.close()
        resolveChannel = null

        val listener = discoveryListener
        if (listener != null && isDiscovering && nsdManager != null) {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Exception stopping discovery: ${e.message}")
            }
        }
        discoveryListener = null
        isDiscovering = false
        releaseMulticastLock()
    }

    fun restartDiscovery() {
        stopDiscovery()
        startDiscovery()
    }

    private fun startResolutionWorker() {
        resolveChannel = Channel(Channel.UNLIMITED)
        resolverJob = scope.launch {
            for (serviceInfo in resolveChannel!!) {
                resolveServiceSynchronously(serviceInfo)
            }
        }
    }

    private suspend fun resolveServiceSynchronously(serviceInfo: NsdServiceInfo) {
        val manager = nsdManager ?: return
        val completion = CompletableDeferred<NsdServiceInfo?>()

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: code $errorCode")
                completion.complete(null)
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                Log.d(TAG, "Resolved service: ${resolvedInfo.serviceName} at ${resolvedInfo.host}:${resolvedInfo.port}")
                completion.complete(resolvedInfo)
            }
        }

        try {
            manager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling resolveService", e)
            completion.complete(null)
        }

        val resolved = completion.await()
        if (resolved != null) {
            handleResolvedService(resolved)
        }
    }

    private fun handleResolvedService(resolved: NsdServiceInfo) {
        val host = resolved.host?.hostAddress ?: return
        val port = resolved.port
        val serviceName = resolved.serviceName ?: "VirtualLab Desktop"

        // Parse attributes/TXT records
        val rawAttributes = resolved.attributes ?: emptyMap()
        val stringAttributes = mutableMapOf<String, String>()
        for ((key, bytes) in rawAttributes) {
            stringAttributes[key] = String(bytes, StandardCharsets.UTF_8)
        }

        val pathAttr = stringAttributes["path"] ?: "/sensors"
        val path = if (pathAttr.startsWith("/")) pathAttr else "/$pathAttr"
        val protocol = stringAttributes["protocol"] ?: "1"

        val wsUrl = "ws://$host:$port$path"

        val device = DiscoveredVirtualLab(
            serviceName = serviceName,
            hostAddress = host,
            port = port,
            path = path,
            protocol = protocol,
            wsUrl = wsUrl,
            attributes = stringAttributes
        )

        discoveredMap[serviceName] = device
        updateDiscoveryState()
    }

    private fun updateDiscoveryState() {
        val list = discoveredMap.values.toList()
        if (list.isNotEmpty()) {
            _discoveryState.value = DiscoveryState.Found(list)
        } else if (isDiscovering) {
            // Still discovering but no devices at the moment
            // Don't override if already Empty or Searching
            if (_discoveryState.value !is DiscoveryState.Searching && _discoveryState.value !is DiscoveryState.Empty) {
                _discoveryState.value = DiscoveryState.Empty
            }
        } else {
            _discoveryState.value = DiscoveryState.Idle
        }
    }

    private fun acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("VirtualLabSensorNode_mDNS")
                multicastLock?.setReferenceCounted(true)
            }
            multicastLock?.let {
                if (!it.isHeld) {
                    it.acquire()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire multicast lock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to release multicast lock: ${e.message}")
        }
    }
}
