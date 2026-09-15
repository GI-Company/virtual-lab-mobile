package com.example.camera

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("frame_size_bytes") val frameSizeBytes: Int,
    @SerialName("scientific_state") val scientificState: String = "MEASURED",
    @SerialName("acquisition_type") val acquisitionType: String = "CAMERA_FRAME",
    @SerialName("representation") val representation: String = "ISP_PROCESSED",
    @SerialName("capture_id") val captureId: String? = null
)

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
    val concurrencyStatus: String
)

data class ConcurrentCameraGroup(
    val cameraIds: List<String>,
    val description: String,
    val isSupported: Boolean
)

data class LiveCameraStats(
    val cameraId: String = "",
    val cameraLabel: String = "",
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
