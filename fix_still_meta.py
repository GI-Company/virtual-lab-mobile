import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

old_still_meta = re.search(r'val metadata = CameraFrameMetadata\(.*?_frameFlow\.tryEmit\(Pair\(metadata, bytes\)\)', content, re.DOTALL)
new_still_meta = """
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = md.digest(bytes)
            val sha256Hex = hashBytes.joinToString("") { "%02x".format(it) }
            
            val afStr = if (afModeInt == CaptureResult.CONTROL_AF_MODE_OFF) "OFF" else if (afModeInt == CaptureResult.CONTROL_AF_MODE_AUTO) "AUTO" else "UNKNOWN"
            val aeStr = if (aeModeInt == CaptureResult.CONTROL_AE_MODE_OFF) "OFF" else if (aeModeInt == CaptureResult.CONTROL_AE_MODE_ON) "ON" else "UNKNOWN"
            val awbStr = if (awbModeInt == CaptureResult.CONTROL_AWB_MODE_OFF) "OFF" else if (awbModeInt == CaptureResult.CONTROL_AWB_MODE_AUTO) "AUTO" else "UNKNOWN"

            val metadata = CameraScientificFrameMessage(
                stream_id = currentStreamId,
                sequence = frameSequence,
                request_id = realCaptureId,
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
                payload_size_bytes = bytes.size,
                payload_sha256 = sha256Hex
            )
            
            _frameFlow.tryEmit(Pair(metadata, bytes))"""
            
if old_still_meta:
    content = content[:old_still_meta.start()] + new_still_meta + content[old_still_meta.end():]
else:
    print("Could not find still metadata")
    
with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
