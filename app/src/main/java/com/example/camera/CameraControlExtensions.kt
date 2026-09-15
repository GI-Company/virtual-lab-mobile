package com.example.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.util.Size

fun CameraCharacteristics.getCameraCapabilitiesPayload(): CameraCapabilitiesPayload {
    val afModes = get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: IntArray(0)
    val aeModes = get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: IntArray(0)
    val awbModes = get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: IntArray(0)
    
    val hwLevel = get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
    val manualSensor = hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL || hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
    
    val minFocusDist = get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
    val manualFocus = minFocusDist != null && minFocusDist > 0f
    
    val expRange = get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
    val isoRange = get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
    val fdRange = get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION)
    
    val aeCompRange = get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
    val aeCompStep = get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
    
    val awbLock = get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) ?: false
    
    val fpsRanges = get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
    val streamMap = get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    val jpegSizes = streamMap?.getOutputSizes(android.graphics.ImageFormat.JPEG) ?: emptyArray()
    
    val maxZoom = get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
    
    val oisModes = get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: IntArray(0)
    val videoStabModes = get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: IntArray(0)
    
    val flashInfo = get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
    
    val capabilities = get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
    val rawSupported = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
    val yuvSupported = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING)

    val afModeList = afModes.map {
        when (it) {
            CameraMetadata.CONTROL_AF_MODE_OFF -> "OFF"
            CameraMetadata.CONTROL_AF_MODE_AUTO -> "AUTO"
            CameraMetadata.CONTROL_AF_MODE_MACRO -> "MACRO"
            CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "CONTINUOUS_VIDEO"
            CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "CONTINUOUS_PICTURE"
            CameraMetadata.CONTROL_AF_MODE_EDOF -> "EDOF"
            else -> "UNKNOWN_$it"
        }
    }

    val aeModeList = aeModes.map {
        when (it) {
            CameraMetadata.CONTROL_AE_MODE_OFF -> "OFF"
            CameraMetadata.CONTROL_AE_MODE_ON -> "ON"
            CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH -> "ON_AUTO_FLASH"
            CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH -> "ON_ALWAYS_FLASH"
            CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE -> "ON_AUTO_FLASH_REDEYE"
            else -> "UNKNOWN_$it"
        }
    }

    val awbModeList = awbModes.map {
        when (it) {
            CameraMetadata.CONTROL_AWB_MODE_OFF -> "OFF"
            CameraMetadata.CONTROL_AWB_MODE_AUTO -> "AUTO"
            CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT -> "INCANDESCENT"
            CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT -> "FLUORESCENT"
            CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "WARM_FLUORESCENT"
            CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT -> "DAYLIGHT"
            CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "CLOUDY_DAYLIGHT"
            CameraMetadata.CONTROL_AWB_MODE_TWILIGHT -> "TWILIGHT"
            CameraMetadata.CONTROL_AWB_MODE_SHADE -> "SHADE"
            else -> "UNKNOWN_$it"
        }
    }
    
    val oisModeList = oisModes.map {
        when (it) {
            CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF -> "OFF"
            CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON -> "ON"
            else -> "UNKNOWN_$it"
        }
    }
    
    val videoStabModeList = videoStabModes.map {
        when (it) {
            CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF -> "OFF"
            CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON -> "ON"
            else -> "UNKNOWN_$it"
        }
    }

    return CameraCapabilitiesPayload(
        manualFocusSupported = manualFocus,
        minFocusDistance = minFocusDist,
        availableAfModes = afModeList,
        manualSensorSupported = manualSensor,
        exposureTimeRange = expRange?.let { listOf(it.lower, it.upper) },
        isoRange = isoRange?.let { listOf(it.lower, it.upper) },
        frameDurationRange = fdRange?.let { listOf(0L, it) }, // max frame duration
        availableAeModes = aeModeList,
        aeCompensationRange = aeCompRange?.let { listOf(it.lower, it.upper) },
        aeCompensationStep = aeCompStep?.toFloat(),
        availableAwbModes = awbModeList,
        awbLockSupported = awbLock,
        availableFpsRanges = fpsRanges.map { "${it.lower}-${it.upper}" },
        supportedResolutions = jpegSizes.map { "${it.width}x${it.height}" },
        zoomRatioRange = listOf(1.0f, maxZoom),
        opticalStabilizationModes = oisModeList,
        videoStabilizationModes = videoStabModeList,
        flashSupported = flashInfo,
        rawSensorSupported = rawSupported,
        yuvSupported = yuvSupported
    )
}
