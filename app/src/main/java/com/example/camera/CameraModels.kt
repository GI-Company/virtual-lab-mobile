package com.example.camera

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CameraFacing {
    BACK,
    FRONT,
    EXTERNAL,
    UNKNOWN
}

enum class CameraMode {
    SINGLE,
    CONCURRENT
}

@Serializable
data class CameraFrameMetadata(
    @SerialName("schema_version") val schemaVersion: String = "1",
    @SerialName("message_type") val messageType: String = "CAMERA_PREVIEW_FRAME",
    @SerialName("device_id") val deviceId: String,
    @SerialName("camera_id") val cameraId: String,
    @SerialName("logical_camera_id") val logicalCameraId: String? = null,
    @SerialName("physical_camera_id") val physicalCameraId: String? = null,
    @SerialName("frame_sequence") val frameSequence: Long,
    @SerialName("device_timestamp_ns") val deviceTimestampNs: Long,
    @SerialName("width") val width: Int,
    @SerialName("height") val height: Int,
    @SerialName("pixel_format") val pixelFormat: String = "JPEG",
    @SerialName("encoding") val encoding: String = "JPEG",
    @SerialName("orientation") val orientation: Int = 0,
    @SerialName("lens_facing") val lensFacing: String,
    @SerialName("focal_length_mm") val focalLengthMm: Float? = null,
    @SerialName("exposure_time_ns") val exposureTimeNs: Long? = null,
    @SerialName("sensor_sensitivity_iso") val sensorSensitivityIso: Int? = null,
    @SerialName("focus_distance") val focusDistance: Float? = null,
    @SerialName("frame_duration_ns") val frameDurationNs: Long? = null,
    @SerialName("af_mode") val afMode: String? = null,
    @SerialName("af_state") val afState: String? = null,
    @SerialName("ae_mode") val aeMode: String? = null,
    @SerialName("ae_state") val aeState: String? = null,
    @SerialName("awb_mode") val awbMode: String? = null,
    @SerialName("awb_state") val awbState: String? = null,
    @SerialName("awb_lock") val awbLock: Boolean? = null,
    @SerialName("fps_range") val fpsRange: String? = null,
    @SerialName("crop_region") val cropRegion: String? = null,
    @SerialName("zoom_ratio") val zoomRatio: Float? = null,
    @SerialName("stabilization_mode") val stabilizationMode: String? = null,
    @SerialName("torch_state") val torchState: String? = null,
    @SerialName("frame_size_bytes") val frameSizeBytes: Int,
    @SerialName("scientific_state") val scientificState: String = "MEASURED",
    @SerialName("acquisition_type") val acquisitionType: String = "CAMERA_FRAME",
    @SerialName("representation") val representation: String = "ISP_PROCESSED",
    @SerialName("capture_id") val captureId: String? = null
)

@Serializable
data class DiscoveredCamera(
    val id: String,
    val isLogical: Boolean,
    val physicalCameraIds: List<String>,
    val parentLogicalCameraId: String? = null,
    val lensFacing: CameraFacing,
    val focalLengths: List<Float>,
    val sensorPhysicalSizeMm: Pair<Float, Float>?,
    val pixelArraySize: Pair<Int, Int>?,
    val activeArraySize: String?,
    val timestampSource: String,
    val hardwareLevel: String,
    val capabilities: List<String>,
    val hasRaw: Boolean,
    val hasLogicalMulti: Boolean,
    val supportedResolutions: List<Pair<Int, Int>>,
    val supportedFpsRanges: List<Pair<Int, Int>>,
    val friendlyName: String,
    val mpClass: String,
    val maxStreamResolution: String,
    val status: String,
    val concurrencyStatus: String,
    val independentlyOpenable: Boolean = true
)

@Serializable
data class ConcurrentCameraGroup(
    val cameraIds: List<String>,
    val description: String,
    val isSupported: Boolean
)

data class LiveCameraStats(
    val cameraId: String = "",
    val cameraLabel: String = "",
    val logicalCameraId: String? = null,
    val physicalCameraId: String? = null,
    val lensFacing: String = "",
    val hardwareLevel: String = "",
    val hasRaw: Boolean = false,
    val configuredFpsRange: String = "",
    val resolution: String = "1280x720",
    val observedFps: Double = 0.0,
    val totalFrames: Long = 0L,
    val droppedFrames: Long = 0L,
    val exposureTimeNs: Long? = null,
    val iso: Int? = null,
    val focalLengthMm: Float? = null,
    val focusDistance: Float? = null,
    val timestampNs: Long? = null,
    val bitrateKbps: Double = 0.0,
    val thermalStatus: String = "NORMAL"
)

data class ScientificCapturedFrame(
    val captureId: String,
    val cameraId: String,
    val cameraLabel: String,
    val timestampNs: Long,
    val width: Int,
    val height: Int,
    val exposureTimeNs: Long?,
    val iso: Int?,
    val focalLengthMm: Float?,
    val focusDistance: Float?,
    val jpegSizeBytes: Int,
    val jpegBytes: ByteArray
)

