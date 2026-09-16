import re

with open('app/src/main/java/com/example/acquisition/SensorAcquisitionManager.kt', 'r') as f:
    content = f.read()
content = content.replace("import com.example.session.MeasurementPacket\n", "")
with open('app/src/main/java/com/example/acquisition/SensorAcquisitionManager.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Fix receiveAsFlow import
if "import kotlinx.coroutines.flow.receiveAsFlow" not in content:
    content = content.replace("import kotlinx.coroutines.flow.asSharedFlow\n", "import kotlinx.coroutines.flow.asSharedFlow\nimport kotlinx.coroutines.flow.receiveAsFlow\n")

# Fix timestampNs in onPreviewImageAvailable
content = content.replace("device_timestamp_ns = timestampNs", "device_timestamp_ns = timestamp")

# Fix mangled onStillImageAvailable
content = content.replace("onStillImageAvailablerivate fun onStillImageAvailable", "private fun onStillImageAvailable")

# Fix frame_sequence in CameraScientificFrameMessage
content = content.replace("sequence = frameSequence++", "frame_sequence = frameSequence++")

# Fix CameraPreviewFrameMessage initialization
content = content.replace(
"""                scientific_state = "MEASURED",
                acquisition_type = "CAMERA_FRAME",
                representation = "ISP_PROCESSED",
                request_id = null,
                payload_size_bytes = bytes.size,
                payload_sha256 = null""",
"""                scientific_state = "MEASURED",
                acquisition_type = "CAMERA_FRAME",
                representation = "ISP_PROCESSED",
                payload_size_bytes = bytes.size
"""
)

# Fix ControlParameters references. It has snake_case in protocol/v1/CameraMessages.kt
# We need to change afMode -> af_mode, aeMode -> ae_mode, etc.
replacements = {
    "afMode": "af_mode",
    "focusDistanceDiopters": "focus_distance_diopters",
    "aeMode": "ae_mode",
    "exposureTimeNs": "exposure_time_ns",
    "frameDurationNs": "frame_duration_ns",
    "aeCompensation": "ae_compensation",
    "awbMode": "awb_mode",
    "awbLock": "awb_lock",
    "fpsRange": "fps_range",
    "zoomRatio": "zoom_ratio",
    "cropRegion": "crop_region",
    "opticalStabilization": "optical_stabilization",
    "videoStabilization": "video_stabilization"
}

for k, v in replacements.items():
    content = content.replace(f"params.{k}", f"params.{v}")
    content = content.replace(f"currentControlParams.{k}", f"currentControlParams.{v}")
    content = content.replace(f"requested.{v} ?: currentControlParams.{v}", f"requested.{v} ?: currentControlParams.{v}")
    content = content.replace(f"{k} = requested.{v} ?: currentControlParams.{v}", f"{v} = requested.{v} ?: currentControlParams.{v}")
    content = content.replace(f"{k} = newRes", f"resolution = newRes") # wait, resolution is already correct.

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)

