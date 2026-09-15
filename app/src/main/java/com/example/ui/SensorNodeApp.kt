package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.acquisition.DiscoveredSensorMeta
import com.example.acquisition.LiveSensorReading
import com.example.acquisition.SensorTypeClass
import com.example.camera.CameraMode
import com.example.camera.CameraPermissionState
import com.example.identity.DeviceMetadata
import com.example.transport.ConnectionState
import com.example.transport.DiscoveredVirtualLab
import com.example.transport.DiscoveryState
import com.example.transport.NetworkDiagnostics
import com.example.transport.PermissionStatus
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorNodeApp(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val connectionState by viewModel.connectionState.collectAsState()
    val connectedEndpoint by viewModel.connectedEndpoint.collectAsState()
    val discoveryState by viewModel.discoveryState.collectAsState()
    val networkDiagnostics by viewModel.networkDiagnostics.collectAsState()
    val connectionLogs by viewModel.connectionLogs.collectAsState()

    val liveReadings by viewModel.liveReadings.collectAsState()
    val selectedPreviewSensor by viewModel.selectedPreviewSensor.collectAsState()
    val selectedCaptureSensors by viewModel.selectedCaptureSensors.collectAsState()
    val sampleId by viewModel.sampleId.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val streamedPacketsCount by viewModel.streamedPacketsCount.collectAsState()
    val showLocalNetworkRestrictionWarning by viewModel.showLocalNetworkRestrictionWarning.collectAsState()

    // Camera Subsystem State
    val cameraPermissionState by viewModel.cameraPermissionState.collectAsState()
    val discoveredCameras by viewModel.discoveredCameras.collectAsState()
    val concurrentCameraGroups by viewModel.concurrentCameraGroups.collectAsState()
    val selectedCamera by viewModel.selectedCamera.collectAsState()
    val cameraMode by viewModel.cameraMode.collectAsState()
    val selectedConcurrentGroup by viewModel.selectedConcurrentGroup.collectAsState()
    val isCameraStreaming by viewModel.isCameraStreaming.collectAsState()
    val liveCameraStats by viewModel.liveCameraStats.collectAsState()
    val previewBitmap by viewModel.previewBitmap.collectAsState()
    val lastCapturedFrame by viewModel.lastCapturedFrame.collectAsState()
    val cameraErrorMessage by viewModel.cameraErrorMessage.collectAsState()
    val cameraWsState by viewModel.cameraWsState.collectAsState()
    val controlWsState by viewModel.controlWsState.collectAsState()

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onCameraPermissionResult(isGranted)
    }

    val requestCameraPermissionAction = {
        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results[android.Manifest.permission.CAMERA]?.let {
            viewModel.onCameraPermissionResult(it)
        }
        val allGranted = results.values.all { it }
        if (allGranted) {
            viewModel.onPermissionsGranted()
        } else {
            viewModel.checkAndStartDiscovery()
        }
    }

    DisposableEffect(Unit) {
        viewModel.checkAndStartDiscovery()
        onDispose {
            viewModel.stopDiscovery()
        }
    }

    val requestPermissionsAction = {
        val perms = viewModel.permissionManager.getPermissionsToRequest().toMutableList()
        if (!viewModel.cameraPermissionManager.isCameraPermissionGranted()) {
            perms.add(android.Manifest.permission.CAMERA)
        }
        if (perms.isNotEmpty()) {
            permissionsLauncher.launch(perms.toTypedArray())
        } else {
            viewModel.onPermissionsGranted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "SensorNode logo",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                "VIRTUAL LAB SENSORNODE",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "Scientific Acquisition Node",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Required Banner
            if (discoveryState is DiscoveryState.PermissionRequired ||
                networkDiagnostics.nearbyWifiDevicesStatus == "DENIED"
            ) {
                item {
                    PermissionRequiredBanner(onRequestAccess = requestPermissionsAction)
                }
            }

            // ANDROID LOCAL NETWORK RESTRICTION
            if (showLocalNetworkRestrictionWarning) {
                item {
                    LocalNetworkRestrictionCard(onDismiss = viewModel::dismissLocalNetworkRestrictionWarning)
                }
            }

            // NETWORK
            item {
                NetworkCard(
                    diagnostics = networkDiagnostics,
                    onRequestAccess = requestPermissionsAction
                )
            }

            // VIRTUAL LAB
            item {
                VirtualLabCard(
                    discoveryState = discoveryState,
                    connectionState = connectionState,
                    cameraWsState = cameraWsState,
                    controlWsState = controlWsState,
                    connectedEndpoint = connectedEndpoint,
                    isUsbDevMode = viewModel.isUsbDevelopmentUrl(connectedEndpoint),
                    onConnect = { device ->
                        viewModel.connect(device.wsUrl, device)
                    },
                    onDisconnect = viewModel::disconnect,
                    onRetrySearch = viewModel::retrySearch,
                    onRequestPermission = requestPermissionsAction
                )
            }

            // DEVICE
            item {
                DeviceCard(metadata = viewModel.deviceMetadata)
            }

            // CAMERA PERMISSION BANNER (if needed)
            if (cameraPermissionState != CameraPermissionState.GRANTED) {
                item {
                    CameraPermissionBanner(onRequestAccess = requestCameraPermissionAction)
                }
            }

            // CAMERAS INVENTORY
            item {
                CamerasInventoryCard(
                    discoveredCameras = discoveredCameras,
                    permissionState = cameraPermissionState,
                    onRequestPermission = requestCameraPermissionAction,
                    onRefresh = viewModel::refreshCameraInventory
                )
            }

            // LIVE CAMERA STREAM
            item {
                LiveCameraCard(
                    discoveredCameras = discoveredCameras,
                    selectedCamera = selectedCamera,
                    onSelectCamera = viewModel::selectCamera,
                    cameraMode = cameraMode,
                    onSelectCameraMode = viewModel::setCameraMode,
                    concurrentGroups = concurrentCameraGroups,
                    selectedConcurrentGroup = selectedConcurrentGroup,
                    onSelectConcurrentGroup = viewModel::selectConcurrentGroup,
                    isStreaming = isCameraStreaming,
                    onStartStream = viewModel::startCameraStream,
                    onStopStream = viewModel::stopCameraStream,
                    onCaptureFrame = viewModel::captureScientificFrame,
                    liveStats = liveCameraStats,
                    previewBitmap = previewBitmap,
                    lastCapturedFrame = lastCapturedFrame,
                    errorMessage = cameraErrorMessage,
                    onDismissError = viewModel::dismissCameraError,
                    isWsConnected = cameraWsState is ConnectionState.Connected
                )
            }

            // SENSORS
            item {
                SensorsCard(discoveredSensors = viewModel.discoveredSensors)
            }

            // LIVE
            item {
                LiveCard(
                    availableSensors = viewModel.discoveredSensors.filter { it.value.isAvailable }.keys.toList(),
                    selectedSensor = selectedPreviewSensor,
                    reading = liveReadings[selectedPreviewSensor],
                    onSelectSensor = viewModel::selectPreviewSensor
                )
            }

            // SESSION
            item {
                SessionCard(
                    sampleId = sampleId,
                    onSampleIdChange = viewModel::setSampleId,
                    availableSensors = viewModel.discoveredSensors,
                    selectedSensors = selectedCaptureSensors,
                    onToggleSensor = viewModel::toggleCaptureSensor,
                    isRecording = isRecording,
                    streamedCount = streamedPacketsCount,
                    isConnected = connectionState is ConnectionState.Connected,
                    onToggleRecording = viewModel::toggleRecording
                )
            }

            // ADVANCED
            item {
                AdvancedCard(
                    diagnostics = networkDiagnostics,
                    connectionLogs = connectionLogs,
                    cameraDiagnosticsReport = viewModel.generateCameraDiagnosticsReport(),
                    onCopyCameraDiagnostics = {
                        clipboardManager.setText(AnnotatedString(viewModel.generateCameraDiagnosticsReport()))
                        Toast.makeText(context, "Camera diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onConnectManual = { url ->
                        viewModel.connect(url)
                    },
                    isConnecting = connectionState is ConnectionState.Connecting
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// -------------------------------------------------------------
// PERMISSION REQUIRED BANNER
// -------------------------------------------------------------
@Composable
fun PermissionRequiredBanner(onRequestAccess: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "LOCAL NETWORK ACCESS REQUIRED",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                "SensorNode needs Nearby Devices permission to connect directly to VirtualLab Desktop on your local network.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onRequestAccess,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("grant_access_button")
            ) {
                Text("GRANT ACCESS")
            }
        }
    }
}

// -------------------------------------------------------------
// ANDROID LOCAL NETWORK RESTRICTION CARD
// -------------------------------------------------------------
@Composable
fun LocalNetworkRestrictionCard(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ANDROID LOCAL NETWORK RESTRICTION",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Text(
                "Local-network permission is granted, but direct LAN traffic is being blocked by the operating system.\n\nFor Android 16 development builds, Local Network Protection compatibility testing may be enabled.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "adb shell am compat disable RESTRICT_LOCAL_NETWORK com.aistudio.sensornode.vlsnxz",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// NETWORK CARD
// -------------------------------------------------------------
@Composable
fun NetworkCard(
    diagnostics: NetworkDiagnostics,
    onRequestAccess: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "NETWORK",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (diagnostics.isWifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = if (diagnostics.isWifiConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (diagnostics.isWifiConnected) "CONNECTED" else "DISCONNECTED",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (diagnostics.isWifiConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            MetricRow(label = "Android", value = "${diagnostics.androidVersion} / API ${diagnostics.sdkLevel}")
            MetricRow(label = "Target SDK", value = "${diagnostics.targetSdk}")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEARBY_WIFI_DEVICES",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (diagnostics.nearbyWifiDevicesStatus == "DENIED") {
                    TextButton(
                        onClick = onRequestAccess,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("REQUEST", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = diagnostics.nearbyWifiDevicesStatus,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = if (diagnostics.nearbyWifiDevicesStatus == "GRANTED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            MetricRow(label = "ACCESS_LOCAL_NETWORK", value = diagnostics.accessLocalNetworkStatus)
            MetricRow(label = "NSD Discovery", value = if (diagnostics.isNsdAvailable) "AVAILABLE" else "UNAVAILABLE")
            MetricRow(label = "Wi-Fi", value = if (diagnostics.isWifiConnected) "CONNECTED" else "DISCONNECTED")
            MetricRow(label = "Device IP", value = diagnostics.localIp ?: "UNAVAILABLE")
            MetricRow(label = "Gateway", value = diagnostics.gateway ?: "UNAVAILABLE")
            MetricRow(label = "Resolved VirtualLab", value = diagnostics.resolvedVirtualLab ?: "NONE")
            MetricRow(label = "WebSocket URL", value = diagnostics.exactWsUrl ?: "NONE")
        }
    }
}

// -------------------------------------------------------------
// VIRTUAL LAB CARD
// -------------------------------------------------------------
@Composable
fun VirtualLabCard(
    discoveryState: DiscoveryState,
    connectionState: ConnectionState,
    cameraWsState: ConnectionState,
    controlWsState: ConnectionState,
    connectedEndpoint: String?,
    isUsbDevMode: Boolean,
    onConnect: (DiscoveredVirtualLab) -> Unit,
    onDisconnect: () -> Unit,
    onRetrySearch: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "VIRTUAL LAB",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                val stateLabel = when (connectionState) {
                    is ConnectionState.Connected -> "CONNECTED"
                    is ConnectionState.Connecting -> "CONNECTING"
                    is ConnectionState.Error -> "FAILED"
                    is ConnectionState.Disconnected -> {
                        when (discoveryState) {
                            is DiscoveryState.Searching -> "SEARCHING"
                            is DiscoveryState.Found -> "FOUND"
                            is DiscoveryState.PermissionRequired -> "PERMISSION REQUIRED"
                            is DiscoveryState.Empty -> "DISCONNECTED"
                            is DiscoveryState.Error -> "FAILED"
                            is DiscoveryState.Idle -> "DISCONNECTED"
                        }
                    }
                }
                val stateColor = when (connectionState) {
                    is ConnectionState.Connected -> MaterialTheme.colorScheme.primary
                    is ConnectionState.Connecting -> MaterialTheme.colorScheme.secondary
                    is ConnectionState.Error -> MaterialTheme.colorScheme.error
                    is ConnectionState.Disconnected -> {
                        if (discoveryState is DiscoveryState.PermissionRequired || discoveryState is DiscoveryState.Error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    }
                }

                Surface(
                    color = stateColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = stateLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = stateColor
                    )
                }
            }

            // USB Dev mode notice if active
            if (isUsbDevMode && connectionState is ConnectionState.Connected) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "USB / LOCAL DEVELOPMENT CONNECTION",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "Connected via ADB reverse tunnel. Localhost loopback link active.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Connected or connecting state
            if (connectedEndpoint != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("VIRTUAL LAB CHANNELS", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        
                        val baseUrl = connectedEndpoint.replace("/sensors", "").trimEnd('/')
                        ChannelStatusRow("Sensors", "/sensors", "$baseUrl/sensors", connectionState)
                        ChannelStatusRow("Imaging", "/camera", "$baseUrl/camera", cameraWsState)
                        ChannelStatusRow("Control", "/control", "$baseUrl/control", controlWsState)
                        
                        Button(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text("DISCONNECT")
                        }
                    }
                }
            } else {
                // Show discovery results
                when (discoveryState) {
                    is DiscoveryState.Searching, is DiscoveryState.Idle -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Searching for VirtualLab...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    is DiscoveryState.PermissionRequired -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Local network discovery requires Nearby Devices permission.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                onClick = onRequestPermission,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("grant_access_button")
                            ) {
                                Text("GRANT ACCESS")
                            }
                        }
                    }
                    is DiscoveryState.Found -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            discoveryState.devices.forEach { device ->
                                DiscoveredDeviceCard(
                                    device = device,
                                    onConnect = { onConnect(device) }
                                )
                            }
                        }
                    }
                    is DiscoveryState.Empty -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "NO VIRTUAL LAB FOUND",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Ensure VirtualLab Desktop is running on the same local Wi-Fi network.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = onRetrySearch,
                                    modifier = Modifier.testTag("retry_search_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Retry Search")
                                }
                            }
                        }
                    }
                    is DiscoveryState.Error -> {
                        Text(
                            "Discovery error: ${discoveryState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = onRetrySearch) {
                            Text("Retry Search")
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun DiscoveredDeviceCard(
    device: DiscoveredVirtualLab,
    onConnect: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = device.serviceName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${device.hostAddress}:${device.port}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Protocol ${device.protocol} • ${device.path}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onConnect,
                modifier = Modifier.testTag("connect_button")
            ) {
                Text("CONNECT")
            }
        }
    }
}

