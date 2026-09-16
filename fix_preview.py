import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Replace onPreviewImageAvailable body
old_preview = re.search(r'val metadata = CameraPreviewFrameMessage\(.*?_frameFlow\.tryEmit\(Pair\(metadata, bytes\)\).*?captureState\[realCaptureId\] = "AWAITING_ACK"', content, re.DOTALL)
new_preview = """            val afStr = if (afModeInt == CaptureResult.CONTROL_AF_MODE_OFF) "OFF" else if (afModeInt == CaptureResult.CONTROL_AF_MODE_AUTO) "AUTO" else "UNKNOWN"
            val aeStr = if (aeModeInt == CaptureResult.CONTROL_AE_MODE_OFF) "OFF" else if (aeModeInt == CaptureResult.CONTROL_AE_MODE_ON) "ON" else "UNKNOWN"
            val awbStr = if (awbModeInt == CaptureResult.CONTROL_AWB_MODE_OFF) "OFF" else if (awbModeInt == CaptureResult.CONTROL_AWB_MODE_AUTO) "AUTO" else "UNKNOWN"

            val metadata = CameraPreviewFrameMessage(
                stream_id = currentStreamId,
                sequence = frameSequence++,
                device_id = activeDeviceId,
                camera_id = activeCameraId,
                logical_camera_id = activeParentLogicalId,
                physical_camera_id = if (isPhysicalMember) activeCameraId else null,
                device_timestamp_ns = timestampNs,
                metadata = CameraFrameMetadata(
                    resolution = "${image.width}x${image.height}",
                    format = "JPEG",
                    lens_facing = activeLensFacingStr,
                    focal_length_mm = focalLen,
                    exposure_time_ns = expTime,
                    iso = iso,
                    focus_distance_diopters = focusDist,
                    af_mode = afStr,
                    ae_mode = aeStr,
                    awb_mode = awbStr,
                    stabilization_mode = stabStr,
                    torch_state = torchStr
                ),
                payload_size_bytes = bytes.size
            )
            
            _frameFlow.tryEmit(Pair(metadata, bytes))"""
if old_preview:
    content = content[:old_preview.start()] + new_preview + content[old_preview.end():]
else:
    print("Failed to replace preview")

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
