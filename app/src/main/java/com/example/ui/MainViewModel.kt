package com.example.ui

import com.example.protocol.v1.BaseMessage
import com.example.protocol.v1.ProtocolSerializer

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
import com.example.camera.ControlWebSocketClient
import com.example.camera.ConcurrentCameraGroup
import com.example.camera.DeviceThermalMonitor
import com.example.camera.DiscoveredCamera
import com.example.camera.LiveCameraStats
import com.example.camera.ScientificCapturedFrame
import com.example.identity.DeviceIdentityManager
import com.example.identity.DeviceMetadata
import com.example.transport.ConnectionState
import com.example.protocol.v1.ChannelHelloMessage
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class TransportMode { LAN, USB_ADB }

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
    val cameraAcquisition = CameraAcquisitionManager(application, cameraPermissionManager, thermalMonitor, cameraInventory)
    val cameraWebSocketClient = CameraWebSocketClient()
    val controlWebSocketClient = ControlWebSocketClient()

    val deviceMetadata: DeviceMetadata = identityManager.metadata
    val deviceId: String = identityManager.deviceId

    // Transport Mode
    private val _transportMode = MutableStateFlow(TransportMode.LAN)
    val transportMode: StateFlow<TransportMode> = _transportMode.asStateFlow()

    private val _usbBaseUrl = MutableStateFlow<String?>("ws://127.0.0.1:8765")
    val usbBaseUrl: StateFlow<String?> = _usbBaseUrl.asStateFlow()

    fun setTransportModeFromIntent(modeStr: String?, baseUrl: String?) {
        val newMode = try {
            if (modeStr != null) TransportMode.valueOf(modeStr) else TransportMode.LAN
        } catch (e: Exception) {
            TransportMode.LAN
        }
        
        if (newMode == TransportMode.USB_ADB) {
            if (baseUrl != null && (baseUrl.startsWith("ws://") || baseUrl.startsWith("wss://"))) {
                _usbBaseUrl.value = baseUrl
            } else {
                _usbBaseUrl.value = "ws://127.0.0.1:8765"
            }
        }
        
        if (_transportMode.value != newMode || newMode == TransportMode.USB_ADB) {
            _transportMode.value = newMode
            disconnect()
            
            if (newMode == TransportMode.USB_ADB) {
                _usbBaseUrl.value?.let { connect(it) }
            }
        }
    }

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
    val controlWsLogs: StateFlow<List<String>> = controlWebSocketClient.connectionLogs

    private val _cameraWsState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val cameraWsState: StateFlow<ConnectionState> = _cameraWsState.asStateFlow()

    private val _controlWsState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val controlWsState: StateFlow<ConnectionState> = _controlWsState.asStateFlow()

    private var connectionJob: Job? = null
    private var cameraWsJob: Job? = null
    private var controlWsJob: Job? = null
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

        viewModelScope.launch {
            controlWebSocketClient.incomingMessages.collect { msg ->
                // device_id validation is now handled inside cameraAcquisition to properly unwrap the polymorphic type
                cameraAcquisition.processControlCommand(msg, deviceId) { responseJson: String ->
                    controlWebSocketClient.sendResponse(responseJson)
                }
            }
        }
    }

    private fun setupPacketForwarding() {
        packetForwardJob = sensorManager.packetStream.onEach { packet ->
            if (_connectionState.value is ConnectionState.Connected) {
                try {
                    val json = ProtocolSerializer.serialize(packet)
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
                val sent = cameraWebSocketClient.sendBinaryFrame(meta, bytes)
                if (!sent && meta.message_type == "CAMERA_PREVIEW_FRAME") {
                    cameraAcquisition.incrementDroppedFrames()
                }
            }
        }.launchIn(viewModelScope)
        
        viewModelScope.launch {
            cameraAcquisition.scientificFrameChannel.collect { (meta, bytes) ->
                if (cameraWebSocketClient.isConnected()) {
                    val sent = cameraWebSocketClient.sendBinaryFrame(meta, bytes)
                    if (sent) {
                        cameraAcquisition.markScientificFrameSent(meta.request_id)
                    } else {
                        cameraAcquisition.markScientificFrameFailed(meta.request_id, "TRANSPORT_QUEUE_FULL")
                    }
                } else {
                    cameraAcquisition.markScientificFrameFailed(meta.request_id, "TRANSPORT_DISCONNECTED")
                }
            }
        }
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
            if (!_selectedCaptureSensors.value.contains(old)) {
                sensorManager.stopLivePreview(old)
            }
            _selectedPreviewSensor.value = sensorClass
            sensorManager.startLivePreview(sensorClass)
        }
    }

    fun toggleCaptureSensor(sensorClass: SensorTypeClass) {
        val current = _selectedCaptureSensors.value.toMutableSet()
        if (current.contains(sensorClass)) {
            current.remove(sensorClass)
            sensorManager.stopCapture(setOf(sensorClass))
        } else {
            current.add(sensorClass)
            if (_connectionState.value is ConnectionState.Connected) {
                sensorManager.startCapture(setOf(sensorClass), deviceId, _sessionId.value)
            }
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
        val baseUrl = getBaseUrl(url)
        val sensorUrl = "$baseUrl/sensors"
        
        connectionJob?.cancel()
        _connectedEndpoint.value = sensorUrl
        if (discoveredLab != null) {
            networkDiagnosticsManager.updateDiscoveredService(discoveredLab)
        } else {
            networkDiagnosticsManager.updateManualUrl(url)
        }

        val sensorsHello = ProtocolSerializer.serialize(ChannelHelloMessage(device_id = deviceId, channel = "sensors"))
        connectionJob = webSocketClient.connect(sensorUrl, sensorsHello).onEach { state ->
            _connectionState.value = state
            when (state) {
                is ConnectionState.Connected -> {
                    _showLocalNetworkRestrictionWarning.value = false
                    sensorManager.startCapture(_selectedCaptureSensors.value, deviceId, _sessionId.value)
                }
                is ConnectionState.Error -> {
                    val isNearbyGranted = permissionManager.isNearbyWifiDevicesGranted()
                    val isLanTarget = !isUsbDevelopmentUrl(url)
                    val errorLower = state.message.lowercase()
                    val isTimeoutOrUnreachable = errorLower.contains("timeout") ||
                            errorLower.contains("timed out") ||
                            errorLower.contains("failed to connect") ||
                            errorLower.contains("unreach") ||
                            errorLower.contains("ehostunreach")

                    if (isLanTarget && isTimeoutOrUnreachable && !isNearbyGranted) {
                        _showLocalNetworkRestrictionWarning.value = true
                    }
                }
                else -> {}
            }
            networkDiagnosticsManager.update()
        }.launchIn(viewModelScope)

        // Connect the other channels immediately and independently
        connectCameraWsIfAppropriate(url)
        connectControlWsIfAppropriate(url)
    }


    private fun getBaseUrl(rawUrl: String): String {
        var base = rawUrl.trimEnd('/')
        if (base.endsWith("/sensors")) base = base.removeSuffix("/sensors")
        if (base.endsWith("/camera")) base = base.removeSuffix("/camera")
        if (base.endsWith("/control")) base = base.removeSuffix("/control")
        return base
    }


    private fun connectControlWsIfAppropriate(sensorUrl: String) {
        if (_controlWsState.value is ConnectionState.Connected || _controlWsState.value is ConnectionState.Connecting) return
        controlWsJob?.cancel()
        val controlUrl = "${getBaseUrl(sensorUrl)}/control"
        val controlHello = ProtocolSerializer.serialize(ChannelHelloMessage(device_id = deviceId, channel = "control"))
        val descriptor = ProtocolSerializer.serialize(com.example.protocol.v1.InstrumentDescriptorMessage(
            device_id = deviceId,
            manufacturer = android.os.Build.MANUFACTURER,
            model = android.os.Build.MODEL,
            software_version = "1.0"
        ))
        controlWsJob = controlWebSocketClient.connect(controlUrl, controlHello).onEach { state ->
            if (state is ConnectionState.Connected) {
                controlWebSocketClient.sendResponse(descriptor)
            }
            _controlWsState.value = state
            Log.d("MainViewModel", "Control WS State: $state")
        }.launchIn(viewModelScope)
    }

    private fun connectCameraWsIfAppropriate(sensorUrl: String) {
        if (_cameraWsState.value is ConnectionState.Connected || _cameraWsState.value is ConnectionState.Connecting) return
        val cameraUrl = "${getBaseUrl(sensorUrl)}/camera"
        cameraWsJob?.cancel()
        val cameraHello = ProtocolSerializer.serialize(ChannelHelloMessage(device_id = deviceId, channel = "camera"))
        cameraWsJob = cameraWebSocketClient.connect(cameraUrl, cameraHello).onEach { state ->
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
        controlWsJob?.cancel()
        controlWsJob = null
        webSocketClient.disconnect()
        cameraWebSocketClient.disconnect()
        controlWebSocketClient.disconnect()
        _connectionState.value = ConnectionState.Disconnected
        _cameraWsState.value = ConnectionState.Disconnected
        _controlWsState.value = ConnectionState.Disconnected
        _connectedEndpoint.value = null
        networkDiagnosticsManager.updateDiscoveredService(null)
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            _isRecording.value = false
        } else {
            _streamedPacketsCount.value = 0L
            _isRecording.value = true
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
        val camera = _selectedCamera.value
        if (camera == null) {
            Log.e(TAG, "CAMERA_UNAVAILABLE: No camera selected")
            return
        }

        cameraAcquisition.startCameraStream(camera, deviceId)

        // If sensor websocket is connected, ensure camera ws transport is also connected
        _connectedEndpoint.value?.let { sensorUrl ->
            connectCameraWsIfAppropriate(sensorUrl)
            connectControlWsIfAppropriate(sensorUrl)
        }
    }

    fun stopCameraStream() {
        cameraAcquisition.stopCameraStream()
    }

    fun captureScientificFrame() {
        viewModelScope.launch {
            cameraAcquisition.captureScientificFrame("MANUAL-" + java.util.UUID.randomUUID().toString().take(6), null) {}
        }
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
