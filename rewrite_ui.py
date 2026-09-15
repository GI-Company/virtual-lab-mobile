import re

with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'r') as f:
    content = f.read()

# Find the start of VirtualLabCard
start_str = "fun VirtualLabCard("
start_idx = content.find(start_str)

# Find the next fun after VirtualLabCard
next_fun_str = "fun DiscoveredDeviceCard("
end_idx = content.find(next_fun_str)

if start_idx != -1 and end_idx != -1:
    new_fun = """fun VirtualLabCard(
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
"""
    
    content = content[:start_idx] + new_fun + content[end_idx:]
    with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'w') as f:
        f.write(content)

