import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

old_get_state = """                "GET_STATE" -> {
                    sendResult(ProtocolSerializer.serialize(CameraStateMessage(
                        device_id = deviceId,
                        camera_stream_key = com.example.protocol.v1.CameraStreamKey(activeCameraId, activeParentLogicalId, if (isPhysicalMember) activeCameraId else null),
                        current_exposure_time_ns = currentControlParams.exposure_time_ns,
                        current_iso = currentControlParams.iso,
                        current_focus_distance_diopters = currentControlParams.focus_distance_diopters,
                        current_af_mode = currentControlParams.af_mode,
                        thermal_status = thermalMonitor.thermalStatus.value
                    )))
                    return
                }"""

new_get_state = """                "GET_STATE" -> {
                    val result = latestCaptureResult
                    val afMode = result?.get(CaptureResult.CONTROL_AF_MODE)?.let { mode ->
                        when(mode) {
                            CaptureResult.CONTROL_AF_MODE_OFF -> "OFF"
                            CaptureResult.CONTROL_AF_MODE_AUTO -> "AUTO"
                            CaptureResult.CONTROL_AF_MODE_MACRO -> "MACRO"
                            CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "CONTINUOUS_VIDEO"
                            CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "CONTINUOUS_PICTURE"
                            else -> "UNKNOWN"
                        }
                    }
                    sendResult(ProtocolSerializer.serialize(CameraStateMessage(
                        device_id = deviceId,
                        camera_stream_key = com.example.protocol.v1.CameraStreamKey(activeCameraId, activeParentLogicalId, if (isPhysicalMember) activeCameraId else null),
                        current_exposure_time_ns = result?.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                        current_iso = result?.get(CaptureResult.SENSOR_SENSITIVITY),
                        current_focus_distance_diopters = result?.get(CaptureResult.LENS_FOCUS_DISTANCE),
                        current_af_mode = afMode,
                        thermal_status = thermalMonitor.thermalStatus.value
                    )))
                    return
                }"""

if old_get_state in content:
    content = content.replace(old_get_state, new_get_state)
    with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
        f.write(content)
    print("Fixed GET_STATE")
else:
    print("Could not find GET_STATE")

