package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.acquisition.DiscoveredSensorMeta
import com.example.acquisition.LiveSensorReading
import com.example.acquisition.SensorAcquisitionManager
import com.example.acquisition.SensorTypeClass
import com.example.camera.CameraAcquisitionManager
import com.example.camera.CameraHardwareInventory
import com.example.camera.CameraMode
import com.example.camera.CameraPermissionManager
import com.example.camera.CameraPermissionState
import com.example.camera.CameraWebSocketClient
import com.example.camera.ConcurrentCameraGroup
import com.example.camera.DeviceThermalMonitor
import com.example.camera.DiscoveredCamera
import com.example.camera.LiveCameraStats
import com.example.camera.ScientificCapturedFrame
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

    // Managers
    val permissionManager = LocalNetworkPermissionManager(application)
    val identityManager = DeviceIdentityManager(application)
    val sensorManager = SensorAcquisitionManager(application)
    val discoveryManager = VirtualLabDiscoveryManager(application, permissionManager)
    val networkDiagnosticsManager = NetworkDiagnosticsManager(application, permissionManager)
    val webSocketClient = WebSocketClient()

    // Camera Subsystem Managers
    val cameraPermissionManager = CameraPermissionManager(application)
    val thermalMonitor = DeviceThermalMonitor(application)
    val cameraInventory = CameraHardwareInventory(application)
    val cameraAcquisition = CameraAcquisitionManager(application, cameraPermissionManager, thermalMonitor)
    val cameraWebSocketClient = CameraWebSocketClient()

    val deviceMetadata: DeviceMetadata = identityManager.metadata
    val deviceId: String = identityManager.deviceId

    // Network & Discovery State
    val discoveryState: StateFlow<DiscoveryState> = discoveryManager.discoveryState
    val networkDiagnostics: StateFlow<NetworkDiagnostics> = networkDiagnosticsManager.diagnostics
    val connectionLogs: StateFlow<List<String>> = webSocketClient.connectionLogs

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedEndpoint = MutableStateFlow<String?>(null)
    val connectedEndpoint: StateFlow<String?> = _connectedEndpoint.asStateFlow()

    private val _showLocalNetworkRestrictionWarning = MutableStateFlow(false)
    val showLocalNetworkRestrictionWarning: StateFlow<Boolean> = _showLocalNetworkRestrictionWarning.asStateFlow()

    // Sensor Manager State
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

    // ==========================================
    // Camera Subsystem State
    // ==========================================
    private val _cameraPermissionState = MutableStateFlow(cameraPermissionManager.getPermissionState())
    val cameraPermissionState: StateFlow<CameraPermissionState> = _cameraPermissionState.asStateFlow()

    private val _discoveredCameras = MutableStateFlow<List<DiscoveredCamera>>(emptyList())
    val discoveredCameras: StateFlow<List<DiscoveredCamera>> = _discoveredCameras.asStateFlow()

    private val _concurrentCameraGroups = MutableStateFlow<List<ConcurrentCameraGroup>>(emptyList())
    val concurrentCameraGroups: StateFlow<List<ConcurrentCameraGroup>> = _concurrentCameraGroups.asStateFlow()

    private val _selectedCamera = MutableStateFlow<DiscoveredCamera?>(null)
    val selectedCamera: StateFlow<DiscoveredCamera?> = _selectedCamera.asStateFlow()

    private val _cameraMode = MutableStateFlow(CameraMode.SINGLE)
    val cameraMode: StateFlow<CameraMode> = _cameraMode.asStateFlow()

    private val _selectedConcurrentGroup = MutableStateFlow<ConcurrentCameraGroup?>(null)
    val selectedConcurrentGroup: StateFlow<ConcurrentCameraGroup?> = _selectedConcurrentGroup.asStateFlow()

    val isCameraStreaming: StateFlow<Boolean> = cameraAcquisition.isStreaming
    val liveCameraStats: StateFlow<LiveCameraStats> = cameraAcquisition.liveStats
    val previewBitmap: StateFlow<Bitmap?> = cameraAcquisition.previewBitmap
    val lastCapturedFrame: StateFlow<ScientificCapturedFrame?> = cameraAcquisition.lastCapturedFrame
    val cameraErrorMessage: StateFlow<String?> = cameraAcquisition.cameraErrorMessage
    val cameraWsLogs: StateFlow<List<String>> = cameraWebSocketClient.connectionLogs

    private val _cameraWsState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val cameraWsState: StateFlow<ConnectionState> = _cameraWsState.asStateFlow()

    private var connectionJob: Job? = null
    private var cameraWsJob: Job? = null
    private var packetForwardJob: Job? = null
    private var cameraFrameForwardJob: Job? = null

    init {
        networkDiagnosticsManager.startMonitoring()
        thermalMonitor.start()
        sensorManager.startLivePreview(_selectedPreviewSensor.value)
        setupPacketForwarding()
        setupCameraFrameForwarding()

        if (cameraPermissionManager.isCameraPermissionGranted()) {
            refreshCameraInventory()
        }
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
                    Log.e(TAG, "Error serializing or sending sensor packet: ${e.message}")
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun setupCameraFrameForwarding() {
        cameraFrameForwardJob = cameraAcquisition.frameFlow.onEach { (meta, bytes) ->
            if (cameraWebSocketClient.isConnected()) {
                cameraWebSocketClient.sendBinaryFrame(meta, bytes)
            }
        }.launchIn(viewModelScope)
    }

    // Network Discovery
    fun checkAndStartDiscovery() {
        discoveryManager.startDiscovery()
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

    // Sensors
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

    // WebSocket / Connection
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
                    // Also connect dedicated camera transport if camera streaming is active
                    connectCameraWsIfAppropriate(url)
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

    private fun deriveCameraWsUrl(sensorWsUrl: String): String {
        return if (sensorWsUrl.contains("/sensors")) {
            sensorWsUrl.replace("/sensors", "/camera")
        } else {
            sensorWsUrl.trimEnd('/') + "/camera"
        }
    }

    private fun connectCameraWsIfAppropriate(sensorUrl: String) {
        val cameraUrl = deriveCameraWsUrl(sensorUrl)
        cameraWsJob?.cancel()
        cameraWsJob = cameraWebSocketClient.connect(cameraUrl).onEach { state ->
            _cameraWsState.value = state
        }.launchIn(viewModelScope)
    }

    fun dismissLocalNetworkRestrictionWarning() {
        _showLocalNetworkRestrictionWarning.value = false
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        cameraWsJob?.cancel()
        cameraWsJob = null
        webSocketClient.disconnect()
        cameraWebSocketClient.disconnect()
        _connectionState.value = ConnectionState.Disconnected
        _cameraWsState.value = ConnectionState.Disconnected
        _connectedEndpoint.value = null
        networkDiagnosticsManager.updateDiscoveredService(null)
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            _isRecording.value = false
            sensorManager.stopCapture()
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

    // ==========================================
    // Camera Subsystem Methods
    // ==========================================
    fun onCameraPermissionResult(granted: Boolean) {
        if (granted) {
            _cameraPermissionState.value = CameraPermissionState.GRANTED
            refreshCameraInventory()
        } else {
            _cameraPermissionState.value = CameraPermissionState.DENIED
            _discoveredCameras.value = emptyList()
            _concurrentCameraGroups.value = emptyList()
            _selectedCamera.value = null
        }
    }

    fun refreshCameraInventory() {
        if (!cameraPermissionManager.isCameraPermissionGranted()) {
            _cameraPermissionState.value = CameraPermissionState.DENIED
            return
        }
        _cameraPermissionState.value = CameraPermissionState.GRANTED
        val cameras = cameraInventory.discoverCameras()
        _discoveredCameras.value = cameras

        val concurrent = cameraInventory.getConcurrentCameraCombinations()
        _concurrentCameraGroups.value = concurrent

        // Auto select first streamable camera if none selected
        if (_selectedCamera.value == null || !cameras.any { it.id == _selectedCamera.value?.id }) {
            _selectedCamera.value = cameras.firstOrNull { it.parentLogicalCameraId == null } ?: cameras.firstOrNull()
        }

        if (_selectedConcurrentGroup.value == null && concurrent.isNotEmpty()) {
            _selectedConcurrentGroup.value = concurrent.firstOrNull()
        }
    }

    fun selectCamera(camera: DiscoveredCamera) {
        if (_selectedCamera.value?.id != camera.id) {
            val wasStreaming = isCameraStreaming.value
            if (wasStreaming) {
                cameraAcquisition.stopCameraStream()
            }
            _selectedCamera.value = camera
            if (wasStreaming) {
                cameraAcquisition.startCameraStream(camera, deviceId)
            }
        }
    }

    fun setCameraMode(mode: CameraMode) {
        _cameraMode.value = mode
    }

    fun selectConcurrentGroup(group: ConcurrentCameraGroup) {
        _selectedConcurrentGroup.value = group
    }

    fun startCameraStream() {
        val camera = _selectedCamera.value ?: run {
            cameraAcquisition.startCameraStream(
                DiscoveredCamera(
                    id = "0",
                    isLogical = false,
                    physicalCameraIds = emptyList(),
                    lensFacing = com.example.camera.CameraFacing.BACK,
                    focalLengths = emptyList(),
                    sensorPhysicalSizeMm = null,
                    pixelArraySize = null,
                    activeArraySize = null,
                    timestampSource = "UNKNOWN",
                    hardwareLevel = "UNKNOWN",
                    capabilities = emptyList(),
                    hasRaw = false,
                    hasLogicalMulti = false,
                    supportedResolutions = emptyList(),
                    supportedFpsRanges = emptyList(),
                    friendlyName = "Rear Camera",
                    mpClass = "Unknown MP",
                    maxStreamResolution = "1280x720",
                    status = "AVAILABLE",
                    concurrencyStatus = "INDIVIDUALLY STREAMABLE"
                ),
                deviceId
            )
            return
        }
        cameraAcquisition.startCameraStream(camera, deviceId)

        // If sensor websocket is connected, ensure camera ws transport is also connected
        _connectedEndpoint.value?.let { sensorUrl ->
            connectCameraWsIfAppropriate(sensorUrl)
        }
    }

    fun stopCameraStream() {
        cameraAcquisition.stopCameraStream()
    }

    fun captureScientificFrame() {
        cameraAcquisition.captureScientificFrame()
    }

    fun dismissCameraError() {
        cameraAcquisition.dismissError()
    }

    fun generateCameraDiagnosticsReport(): String {
        val sb = StringBuilder()
        sb.appendLine("=== VIRTUALLAB SENSORNODE CAMERA2 DIAGNOSTICS ===")
        sb.appendLine("Device ID: $deviceId")
        sb.appendLine("Android OS: ${networkDiagnostics.value.androidVersion} (API ${networkDiagnostics.value.sdkLevel})")
        sb.appendLine("Target SDK: ${networkDiagnostics.value.targetSdk}")
        sb.appendLine("Thermal Status: ${thermalMonitor.thermalStatus.value}")
        sb.appendLine("Camera Permission: ${_cameraPermissionState.value}")
        sb.appendLine()

        val cameras = _discoveredCameras.value
        sb.appendLine("DISCOVERED CAMERAS (${cameras.size}):")
        for (cam in cameras) {
            sb.appendLine("----------------------------------------")
            sb.appendLine("Camera2 ID: ${cam.id}")
            sb.appendLine("Friendly Name: ${cam.friendlyName}")
            sb.appendLine("Status: ${cam.status}")
            sb.appendLine("Concurrency Status: ${cam.concurrencyStatus}")
            sb.appendLine("Is Logical Multi-Camera: ${cam.isLogical}")
            if (cam.parentLogicalCameraId != null) {
                sb.appendLine("Parent Logical ID: ${cam.parentLogicalCameraId}")
            }
            if (cam.physicalCameraIds.isNotEmpty()) {
                sb.appendLine("Physical Member IDs: ${cam.physicalCameraIds.joinToString(", ")}")
            }
            sb.appendLine("Lens Facing: ${cam.lensFacing}")
            sb.appendLine("Focal Lengths (mm): ${cam.focalLengths.joinToString(", ")}")
            sb.appendLine("MP Class: ${cam.mpClass}")
            sb.appendLine("Pixel Array Size: ${cam.pixelArraySize?.let { "${it.first}x${it.second}" } ?: "Unknown"}")
            sb.appendLine("Active Array Size: ${cam.activeArraySize ?: "Unknown"}")
            sb.appendLine("Sensor Physical Size (mm): ${cam.sensorPhysicalSizeMm?.let { "${it.first} x ${it.second}" } ?: "Unknown"}")
            sb.appendLine("Timestamp Source: ${cam.timestampSource}")
            sb.appendLine("Hardware Level: ${cam.hardwareLevel}")
            sb.appendLine("RAW Capability: ${if (cam.hasRaw) "SUPPORTED" else "UNAVAILABLE"}")
            sb.appendLine("Logical Multi-Camera: ${if (cam.hasLogicalMulti) "SUPPORTED" else "UNAVAILABLE"}")
            sb.appendLine("Max Stream Resolution: ${cam.maxStreamResolution}")
            sb.appendLine("Supported FPS Ranges: ${cam.supportedFpsRanges.joinToString(", ") { "[${it.first}, ${it.second}]" }}")
            sb.appendLine("Capabilities: ${cam.capabilities.joinToString(", ")}")
            sb.appendLine("Supported JPEG Sizes (sample): ${cam.supportedResolutions.take(6).joinToString(", ") { "${it.first}x${it.second}" }}")
        }

        sb.appendLine()
        val concurrent = _concurrentCameraGroups.value
        sb.appendLine("CONCURRENT CAMERA COMBINATIONS (${concurrent.size}):")
        if (concurrent.isEmpty()) {
            sb.appendLine("No concurrent camera combinations reported by CameraManager (HAL does not support concurrent streaming).")
        } else {
            for (g in concurrent) {
                sb.appendLine(" - ${g.description} (Supported: ${g.isSupported})")
            }
        }

        sb.appendLine()
        sb.appendLine("=== END CAMERA DIAGNOSTICS ===")
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
        discoveryManager.stopDiscovery()
        sensorManager.release()
        cameraAcquisition.release()
        thermalMonitor.stop()
        networkDiagnosticsManager.stopMonitoring()
    }
}