// -------------------------------------------------------------
// DEVICE CARD
// -------------------------------------------------------------
@Composable
fun DeviceCard(metadata: DeviceMetadata) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "DEVICE",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            MetricRow(label = "SensorNode ID", value = metadata.deviceId)
            MetricRow(label = "Identity", value = metadata.identityType)
            MetricRow(label = "Android", value = "${metadata.androidVersion} / API ${metadata.sdkLevel}")
        }
    }
}

// -------------------------------------------------------------
// SENSORS CARD
// -------------------------------------------------------------
@Composable
fun SensorsCard(discoveredSensors: Map<SensorTypeClass, DiscoveredSensorMeta>) {
    var expandedSensor by remember { mutableStateOf<SensorTypeClass?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "SENSORS",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            for (sensorClass in SensorTypeClass.values()) {
                val meta = discoveredSensors[sensorClass]
                val isAvailable = meta?.isAvailable == true

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isAvailable) {
                            expandedSensor = if (expandedSensor == sensorClass) null else sensorClass
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isAvailable) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = sensorClass.displayName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                        if (isAvailable) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    meta?.name ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (expandedSensor == sensorClass) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        } else {
                            Text(
                                "NOT AVAILABLE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Expandable sensor metadata
                    AnimatedVisibility(visible = expandedSensor == sensorClass && meta != null) {
                        meta?.let { m ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    SubMetricRow("Vendor", m.vendor)
                                    SubMetricRow("Version", m.version.toString())
                                    SubMetricRow("Resolution", "${m.resolution}")
                                    SubMetricRow("Max Range", "${m.maximumRange}")
                                    SubMetricRow("Min Delay", "${m.minDelay} µs")
                                    SubMetricRow("Power", "${m.power} mA")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// LIVE CARD
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCard(
    availableSensors: List<SensorTypeClass>,
    selectedSensor: SensorTypeClass,
    reading: LiveSensorReading?,
    onSelectSensor: (SensorTypeClass) -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "LIVE",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                // Sensor Selector Dropdown
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it }
                ) {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier
                            .menuAnchor()
                            .testTag("sensor_selector")
                    ) {
                        Text(selectedSensor.displayName)
                        Spacer(Modifier.width(4.dp))
                        Icon(imageVector = Icons.Default.ExpandMore, contentDescription = null)
                    }

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        availableSensors.forEach { sensorClass ->
                            DropdownMenuItem(
                                text = { Text(sensorClass.displayName) },
                                onClick = {
                                    onSelectSensor(sensorClass)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (reading != null) {
                when (selectedSensor) {
                    SensorTypeClass.MAGNETOMETER -> {
                        val x = reading.values.getOrNull(0) ?: 0f
                        val y = reading.values.getOrNull(1) ?: 0f
                        val z = reading.values.getOrNull(2) ?: 0f
                        MetricRow(label = "Bx", value = String.format(Locale.US, "%+8.2f µT", x))
                        MetricRow(label = "By", value = String.format(Locale.US, "%+8.2f µT", y))
                        MetricRow(label = "Bz", value = String.format(Locale.US, "%+8.2f µT", z))
                    }
                    SensorTypeClass.ACCELEROMETER -> {
                        val x = reading.values.getOrNull(0) ?: 0f
                        val y = reading.values.getOrNull(1) ?: 0f
                        val z = reading.values.getOrNull(2) ?: 0f
                        MetricRow(label = "Ax", value = String.format(Locale.US, "%+8.2f m/s²", x))
                        MetricRow(label = "Ay", value = String.format(Locale.US, "%+8.2f m/s²", y))
                        MetricRow(label = "Az", value = String.format(Locale.US, "%+8.2f m/s²", z))
                    }
                    SensorTypeClass.GYROSCOPE -> {
                        val x = reading.values.getOrNull(0) ?: 0f
                        val y = reading.values.getOrNull(1) ?: 0f
                        val z = reading.values.getOrNull(2) ?: 0f
                        MetricRow(label = "ωx", value = String.format(Locale.US, "%+8.4f rad/s", x))
                        MetricRow(label = "ωy", value = String.format(Locale.US, "%+8.4f rad/s", y))
                        MetricRow(label = "ωz", value = String.format(Locale.US, "%+8.4f rad/s", z))
                    }
                    SensorTypeClass.AMBIENT_LIGHT -> {
                        val lux = reading.values.getOrNull(0) ?: 0f
                        MetricRow(label = "Illuminance", value = String.format(Locale.US, "%.1f lx", lux))
                    }
                    SensorTypeClass.PRESSURE -> {
                        val hPa = reading.values.getOrNull(0) ?: 0f
                        MetricRow(label = "Pressure", value = String.format(Locale.US, "%.2f hPa", hPa))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Rate",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (reading.observedHz > 0.1) {
                            String.format(Locale.US, "%.1f Hz", reading.observedHz)
                        } else {
                            selectedSensor.rateDescription
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            } else {
                Text(
                    "Waiting for sensor data...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SESSION CARD
// -------------------------------------------------------------
@Composable
fun SessionCard(
    sampleId: String,
    onSampleIdChange: (String) -> Unit,
    availableSensors: Map<SensorTypeClass, DiscoveredSensorMeta>,
    selectedSensors: Set<SensorTypeClass>,
    onToggleSensor: (SensorTypeClass) -> Unit,
    isRecording: Boolean,
    streamedCount: Long,
    isConnected: Boolean,
    onToggleRecording: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "SESSION",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            OutlinedTextField(
                value = sampleId,
                onValueChange = onSampleIdChange,
                label = { Text("Sample ID") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sample_id_input"),
                singleLine = true,
                enabled = !isRecording
            )

            Text(
                "Sensors",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (sensorClass in SensorTypeClass.values()) {
                    val isAvailable = availableSensors[sensorClass]?.isAvailable == true
                    val isChecked = selectedSensors.contains(sensorClass) && isAvailable

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isAvailable && !isRecording) {
                                onToggleSensor(sensorClass)
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleSensor(sensorClass) },
                                enabled = isAvailable && !isRecording
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = sensorClass.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = if (isAvailable) sensorClass.rateDescription else "NOT AVAILABLE",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (isAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onToggleRecording,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("capture_toggle_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isRecording) "STOP CAPTURE" else "START CAPTURE")
            }

            if (isRecording) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Capturing physical sensor data...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Packets: $streamedCount",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// ADVANCED CARD
// -------------------------------------------------------------
@Composable
fun AdvancedCard(
    diagnostics: NetworkDiagnostics,
    connectionLogs: List<String>,
    cameraDiagnosticsReport: String,
    onCopyCameraDiagnostics: () -> Unit,
    onConnectManual: (String) -> Unit,
    isConnecting: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }
    var manualUrl by remember { mutableStateOf("ws://192.168.254.3:8765/sensors") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ADVANCED",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Diagnostics & manual URL",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Manual Connect
                    Text(
                        "Connect Manually",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = manualUrl,
                        onValueChange = { manualUrl = it },
                        label = { Text("WebSocket URL") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_url_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true
                    )
                    Button(
                        onClick = { onConnectManual(manualUrl) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connect_manually_button"),
                        enabled = !isConnecting
                    ) {
                        Text("CONNECT MANUALLY")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // ANDROID / NETWORK DIAGNOSTICS
                    Text(
                        "ANDROID / NETWORK DIAGNOSTICS",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    DiagnosticItem("Android", "${diagnostics.androidVersion} / API ${diagnostics.sdkLevel}")
                    DiagnosticItem("Target SDK", "${diagnostics.targetSdk}")
                    DiagnosticItem("NEARBY_WIFI_DEVICES", diagnostics.nearbyWifiDevicesStatus)
                    DiagnosticItem("ACCESS_LOCAL_NETWORK", diagnostics.accessLocalNetworkStatus)
                    DiagnosticItem("NSD Discovery", if (diagnostics.isNsdAvailable) "AVAILABLE" else "UNAVAILABLE")
                    DiagnosticItem("Wi-Fi state", if (diagnostics.isWifiConnected) "CONNECTED" else "DISCONNECTED")
                    DiagnosticItem("Device IP", diagnostics.localIp ?: "UNAVAILABLE")
                    DiagnosticItem("Gateway", diagnostics.gateway ?: "UNAVAILABLE")
                    DiagnosticItem("Resolved VirtualLab", diagnostics.resolvedVirtualLab ?: "NONE")
                    DiagnosticItem("WebSocket URL", diagnostics.exactWsUrl ?: "NONE")

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Raw Connection Log
                    Text(
                        "Raw Connection Log",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                    ) {
                        if (connectionLogs.isEmpty()) {
                            Text(
                                "No socket events logged yet.",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(connectionLogs) { log ->
                                    Text(
                                        text = log,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // CAMERA2 HARDWARE INVENTORY & CONCURRENCY
                    CameraDiagnosticsView(
                        diagnosticsReport = cameraDiagnosticsReport,
                        onCopyDiagnostics = onCopyCameraDiagnostics
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// HELPER COMPONENTS
// -------------------------------------------------------------
@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun DiagnosticItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            ),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun SubMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

@Composable
fun ChannelStatusRow(name: String, path: String, url: String, state: ConnectionState) {
    val stateText = when (state) {
        is ConnectionState.Connected -> "CONNECTED"
        is ConnectionState.Connecting -> "CONNECTING"
        is ConnectionState.Error -> "FAILED"
        is ConnectionState.Disconnected -> "DISCONNECTED"
    }
    val color = when (state) {
        is ConnectionState.Connected -> MaterialTheme.colorScheme.primary
        is ConnectionState.Connecting -> MaterialTheme.colorScheme.secondary
        is ConnectionState.Error -> MaterialTheme.colorScheme.error
        is ConnectionState.Disconnected -> MaterialTheme.colorScheme.outline
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        Text(path, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
        Text(url, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Icon(imageVector = Icons.Default.Circle, contentDescription = null, tint = color, modifier = Modifier.size(10.dp))
            Spacer(Modifier.width(6.dp))
            Text(stateText, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color)
            if (state is ConnectionState.Error) {
                Spacer(Modifier.width(6.dp))
                Text(state.message, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
            }
        }
    }
}
