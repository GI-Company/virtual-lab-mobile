package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.transport.ConnectionState
import java.util.Locale
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorNodeApp(
    viewModel: MainViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val magnetometerData by viewModel.magnetometerData.collectAsState()
    val isMagnetometerAvailable by viewModel.isMagnetometerAvailable.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SensorNode") },
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
                ConnectSection(connectionState, onConnect = viewModel::connect, onDisconnect = viewModel::disconnect)
            }
            item {
                SensorsSection(isMagnetometerAvailable)
            }
            item {
                LiveSection(magnetometerData)
            }
            item {
                SessionSection(isRecording, onToggleRecording = viewModel::toggleRecording)
            }
        }
    }
}

@Composable
fun ConnectSection(
    connectionState: ConnectionState,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    var url by remember { mutableStateOf("ws://192.168.1.42:8765/sensors") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CONNECT", style = MaterialTheme.typography.titleMedium)
            
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("VirtualLab Desktop URL") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (connectionState) {
                    is ConnectionState.Connected -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("CONNECTED", color = MaterialTheme.colorScheme.primary)
                        }
                        Button(onClick = onDisconnect) {
                            Text("DISCONNECT")
                        }
                    }
                    is ConnectionState.Connecting -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Button(onClick = onDisconnect) {
                            Text("CANCEL")
                        }
                    }
                    is ConnectionState.Disconnected, is ConnectionState.Error -> {
                        val isError = connectionState is ConnectionState.Error
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isError) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(4.dp))
                                Text("ERROR", color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("DISCONNECTED")
                            }
                        }
                        Button(onClick = { onConnect(url) }) {
                            Text("CONNECT")
                        }
                    }
                }
            }
            if (connectionState is ConnectionState.Error) {
                Text(
                    text = (connectionState as ConnectionState.Error).message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun SensorsSection(isMagnetometerAvailable: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SENSORS", style = MaterialTheme.typography.titleMedium)
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("LIVE (Magnetometer)", style = MaterialTheme.typography.titleMedium)
            
            if (data != null) {
                val magnitude = sqrt(data.x * data.x + data.y * data.y + data.z * data.z)
                
                Text(String.format(Locale.US, "Bx: %8.1f µT", data.x), style = MaterialTheme.typography.bodyLarge)
                Text(String.format(Locale.US, "By: %8.1f µT", data.y), style = MaterialTheme.typography.bodyLarge)
                Text(String.format(Locale.US, "Bz: %8.1f µT", data.z), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(String.format(Locale.US, "|B|: %8.1f µT", magnitude), style = MaterialTheme.typography.titleMedium)
            } else {
                Text("Waiting for data...", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun SessionSection(isRecording: Boolean, onToggleRecording: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SESSION", style = MaterialTheme.typography.titleMedium)
            Text("Sample: SMP-001")
            Text("Rate: GAME (~50 Hz)")
            
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onToggleRecording,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isRecording) "STOP CAPTURE" else "START CAPTURE")
            }
            if (isRecording) {
                Text("Recording magnetic field...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
