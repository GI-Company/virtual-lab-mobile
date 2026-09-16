import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Remove CameraInventoryMessage handling in processControlCommand
# and change it to handle GET_CAMERA_INVENTORY inside CAMERA_CONTROL_REQUEST

# The processControlCommand method currently has:
# if (command is CameraInventoryMessage) { ... }
# Let's remove it and add GET_CAMERA_INVENTORY case inside command.control_type when 

old_inventory_block = re.search(r'if \(command is CameraInventoryMessage\) \{.*?return\n        \}\n', content, re.DOTALL)
if old_inventory_block:
    content = content[:old_inventory_block.start()] + content[old_inventory_block.end():]
else:
    print("Could not find CameraInventoryMessage block")

# Add to when (command.control_type)
inventory_case = """
                "GET_CAMERA_INVENTORY" -> {
                    val cameras = cameraInventory.discoverCameras().map { c ->
                        com.example.protocol.v1.DiscoveredCameraModel(
                            id = c.id,
                            is_logical = c.isLogical,
                            physical_camera_ids = c.physicalCameraIds,
                            parent_logical_camera_id = c.parentLogicalCameraId,
                            lens_facing = c.lensFacing.name,
                            focal_lengths = c.focalLengths,
                            sensor_physical_size_mm = c.sensorPhysicalSizeMm?.let { listOf(it.first, it.second) },
                            pixel_array_size = c.pixelArraySize?.let { listOf(it.first, it.second) },
                            active_array_size = c.activeArraySize,
                            timestamp_source = c.timestampSource,
                            hardware_level = c.hardwareLevel,
                            capabilities = c.capabilities,
                            has_raw = c.hasRaw,
                            has_logical_multi = c.hasLogicalMulti,
                            supported_resolutions = c.supportedResolutions.map { "${it.first}x${it.second}" },
                            supported_fps_ranges = c.supportedFpsRanges.map { "${it.first}-${it.second}" },
                            friendly_name = c.friendlyName,
                            mp_class = c.mpClass,
                            max_stream_resolution = c.maxStreamResolution,
                            status = c.status,
                            concurrency_status = c.concurrencyStatus,
                            independently_openable = c.independentlyOpenable
                        )
                    }
                    sendResult(ProtocolSerializer.serialize(CameraInventoryMessage(
                        request_id = requestId,
                        device_id = deviceId,
                        cameras = cameras
                    )))
                    return
                }"""

content = content.replace('"GET_CAPABILITIES" -> {', inventory_case.lstrip() + '\n                "GET_CAPABILITIES" -> {')

# Also fix the device_id check at the top of processControlCommand
old_req_device = """val reqDeviceId = when (command) {
            is CameraControlRequestMessage -> command.device_id
            is ScientificCaptureRequestMessage -> command.device_id
            is CameraInventoryMessage -> command.device_id
            else -> null
        }"""
new_req_device = """val reqDeviceId = when (command) {
            is CameraControlRequestMessage -> command.device_id
            is ScientificCaptureRequestMessage -> command.device_id
            else -> null
        }"""
content = content.replace(old_req_device, new_req_device)

old_req_id = """val reqId = when (command) {
                is CameraControlRequestMessage -> command.request_id
                is ScientificCaptureRequestMessage -> command.request_id
                is CameraInventoryMessage -> command.request_id
                else -> ""
            }"""
new_req_id = """val reqId = when (command) {
                is CameraControlRequestMessage -> command.request_id
                is ScientificCaptureRequestMessage -> command.request_id
                else -> ""
            }"""
content = content.replace(old_req_id, new_req_id)

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
