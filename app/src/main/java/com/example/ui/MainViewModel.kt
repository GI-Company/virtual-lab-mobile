package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.acquisition.MagnetometerData
import com.example.acquisition.MagnetometerSource
import com.example.acquisition.SensorDiscovery
import com.example.session.MagnetometerValues
import com.example.session.MeasurementPacket
import com.example.session.SensorMetadata
import com.example.transport.ConnectionState
import com.example.transport.DiscoveredVirtualLab
import com.example.transport.DiscoveryState
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val webSocketClient = WebSocketClient()
    private val magnetometerSource = MagnetometerSource(application)
    private val sensorDiscovery = SensorDiscovery(application)
    val discoveryManager = VirtualLabDiscoveryManager(application)
    val networkDiagnosticsManager = NetworkDiagnosticsManager(application)

    val discoveryState: StateFlow<DiscoveryState> = discoveryManager.discoveryState
    val networkDiagnostics: StateFlow<NetworkDiagnostics> = networkDiagnosticsManager.diagnostics

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedEndpoint = MutableStateFlow<String?>(null)
    val connectedEndpoint: StateFlow<String?> = _connectedEndpoint.asStateFlow()

    private val _magnetometerData = MutableStateFlow<MagnetometerData?>(null)
    val magnetometerData: StateFlow<MagnetometerData?> = _magnetometerData.asStateFlow()

    private val _isMagnetometerAvailable = MutableStateFlow(sensorDiscovery.isMagnetometerAvailable())
    val isMagnetometerAvailable: StateFlow<Boolean> = _isMagnetometerAvailable.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private var sessionId = "SES-0042"
    private var deviceId = "ANDROID-001"
    private var recordingJob: Job? = null
    private var connectionJob: Job? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        networkDiagnosticsManager.startMonitoring()
    }

    fun startDiscovery() {
        discoveryManager.startDiscovery()
    }

    fun stopDiscovery() {
        discoveryManager.stopDiscovery()
    }

    fun retrySearch() {
        discoveryManager.restartDiscovery()
    }

    fun connect(url: String) {
        connectionJob?.cancel()
        _connectedEndpoint.value = url
        connectionJob = webSocketClient.connect(url).onEach { state ->
            _connectionState.value = state
            if (state is ConnectionState.Disconnected || state is ConnectionState.Error) {
                // Keep connectedEndpoint so user can retry or see what failed
            }
        }.launchIn(viewModelScope)
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        webSocketClient.disconnect()
        _connectionState.value = ConnectionState.Disconnected
        _connectedEndpoint.value = null
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            _isRecording.value = false
            recordingJob?.cancel()
            recordingJob = null
        } else {
            _isRecording.value = true
            startRecording()
        }
    }

    private fun startRecording() {
        recordingJob?.cancel()
        recordingJob = magnetometerSource.startListening().onEach { data ->
            _magnetometerData.value = data
            if (_isRecording.value && _connectionState.value == ConnectionState.Connected) {
                sendPacket(data)
            }
        }.launchIn(viewModelScope)
    }

    private fun sendPacket(data: MagnetometerData) {
        val nowUtc = dateFormat.format(Date())

        val packet = MeasurementPacket(
            deviceId = deviceId,
            sessionId = sessionId,
            measurementType = "MAGNETIC_FIELD",
            timestampMonotonicNs = data.timestamp,
            timestampUtc = nowUtc,
            sensor = SensorMetadata(
                name = data.sensorName,
                vendor = data.sensorVendor,
                androidType = data.androidType,
                accuracy = data.accuracy
            ),
            values = MagnetometerValues(
                xUt = data.x,
                yUt = data.y,
                zUt = data.z
            )
        )

        val jsonString = Json.encodeToString(packet)
        webSocketClient.send(jsonString)
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
        discoveryManager.stopDiscovery()
        networkDiagnosticsManager.stopMonitoring()
    }
}
