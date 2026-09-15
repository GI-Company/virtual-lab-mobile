package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.acquisition.DiscoveredSensorMeta
import com.example.acquisition.LiveSensorReading
import com.example.acquisition.SensorAcquisitionManager
import com.example.acquisition.SensorTypeClass
import com.example.identity.DeviceIdentityManager
import com.example.identity.DeviceMetadata
import com.example.session.MeasurementPacket
import com.example.transport.ConnectionState
import com.example.transport.DiscoveredVirtualLab
import com.example.transport.DiscoveryState
import com.example.transport.LocalNetworkPermissionManager
import com.example.transport.NetworkDiagnostics
import com.example.transport.NetworkDiagnosticsManager
import com.example.transport.VirtualLabDiscoveryManager
import com.example.transport.WebSocketClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
    }

    val permissionManager = LocalNetworkPermissionManager(application)
    val identityManager = DeviceIdentityManager(application)
    val sensorManager = SensorAcquisitionManager(application)
    val discoveryManager = VirtualLabDiscoveryManager(application, permissionManager)
    val networkDiagnosticsManager = NetworkDiagnosticsManager(application, permissionManager)
    val webSocketClient = WebSocketClient()

    val deviceMetadata: DeviceMetadata = identityManager.metadata
    val deviceId: String = identityManager.deviceId

    val discoveryState: StateFlow<DiscoveryState> = discoveryManager.discoveryState
    val networkDiagnostics: StateFlow<NetworkDiagnostics> = networkDiagnosticsManager.diagnostics
    val connectionLogs: StateFlow<List<String>> = webSocketClient.connectionLogs

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedEndpoint = MutableStateFlow<String?>(null)
    val connectedEndpoint: StateFlow<String?> = _connectedEndpoint.asStateFlow()

    private val _showLocalNetworkRestrictionWarning = MutableStateFlow(false)
    val showLocalNetworkRestrictionWarning: StateFlow<Boolean> = _showLocalNetworkRestrictionWarning.asStateFlow()

    val discoveredSensors: Map<SensorTypeClass, DiscoveredSensorMeta> = sensorManager.discoveredSensors
    val liveReadings: StateFlow<Map<SensorTypeClass, LiveSensorReading>> = sensorManager.liveReadings

    private val _selectedPreviewSensor = MutableStateFlow(
        discoveredSensors.keys.firstOrNull { discoveredSensors[it]?.isAvailable == true }
            ?: SensorTypeClass.MAGNETOMETER
    )
    val selectedPreviewSensor: StateFlow<SensorTypeClass> = _selectedPreviewSensor.asStateFlow()

    private val _selectedCaptureSensors = MutableStateFlow<Set<SensorTypeClass>>(
        setOf(
            SensorTypeClass.MAGNETOMETER,
            SensorTypeClass.ACCELEROMETER,
            SensorTypeClass.GYROSCOPE
        ).filter { sensorManager.isSensorAvailable(it) }.toSet()
    )
    val selectedCaptureSensors: StateFlow<Set<SensorTypeClass>> = _selectedCaptureSensors.asStateFlow()

    private val _sampleId = MutableStateFlow("SMP-001")
    val sampleId: StateFlow<String> = _sampleId.asStateFlow()

    private val _sessionId = MutableStateFlow("SES-0042")
    val sessionId: StateFlow<String> = _sessionId.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _streamedPacketsCount = MutableStateFlow(0L)
    val streamedPacketsCount: StateFlow<Long> = _streamedPacketsCount.asStateFlow()

    private var connectionJob: Job? = null
    private var packetForwardJob: Job? = null

    init {
        networkDiagnosticsManager.startMonitoring()
        sensorManager.startLivePreview(_selectedPreviewSensor.value)
        setupPacketForwarding()
    }

    private fun setupPacketForwarding() {
        packetForwardJob = sensorManager.packetStream.onEach { packet ->
            if (_isRecording.value && _connectionState.value == ConnectionState.Connected) {
                try {
                    val json = Json.encodeToString(packet)
                    val sent = webSocketClient.send(json)
                    if (sent) {
                        _streamedPacketsCount.value = _streamedPacketsCount.value + 1
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error serializing or sending packet: ${e.message}")
                }
            }
        }.launchIn(viewModelScope)
    }

    fun checkAndStartDiscovery() {
        if (!permissionManager.hasRequiredPermissions()) {
            discoveryManager.startDiscovery() // will set PermissionRequired state
        } else {
            discoveryManager.startDiscovery()
        }
        networkDiagnosticsManager.update()
    }

    fun onPermissionsGranted() {
        discoveryManager.restartDiscovery()
        networkDiagnosticsManager.update()
    }

    fun stopDiscovery() {
        discoveryManager.stopDiscovery()
    }

    fun retrySearch() {
        discoveryManager.restartDiscovery()
        networkDiagnosticsManager.update()
    }

    fun selectPreviewSensor(sensorClass: SensorTypeClass) {
        val old = _selectedPreviewSensor.value
        if (old != sensorClass) {
            if (!_isRecording.value || !_selectedCaptureSensors.value.contains(old)) {
                sensorManager.stopLivePreview(old)
            }
            _selectedPreviewSensor.value = sensorClass
            sensorManager.startLivePreview(sensorClass)
        }
    }

    fun toggleCaptureSensor(sensorClass: SensorTypeClass) {
        if (!sensorManager.isSensorAvailable(sensorClass)) return
        val current = _selectedCaptureSensors.value.toMutableSet()
        if (current.contains(sensorClass)) {
            current.remove(sensorClass)
        } else {
            current.add(sensorClass)
        }
        _selectedCaptureSensors.value = current
    }

    fun setSampleId(id: String) {
        _sampleId.value = id
    }

    fun setSessionId(id: String) {
        _sessionId.value = id
    }

    fun connect(url: String, discoveredLab: DiscoveredVirtualLab? = null) {
        connectionJob?.cancel()
        _connectedEndpoint.value = url
        if (discoveredLab != null) {
            networkDiagnosticsManager.updateDiscoveredService(discoveredLab)
        } else {
            networkDiagnosticsManager.updateManualUrl(url)
        }

        connectionJob = webSocketClient.connect(url).onEach { state ->
            _connectionState.value = state
            when (state) {
                is ConnectionState.Connected -> {
                    _showLocalNetworkRestrictionWarning.value = false
                }
                is ConnectionState.Error -> {
                    val isNearbyGranted = permissionManager.isNearbyWifiDevicesGranted()
                    val isLanTarget = !isUsbDevelopmentUrl(url)
                    val errorLower = state.message.lowercase()
                    val isTimeoutOrUnreachable = errorLower.contains("timeout") ||
                            errorLower.contains("timed out") ||
                            errorLower.contains("failed to connect") ||
                            errorLower.contains("unreach") ||
                            state.throwable is java.net.SocketTimeoutException ||
                            state.throwable is java.net.ConnectException

                    if (isNearbyGranted && isLanTarget && isTimeoutOrUnreachable) {
                        _showLocalNetworkRestrictionWarning.value = true
                    }
                }
                else -> {}
            }
            networkDiagnosticsManager.update()
        }.launchIn(viewModelScope)
    }

    fun dismissLocalNetworkRestrictionWarning() {
        _showLocalNetworkRestrictionWarning.value = false
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        webSocketClient.disconnect()
        _connectionState.value = ConnectionState.Disconnected
        _connectedEndpoint.value = null
        networkDiagnosticsManager.updateDiscoveredService(null)
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            _isRecording.value = false
            sensorManager.stopCapture()
            // Keep preview going for selected preview sensor
            sensorManager.startLivePreview(_selectedPreviewSensor.value)
        } else {
            _streamedPacketsCount.value = 0L
            _isRecording.value = true
            sensorManager.startCapture(
                selectedSensors = _selectedCaptureSensors.value,
                deviceId = deviceId,
                sessionId = _sessionId.value
            )
        }
    }

    fun isUsbDevelopmentUrl(url: String?): Boolean {
        if (url == null) return false
        val lower = url.lowercase()
        return lower.contains("127.0.0.1") || lower.contains("localhost")
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
        discoveryManager.stopDiscovery()
        sensorManager.release()
        networkDiagnosticsManager.stopMonitoring()
    }
}
