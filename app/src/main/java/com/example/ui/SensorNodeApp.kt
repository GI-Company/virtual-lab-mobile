package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.transport.ConnectionState
import com.example.transport.DiscoveredVirtualLab
import com.example.transport.DiscoveryState
import com.example.transport.NetworkDiagnostics
import java.util.Locale
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorNodeApp(
    viewModel: MainViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val connectedEndpoint by viewModel.connectedEndpoint.collectAsState()
    val discoveryState by viewModel.discoveryState.collectAsState()
    val networkDiagnostics by viewModel.networkDiagnostics.collectAsState()
    val magnetometerData by viewModel.magnetometerData.collectAsState()
    val isMagnetometerAvailable by viewModel.isMagnetometerAvailable.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startDiscovery()
        onDispose {
            viewModel.stopDiscovery()
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
                        Text(
                            "VirtualLab SensorNode",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                NetworkDiagnosticsSection(networkDiagnostics)
            }
            item {
                ConnectSection(
                    discoveryState = discoveryState,
                    connectionState = connectionState,
                    connectedEndpoint = connectedEndpoint,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onRetrySearch = viewModel::retrySearch
                )
            }
            item {
                SensorsSection(isMagnetometerAvailable)
            }
            item {
                LiveSection(magnetometerData)
            }
            item {
                SessionSection(
                    isRecording = isRecording,
                    onToggleRecording = viewModel::toggleRecording
                )
            }
        }
    }
}

@Composable
fun NetworkDiagnosticsSection(diagnostics: NetworkDiagnostics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "NETWORK",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (diagnostics.isWifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = "Wi-Fi icon",
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            DiagnosticRow(label = "Device IP", value = diagnostics.localIp ?: "Unavailable")
            if (diagnostics.gateway != null) {
                DiagnosticRow(label = "Gateway", value = diagnostics.gateway)
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
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
fun ConnectSection(
    discoveryState: DiscoveryState,
    connectionState: ConnectionState,
    connectedEndpoint: String?,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRetrySearch: () -> Unit
) {
    var manualUrl by remember { mutableStateOf("ws://192.168.1.42:8765/sensors") }
    var isAdvancedExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "CONNECT",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            // If already connected
            if (connectionState is ConnectionState.Connected) {
                ConnectedStatusCard(
                    endpoint = connectedEndpoint ?: "Connected",
                    onDisconnect = onDisconnect
                )
            } else if (connectionState is ConnectionState.Connecting) {
                ConnectingStatusCard(
                    endpoint = connectedEndpoint ?: "VirtualLab",
                    onCancel = onDisconnect
                )
            } else {
                // Not connected: show discovery results or empty/searching state
                if (connectionState is ConnectionState.Error) {
                    ConnectionErrorBanner(
                        message = connectionState.message,
                        onDismiss = onDisconnect
                    )
                }

                DiscoveredServicesView(
                    discoveryState = discoveryState,
                    onConnect = onConnect,
                    onRetrySearch = onRetrySearch
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Advanced -> Connect manually section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Advanced",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Connect manually",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isAdvancedExpanded) "Collapse manual connect" else "Expand manual connect"
                    )
                }
            }

            AnimatedVisibility(visible = isAdvancedExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualUrl,
                        onValueChange = { manualUrl = it },
                        label = { Text("WebSocket URL") },
                        modifier = Modifier.fillMaxWidth().testTag("manual_url_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true
                    )
                    Button(
                        onClick = { onConnect(manualUrl) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connect_manually_button"),
                        enabled = connectionState !is ConnectionState.Connecting
                    ) {
                        Text("CONNECT MANUALLY")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedStatusCard(
    endpoint: String,
    onDisconnect: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "CONNECTED",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("disconnect_button")
                ) {
                    Text("DISCONNECT")
                }
            }
            Text(
                text = endpoint,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ConnectingStatusCard(
    endpoint: String,
    onCancel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Connecting...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        endpoint,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
            OutlinedButton(onClick = onCancel) {
                Text("CANCEL")
            }
        }
    }
}

@Composable
private fun ConnectionErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            TextButton(onClick = onDismiss) {
                Text("DISMISS", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DiscoveredServicesView(
    discoveryState: DiscoveryState,
    onConnect: (String) -> Unit,
    onRetrySearch: () -> Unit
) {
    when (discoveryState) {
        is DiscoveryState.Searching, is DiscoveryState.Idle -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Searching for VirtualLab...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        is DiscoveryState.Found -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "VIRTUAL LAB",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    IconButton(
                        onClick = onRetrySearch,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh discovered services",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                discoveryState.devices.forEach { device ->
                    DiscoveredDeviceItem(device = device, onConnect = { onConnect(device.wsUrl) })
                }
            }
        }
        is DiscoveryState.Empty -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "NO VIRTUAL LAB FOUND",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Ensure VirtualLab Desktop is running and both devices are on the same Wi-Fi network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onRetrySearch,
                        modifier = Modifier.testTag("retry_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Retry Search")
                    }
                }
            }
        }
        is DiscoveryState.Error -> {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "DISCOVERY ERROR",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        discoveryState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(
                        onClick = onRetrySearch,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("retry_search_button")
                    ) {
                        Text("Retry Search")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveredDeviceItem(
    device: DiscoveredVirtualLab,
    onConnect: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
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
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
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
                if (device.path != "/sensors" || device.protocol != "1") {
                    Text(
                        text = "path: ${device.path} • proto: ${device.protocol}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
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

@Composable
fun SensorsSection(isMagnetometerAvailable: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "SENSORS",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isMagnetometerAvailable) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (isMagnetometerAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text("Magnetometer", style = MaterialTheme.typography.bodyLarge)
                if (!isMagnetometerAvailable) {
                    Spacer(Modifier.width(8.dp))
                    Text("(NOT AVAILABLE)", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun LiveSection(data: com.example.acquisition.MagnetometerData?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "LIVE",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            if (data != null) {
                val magnitude = sqrt(data.x * data.x + data.y * data.y + data.z * data.z)

                LiveMetricRow(label = "Bx", value = String.format(Locale.US, "%8.1f µT", data.x))
                LiveMetricRow(label = "By", value = String.format(Locale.US, "%8.1f µT", data.y))
                LiveMetricRow(label = "Bz", value = String.format(Locale.US, "%8.1f µT", data.z))

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "|B|",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        String.format(Locale.US, "%8.1f µT", magnitude),
                        style = MaterialTheme.typography.titleMedium.copy(
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

@Composable
private fun LiveMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

@Composable
fun SessionSection(isRecording: Boolean, onToggleRecording: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "SESSION",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text("Sample: SMP-001", style = MaterialTheme.typography.bodyMedium)
            Text("Rate: 50 Hz", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(8.dp))
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
                Text(
                    "Recording magnetic field...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
