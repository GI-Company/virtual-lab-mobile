import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Replace setKey to return Boolean indicating success
old_setkey = """    private fun <T> setKey(builder: CaptureRequest.Builder, key: CaptureRequest.Key<T>, value: T) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && isPhysicalMember && activeParentLogicalId != null) {
            try {
                // Not all keys are supported for physical camera requests.
                // CameraDevice.isSessionConfigurationSupported can check, but for now we try/catch or just set.
                // We'll just call setPhysicalCameraKey
                builder.setPhysicalCameraKey(key, value, activeCameraId)
            } catch (e: Exception) {
                Log.w(TAG, "Unsupported physical key: ${key.name}")
            }
        } else {
            builder.set(key, value)
        }
    }"""
new_setkey = """    private fun <T> setKey(builder: CaptureRequest.Builder, key: CaptureRequest.Key<T>, value: T): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && isPhysicalMember && activeParentLogicalId != null) {
            return try {
                builder.setPhysicalCameraKey(key, value, activeCameraId)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Unsupported physical key: ${key.name}")
                false
            }
        } else {
            builder.set(key, value)
            return true
        }
    }"""
content = content.replace(old_setkey, new_setkey)

# We'll skip rewriting applyControlParametersToBuilder fully, and instead just track in updateRepeatingRequest directly. But wait, applyControlParametersToBuilder uses setKey.
# Let's change applyControlParametersToBuilder to track failures.

old_apply = re.search(r'private fun applyControlParametersToBuilder\(.*?\}\n    \}\n', content, re.DOTALL)
new_apply = """    private fun applyControlParametersToBuilder(builder: CaptureRequest.Builder, requestedParams: com.example.protocol.v1.ControlParameters? = null): Map<String, String> {
        val params = currentControlParams
        val failures = mutableMapOf<String, String>()
        
        fun track(paramName: String, success: Boolean) {
            if (!success) failures[paramName] = "REJECTED_PHYSICAL_CAMERA"
        }

        // Base auto mode if no manual controls are specified
        setKey(builder, CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        
        // Focus
        when (params.afMode) {
            "OFF", "MANUAL" -> {
                track("af_mode", setKey(builder, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF))
                params.focusDistanceDiopters?.let {
                    track("focus_distance_diopters", setKey(builder, CaptureRequest.LENS_FOCUS_DISTANCE, it))
                }
            }
            "AUTO" -> track("af_mode", setKey(builder, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO))
            "MACRO" -> track("af_mode", setKey(builder, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_MACRO))
            "CONTINUOUS_VIDEO" -> track("af_mode", setKey(builder, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO))
            "CONTINUOUS_PICTURE" -> track("af_mode", setKey(builder, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE))
            "EDOF" -> track("af_mode", setKey(builder, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_EDOF))
            else -> track("af_mode", setKey(builder, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE))
        }
        
        // Exposure
        when (params.aeMode) {
            "OFF", "MANUAL" -> {
                track("ae_mode", setKey(builder, CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF))
                params.exposureTimeNs?.let {
                    track("exposure_time_ns", setKey(builder, CaptureRequest.SENSOR_EXPOSURE_TIME, it))
                }
                params.iso?.let {
                    track("iso", setKey(builder, CaptureRequest.SENSOR_SENSITIVITY, it))
                }
                params.frameDurationNs?.let {
                    track("frame_duration_ns", setKey(builder, CaptureRequest.SENSOR_FRAME_DURATION, it))
                }
            }
            "ON" -> track("ae_mode", setKey(builder, CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON))
            "ON_AUTO_FLASH" -> track("ae_mode", setKey(builder, CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH))
            "ON_ALWAYS_FLASH" -> track("ae_mode", setKey(builder, CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH))
            "ON_AUTO_FLASH_REDEYE" -> track("ae_mode", setKey(builder, CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE))
            else -> track("ae_mode", setKey(builder, CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON))
        }
        
        params.aeCompensation?.let {
            track("ae_compensation", setKey(builder, CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, it))
        }
        
        // AWB
        when (params.awbMode) {
            "OFF" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF))
            "AUTO" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO))
            "INCANDESCENT" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT))
            "FLUORESCENT" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT))
            "WARM_FLUORESCENT" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT))
            "DAYLIGHT" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT))
            "CLOUDY_DAYLIGHT" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT))
            "TWILIGHT" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_TWILIGHT))
            "SHADE" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_SHADE))
            else -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO))
        }
        
        params.awbLock?.let {
            track("awb_lock", setKey(builder, CaptureRequest.CONTROL_AWB_LOCK, it))
        }
        
        // FPS Range
        params.fpsRange?.let { fpsStr ->
            try {
                val parts = fpsStr.split("-")
                if (parts.size == 2) {
                    val lower = parts[0].toInt()
                    val upper = parts[1].toInt()
                    track("fps_range", setKey(builder, CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(lower, upper)))
                }
            } catch (_: Exception) {}
        }
        
        // Zoom
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            params.zoomRatio?.let {
                track("zoom_ratio", setKey(builder, CaptureRequest.CONTROL_ZOOM_RATIO, it))
            }
        }
        
        // Stabilization
        when (params.opticalStabilization) {
            "ON" -> track("optical_stabilization", setKey(builder, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON))
            "OFF" -> track("optical_stabilization", setKey(builder, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF))
        }
        
        when (params.videoStabilization) {
            "ON" -> track("video_stabilization", setKey(builder, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON))
            "OFF" -> track("video_stabilization", setKey(builder, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF))
        }
        
        // Torch
        when (params.torch) {
            "ON", "TORCH" -> track("torch", setKey(builder, CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH))
            "OFF" -> track("torch", setKey(builder, CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF))
        }
        
        return failures
    }
"""

if old_apply:
    content = content[:old_apply.start()] + new_apply + content[old_apply.end():]
else:
    print("Could not find applyControlParametersToBuilder")

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
