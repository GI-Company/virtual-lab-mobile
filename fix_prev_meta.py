import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

old_prev = re.search(r'val metadata = CameraPreviewFrameMessage\(.*?payload_size_bytes = bytes\.size\n            \)', content, re.DOTALL)
if old_prev:
    new_prev = """val metadata = com.example.protocol.v1.CameraPreviewFrameMessage(
                stream_id = currentStreamId,
                frame_sequence = frameSequence++,
                device_id = activeDeviceId,
                camera_stream_key = com.example.protocol.v1.CameraStreamKey(activeCameraId, activeParentLogicalId, if (isPhysicalMember) activeCameraId else null),
                device_timestamp_ns = timestampNs,
                device_timebase = "MONOTONIC",
                width = image.width,
                height = image.height,
                encoding = "JPEG",
                orientation = 0,
                lens_facing = activeLensFacingStr,
                focal_length_mm = focalLen,
                exposure_time_ns = expTime,
                sensor_sensitivity_iso = iso,
                focus_distance_diopters = focusDist,
                scientific_state = "MEASURED",
                acquisition_type = "CAMERA_FRAME",
                representation = "ISP_PROCESSED",
                request_id = null,
                payload_size_bytes = bytes.size,
                payload_sha256 = null
            )"""
    content = content[:old_prev.start()] + new_prev + content[old_prev.end():]
else:
    print("Could not find preview metadata")

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
