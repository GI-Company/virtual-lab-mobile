package com.example.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Log

class CameraHardwareInventory(private val context: Context) {
    companion object {
        private const val TAG = "CameraHardwareInventory"
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun discoverCameras(): List<DiscoveredCamera> {
        val discoveredList = mutableListOf<DiscoveredCamera>()
        try {
            val cameraIds = cameraManager.cameraIdList
            val concurrentCombinations = getConcurrentCameraCombinations()

            for (id in cameraIds) {
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val discovered = inspectCamera(id, chars, concurrentCombinations, null)
                    discoveredList.add(discovered)

                    // If logical multi-camera, also inspect physical cameras
                    if (discovered.hasLogicalMulti) {
                        for (physId in discovered.physicalCameraIds) {
                            try {
                                val physChars = cameraManager.getCameraCharacteristics(physId)
                                val physCamera = inspectCamera(
                                    id = physId,
                                    chars = physChars,
                                    concurrentGroups = concurrentCombinations,
                                    parentLogicalId = id
                                )
                                discoveredList.add(physCamera)
                            } catch (e: Exception) {
                                Log.w(TAG, "Could not inspect physical camera $physId: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed inspecting camera $id: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed retrieving camera ID list: ${e.message}", e)
        }
        return discoveredList
    }

    fun getConcurrentCameraCombinations(): List<ConcurrentCameraGroup> {
        val groups = mutableListOf<ConcurrentCameraGroup>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val concurrentSets = cameraManager.concurrentCameraIds
                for (set in concurrentSets) {
                    val ids = set.toList()
                    val desc = ids.joinToString(" + ") { "Camera $it" }
                    groups.add(
                        ConcurrentCameraGroup(
                            cameraIds = ids,
                            description = desc,
                            isSupported = true
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying concurrent camera IDs: ${e.message}")
            }
        }
        return groups
    }

    private fun inspectCamera(
        id: String,
        chars: CameraCharacteristics,
        concurrentGroups: List<ConcurrentCameraGroup>,
        parentLogicalId: String?
    ): DiscoveredCamera {
        val facingInt = chars.get(CameraCharacteristics.LENS_FACING)
        val facing = when (facingInt) {
            CameraCharacteristics.LENS_FACING_FRONT -> CameraFacing.FRONT
            CameraCharacteristics.LENS_FACING_BACK -> CameraFacing.BACK
            CameraCharacteristics.LENS_FACING_EXTERNAL -> CameraFacing.EXTERNAL
            else -> CameraFacing.UNKNOWN
        }

        val capabilitiesArray = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val capabilities = parseCapabilities(capabilitiesArray)
        val hasRaw = capabilitiesArray.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        val hasLogicalMulti = capabilitiesArray.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)

        val physicalIds = if (hasLogicalMulti && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            chars.physicalCameraIds.toList()
        } else {
            emptyList()
        }

        val hwLevelInt = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val hwLevel = when (hwLevelInt) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN ($hwLevelInt)"
        }

        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList() ?: emptyList()

        val physSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.let {
            Pair(it.width, it.height)
        }

        val pixelArraySize = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)?.let {
            Pair(it.width, it.height)
        }

        val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.toString()

        val timestampSourceInt = chars.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)
        val timestampSource = if (timestampSourceInt == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) {
            "REALTIME"
        } else {
            "UNKNOWN"
        }

        val streamConfig = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSizes = streamConfig?.getOutputSizes(ImageFormat.JPEG)?.map { Pair(it.width, it.height) } ?: emptyList()

        val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.map {
            Pair(it.lower, it.upper)
        } ?: emptyList()

        val maxStreamRes = if (jpegSizes.isNotEmpty()) {
            val max = jpegSizes.maxByOrNull { it.first * it.second }!!
            "${max.first}x${max.second}"
        } else {
            "UNAVAILABLE"
        }

        val mpCount = pixelArraySize?.let { (it.first.toLong() * it.second) / 1_000_000.0 } ?: 0.0
        val mpClass = if (mpCount > 0) {
            "${Math.round(mpCount)} MP class"
        } else {
            "Unknown MP"
        }

        val isLogical = hasLogicalMulti && parentLogicalId == null
        val isPhysicalMember = parentLogicalId != null

        val friendlyName = deriveFriendlyName(
            facing = facing,
            focalLengths = focalLengths,
            isLogical = isLogical,
            isPhysicalMember = isPhysicalMember,
            id = id,
            parentLogicalId = parentLogicalId
        )

        val status = if (isPhysicalMember) {
            "LOGICAL CAMERA MEMBER"
        } else {
            "AVAILABLE"
        }

        val isConcurrentlyStreamable = concurrentGroups.any { it.cameraIds.contains(id) }
        val concurrencyStatus = when {
            isPhysicalMember -> "LOGICAL CAMERA MEMBER"
            isConcurrentlyStreamable -> "CONCURRENTLY STREAMABLE"
            else -> "INDIVIDUALLY STREAMABLE"
        }

        return DiscoveredCamera(
            id = id,
            isLogical = isLogical,
            physicalCameraIds = physicalIds,
            parentLogicalCameraId = parentLogicalId,
            lensFacing = facing,
            focalLengths = focalLengths,
            sensorPhysicalSizeMm = physSize,
            pixelArraySize = pixelArraySize,
            activeArraySize = activeArray,
            timestampSource = timestampSource,
            hardwareLevel = hwLevel,
            capabilities = capabilities,
            hasRaw = hasRaw,
            hasLogicalMulti = hasLogicalMulti,
            supportedResolutions = jpegSizes,
            supportedFpsRanges = fpsRanges,
            friendlyName = friendlyName,
            mpClass = mpClass,
            maxStreamResolution = maxStreamRes,
            status = status,
            concurrencyStatus = concurrencyStatus
        )
    }

    private fun deriveFriendlyName(
        facing: CameraFacing,
        focalLengths: List<Float>,
        isLogical: Boolean,
        isPhysicalMember: Boolean,
        id: String,
        parentLogicalId: String?
    ): String {
        if (facing == CameraFacing.FRONT) {
            return "Front Camera (ID: $id)"
        }

        val focal = focalLengths.firstOrNull() ?: 0f
        val suffix = when {
            focal in 0.1f..3.0f -> "Ultra-Wide"
            focal > 6.0f -> "Telephoto"
            focal > 3.0f -> "Wide"
            else -> "Camera"
        }

        return when {
            isLogical -> "Rear Logical ($suffix, ID: $id)"
            isPhysicalMember -> "Rear Physical $suffix (ID: $id, parent: $parentLogicalId)"
            else -> "Rear $suffix (ID: $id)"
        }
    }

    private fun parseCapabilities(caps: IntArray): List<String> {
        val list = mutableListOf<String>()
        for (c in caps) {
            when (c) {
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> list.add("BACKWARD_COMPATIBLE")
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> list.add("MANUAL_SENSOR")
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> list.add("MANUAL_POST_PROCESSING")
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> list.add("RAW")
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING -> list.add("PRIVATE_REPROCESSING")
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS -> list.add("READ_SENSOR_SETTINGS")
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> list.add("BURST_CAPTURE")
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING -> list.add("YUV_REPROCESSING")
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> list.add("DEPTH_OUTPUT")
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> list.add("LOGICAL_MULTI_CAMERA")
                else -> list.add("CAPABILITY_$c")
            }
        }
        return list
    }
}