@Serializable
data class ControlParameters(
    @SerialName("af_mode") val afMode: String? = null,
    @SerialName("focus_distance_diopters") val focusDistanceDiopters: Float? = null,
    @SerialName("ae_mode") val aeMode: String? = null,
    @SerialName("exposure_time_ns") val exposureTimeNs: Long? = null,
    @SerialName("iso") val iso: Int? = null,
    @SerialName("frame_duration_ns") val frameDurationNs: Long? = null,
    @SerialName("ae_compensation") val aeCompensation: Int? = null,
    @SerialName("awb_mode") val awbMode: String? = null,
    @SerialName("awb_lock") val awbLock: Boolean? = null,
    @SerialName("fps_range") val fpsRange: String? = null,
    @SerialName("resolution") val resolution: String? = null,
    @SerialName("zoom_ratio") val zoomRatio: Float? = null,
    @SerialName("crop_region") val cropRegion: String? = null,
    @SerialName("optical_stabilization") val opticalStabilization: String? = null,
    @SerialName("video_stabilization") val videoStabilization: String? = null,
    @SerialName("torch") val torch: String? = null,
    @SerialName("stream_state") val streamState: String? = null
)

@Serializable
data class CameraControlMessage(
    @SerialName("schema_version") val schemaVersion: String = "1",
    @SerialName("message_type") val messageType: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("camera_id") val cameraId: String? = null,
    @SerialName("logical_camera_id") val logicalCameraId: String? = null,
    @SerialName("physical_camera_id") val physicalCameraId: String? = null,
    @SerialName("timestamp_utc") val timestampUtc: Long = 0L,
    @SerialName("parameters") val parameters: ControlParameters? = null
)

@Serializable
data class CameraControlResult(
    @SerialName("schema_version") val schemaVersion: String = "1",
    @SerialName("message_type") val messageType: String = "CAMERA_CONTROL_RESULT",
    @SerialName("request_id") val requestId: String,
    @SerialName("status") val status: String,
    @SerialName("requested") val requested: ControlParameters? = null,
    @SerialName("applied") val applied: ControlParameters? = null,
    @SerialName("error_reason") val errorReason: String? = null
)

@Serializable
data class CameraCapabilitiesPayload(
    @SerialName("manual_focus_supported") val manualFocusSupported: Boolean,
    @SerialName("min_focus_distance") val minFocusDistance: Float?,
    @SerialName("available_af_modes") val availableAfModes: List<String>,
    @SerialName("manual_sensor_supported") val manualSensorSupported: Boolean,
    @SerialName("exposure_time_range") val exposureTimeRange: List<Long>?,
    @SerialName("iso_range") val isoRange: List<Int>?,
    @SerialName("frame_duration_range") val frameDurationRange: List<Long>?,
    @SerialName("available_ae_modes") val availableAeModes: List<String>,
    @SerialName("ae_compensation_range") val aeCompensationRange: List<Int>?,
    @SerialName("ae_compensation_step") val aeCompensationStep: Float?,
    @SerialName("available_awb_modes") val availableAwbModes: List<String>,
    @SerialName("awb_lock_supported") val awbLockSupported: Boolean,
    @SerialName("available_fps_ranges") val availableFpsRanges: List<String>,
    @SerialName("supported_resolutions") val supportedResolutions: List<String>,
    @SerialName("zoom_ratio_range") val zoomRatioRange: List<Float>?,
    @SerialName("optical_stabilization_modes") val opticalStabilizationModes: List<String>,
    @SerialName("video_stabilization_modes") val videoStabilizationModes: List<String>,
    @SerialName("flash_supported") val flashSupported: Boolean,
    @SerialName("raw_sensor_supported") val rawSensorSupported: Boolean,
    @SerialName("yuv_supported") val yuvSupported: Boolean
)

@Serializable
data class CameraCapabilitiesResponse(
    @SerialName("schema_version") val schemaVersion: String = "1",
    @SerialName("message_type") val messageType: String = "CAMERA_CAPABILITIES",
    @SerialName("request_id") val requestId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("camera_id") val cameraId: String,
    @SerialName("capabilities") val capabilities: CameraCapabilitiesPayload
)

@Serializable
data class CameraStateResponse(
    @SerialName("schema_version") val schemaVersion: String = "1",
    @SerialName("message_type") val messageType: String = "CAMERA_STATE",
    @SerialName("request_id") val requestId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("camera_id") val cameraId: String,
    @SerialName("state") val state: ControlParameters
)

@Serializable
data class ChannelHelloMessage(
    @SerialName("schema_version") val schemaVersion: String = "1",
    @SerialName("message_type") val messageType: String = "CHANNEL_HELLO",
    @SerialName("device_id") val deviceId: String,
    val channel: String
)

@Serializable
data class CameraInventoryResponse(
    @SerialName("schema_version") val schemaVersion: String = "1",
    @SerialName("message_type") val messageType: String = "CAMERA_INVENTORY",
    @SerialName("request_id") val requestId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("cameras") val cameras: List<DiscoveredCamera>
)
