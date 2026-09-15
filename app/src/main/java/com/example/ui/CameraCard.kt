package com.example.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.*

// -------------------------------------------------------------
// CAMERA PERMISSION BANNER
// -------------------------------------------------------------
@Composable
fun CameraPermissionBanner(onRequestAccess: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CAMERA PERMISSION REQUIRED",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Camera access is required for Camera2 hardware topology discovery and scientific streaming.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onRequestAccess,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("GRANT", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// -------------------------------------------------------------
// CAMERAS INVENTORY CARD
// -------------------------------------------------------------
@Composable
fun CamerasInventoryCard(
    discoveredCameras: List<DiscoveredCamera>,
    permissionState: CameraPermissionState,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "CAMERAS",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${discoveredCameras.size} DETECTED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh cameras",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                text = "Discovered via android.hardware.camera2 runtime topology inspection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (permissionState != CameraPermissionState.GRANTED) {
                Text(
                    text = "Camera permission not granted. Tap GRANT to inspect Camera2 hardware.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("GRANT CAMERA ACCESS")
                }
            } else if (discoveredCameras.isEmpty()) {
                Text(
                    text = "No cameras detected on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                discoveredCameras.forEach { cam ->
                    DiscoveredCameraItem(cam)
                    if (cam != discoveredCameras.last()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveredCameraItem(cam: DiscoveredCamera) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cam.friendlyName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${cam.mpClass} • Camera2 ID: ${cam.id}${if (cam.parentLogicalCameraId != null) " (Member of ${cam.parentLogicalCameraId})" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = if (cam.status == "AVAILABLE") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = cam.status,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = if (cam.status == "AVAILABLE") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val focalStr = if (cam.focalLengths.isNotEmpty()) "${cam.focalLengths.joinToString(", ")} mm" else "Fixed"
            Text(
                text = "Focal: $focalStr",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Max Stream: ${cam.maxStreamResolution}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Concurrency: ${cam.concurrencyStatus}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                ),
                color = if (cam.concurrencyStatus.contains("CONCURRENT")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(
                    text = if (expanded) "Hide Specs" else "View Specs",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Hardware Level: ${cam.hardwareLevel}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Text(
                        "Timestamp Source: ${cam.timestampSource}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Text(
                        "RAW Capability: ${if (cam.hasRaw) "SUPPORTED" else "UNAVAILABLE"}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Text(
                        "Logical Multi-Camera: ${if (cam.hasLogicalMulti) "SUPPORTED" else "NO"}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    if (cam.physicalCameraIds.isNotEmpty()) {
                        Text(
                            "Physical Members: ${cam.physicalCameraIds.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                    if (cam.sensorPhysicalSizeMm != null) {
                        Text(
                            "Physical Sensor: ${cam.sensorPhysicalSizeMm.first} x ${cam.sensorPhysicalSizeMm.second} mm",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                    if (cam.pixelArraySize != null) {
                        Text(
                            "Pixel Array: ${cam.pixelArraySize.first} x ${cam.pixelArraySize.second}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                    Text(
                        "Capabilities: ${cam.capabilities.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// LIVE CAMERA CARD
// -------------------------------------------------------------
@Composable
fun LiveCameraCard(
    discoveredCameras: List<DiscoveredCamera>,
    selectedCamera: DiscoveredCamera?,
    onSelectCamera: (DiscoveredCamera) -> Unit,
    cameraMode: CameraMode,
    onSelectCameraMode: (CameraMode) -> Unit,
    concurrentGroups: List<ConcurrentCameraGroup>,
    selectedConcurrentGroup: ConcurrentCameraGroup?,
    onSelectConcurrentGroup: (ConcurrentCameraGroup) -> Unit,
    isStreaming: Boolean,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit,
    onCaptureFrame: () -> Unit,
    liveStats: LiveCameraStats,
    previewBitmap: Bitmap?,
    lastCapturedFrame: ScientificCapturedFrame?,
    errorMessage: String?,
    onDismissError: () -> Unit,
    isWsConnected: Boolean
) {
    var cameraDropdownExpanded by remember { mutableStateOf(false) }
    var concurrentDropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "LIVE CAMERA",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Surface(
                    color = if (isStreaming) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isStreaming) "STREAMING" else "IDLE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = if (isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Error banner if any
            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismissError, modifier = Modifier.size(20.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Camera Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = cameraMode == CameraMode.SINGLE,
                    onClick = { onSelectCameraMode(CameraMode.SINGLE) },
                    label = { Text("Single Camera") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = cameraMode == CameraMode.CONCURRENT,
                    onClick = { onSelectCameraMode(CameraMode.CONCURRENT) },
                    label = { Text("Concurrent Cameras") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Single Camera Selector vs Concurrent Selector
            if (cameraMode == CameraMode.SINGLE) {
                Box {
                    OutlinedButton(
                        onClick = { cameraDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedCamera?.friendlyName ?: "Select Camera",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = cameraDropdownExpanded,
                        onDismissRequest = { cameraDropdownExpanded = false }
                    ) {
                        discoveredCameras.forEach { cam ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(cam.friendlyName, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${cam.mpClass} • ID: ${cam.id} • ${cam.status}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                },
                                onClick = {
                                    onSelectCamera(cam)
                                    cameraDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                // Concurrent Camera Mode
                if (concurrentGroups.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "NOT SUPPORTED CONCURRENTLY",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Android CameraManager reports zero concurrent camera combinations supported by the HAL on this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Box {
                        OutlinedButton(
                            onClick = { concurrentDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedConcurrentGroup?.description ?: "Select Concurrent Group",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }

                        DropdownMenu(
                            expanded = concurrentDropdownExpanded,
                            onDismissRequest = { concurrentDropdownExpanded = false }
                        ) {
                            concurrentGroups.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text(group.description, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        onSelectConcurrentGroup(group)
                                        concurrentDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Live Image Preview Frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Camera Live Preview",
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (isStreaming) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                        Text(
                            text = "Acquiring hardware camera frames...",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Camera preview inactive",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray
                        )
                        Text(
                            text = "Press START STREAM to begin acquisition",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.DarkGray
                        )
                    }
                }

                // Scientific preview watermark badge
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "1280x720 max • ~10–15 FPS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Live Metrics Grid
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MetricRow(label = "Resolution", value = liveStats.resolution)
                    MetricRow(label = "Observed FPS", value = "${liveStats.observedFps} FPS")
                    MetricRow(label = "Frames", value = "%,d".format(liveStats.totalFrames))
                    MetricRow(label = "Dropped", value = "%,d".format(liveStats.droppedFrames))
                    MetricRow(
                        label = "Exposure",
                        value = liveStats.exposureTimeNs?.let { "${"%.2f".format(it / 1_000_000.0)} ms" } ?: "AUTO"
                    )
                    MetricRow(label = "ISO", value = liveStats.iso?.toString() ?: "AUTO")
                    MetricRow(
                        label = "Focal Length",
                        value = liveStats.focalLengthMm?.let { "${"%.2f".format(it)} mm" } ?: "N/A"
                    )
                    MetricRow(
                        label = "Focus Distance",
                        value = liveStats.focusDistance?.let { "${"%.2f".format(it)} m" } ?: "N/A"
                    )
                    MetricRow(
                        label = "Device Timestamp",
                        value = liveStats.timestampNs?.let { "${it} ns" } ?: "N/A"
                    )
                    MetricRow(label = "Bitrate", value = "${liveStats.bitrateKbps} kbps")
                    MetricRow(label = "Thermal Status", value = liveStats.thermalStatus)
                    MetricRow(
                        label = "Transport",
                        value = if (isWsConnected) "ACTIVE (/camera binary WS)" else "READY (Local only)"
                    )
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isStreaming) {
                    Button(
                        onClick = onStartStream,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("START STREAM")
                    }
                } else {
                    Button(
                        onClick = onStopStream,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("STOP STREAM")
                    }
                }

                OutlinedButton(
                    onClick = onCaptureFrame,
                    enabled = isStreaming,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("CAPTURE FRAME")
                }
            }

            // Last Captured Scientific Frame Notification
            if (lastCapturedFrame != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "SCIENTIFIC FRAME CAPTURED",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Capture ID: ${lastCapturedFrame.captureId}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Resolution: ${lastCapturedFrame.width}x${lastCapturedFrame.height} • ${lastCapturedFrame.jpegSizeBytes / 1024} KB • ISO ${lastCapturedFrame.iso ?: "Auto"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// CAMERA DIAGNOSTICS COMPONENT (FOR ADVANCED CARD)
// -------------------------------------------------------------
@Composable
fun CameraDiagnosticsView(
    diagnosticsReport: String,
    onCopyDiagnostics: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CAMERA2 HARDWARE INVENTORY",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            Button(
                onClick = onCopyDiagnostics,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("COPY DIAGNOSTICS", style = MaterialTheme.typography.labelSmall)
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = diagnosticsReport,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                ),
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
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
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        )
    }
}
