package com.example.camera

enum class CameraFacing {
    FRONT, BACK, EXTERNAL, UNKNOWN
}

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

enum class CameraMode {
    SINGLE,
    CONCURRENT,
    LOGICAL
}

data class CameraCapabilitiesPayload(
    val manualFocusSupported: Boolean,
    val minFocusDistance: Float?,
    val availableAfModes: List<String>,
    val manualSensorSupported: Boolean,
    val exposureTimeRange: List<Long>?,
    val isoRange: List<Int>?,
    val frameDurationRange: List<Long>?,
    val availableAeModes: List<String>,
    val aeCompensationRange: List<Int>?,
    val aeCompensationStep: Float?,
    val availableAwbModes: List<String>,
    val awbLockSupported: Boolean,
    val availableFpsRanges: List<List<Int>>,
    val supportedResolutions: List<List<Int>>,
    val zoomRatioRange: List<Float>?,
    val opticalStabilizationModes: List<String>,
    val videoStabilizationModes: List<String>,
    val flashSupported: Boolean,
    val rawSensorSupported: Boolean,
    val yuvSupported: Boolean
)
