import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Replace updateRepeatingRequest
old_upd = re.search(r'private suspend fun updateRepeatingRequest\(.*?private suspend fun reconfigureSession', content, re.DOTALL)
new_upd = """private suspend fun updateRepeatingRequest(
        requestId: String,
        sendResult: (String) -> Unit,
        requested: ControlParameters
    ) {
        val device = activeCameraDevice ?: return
        val session = activeCaptureSession ?: return
        val previewReader = previewImageReader ?: return
        val handler = backgroundHandler ?: return
        
        try {
            val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewReader.surface)
            }
            val builderFailures = applyControlParametersToBuilder(requestBuilder, requested)
            
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                try {
                    session.setRepeatingRequest(
                        requestBuilder.build(),
                        object : CameraCaptureSession.CaptureCallback() {
                            private var appliedSent = false
                            
                            override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: TotalCaptureResult
                            ) {
                                latestCaptureResult = result
                                if (!appliedSent) {
                                    appliedSent = true
                                    
                                    val parameterResults = mutableMapOf<String, com.example.protocol.v1.ParameterResult>()
                                    
                                    // Iterate over all fields in requested
                                    fun addResult(name: String, reqVal: String?, appliedVal: String?) {
                                        if (reqVal != null) {
                                            val buildFail = builderFailures[name]
                                            if (buildFail != null) {
                                                parameterResults[name] = com.example.protocol.v1.ParameterResult(reqVal, null, buildFail)
                                            } else {
                                                parameterResults[name] = com.example.protocol.v1.ParameterResult(reqVal, appliedVal, "APPLIED")
                                            }
                                        }
                                    }
                                    
                                    val appliedAfMode = result.get(CaptureResult.CONTROL_AF_MODE)?.let { mode ->
                                        when(mode) {
                                            CaptureResult.CONTROL_AF_MODE_OFF -> "OFF"
                                            CaptureResult.CONTROL_AF_MODE_AUTO -> "AUTO"
                                            CaptureResult.CONTROL_AF_MODE_MACRO -> "MACRO"
                                            CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "CONTINUOUS_VIDEO"
                                            CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "CONTINUOUS_PICTURE"
                                            else -> "UNKNOWN"
                                        }
                                    } ?: "UNKNOWN"
                                    
                                    addResult("af_mode", requested.af_mode, appliedAfMode)
                                    addResult("focus_distance_diopters", requested.focus_distance_diopters?.toString(), result.get(CaptureResult.LENS_FOCUS_DISTANCE)?.toString())
                                    
                                    val appliedAeMode = result.get(CaptureResult.CONTROL_AE_MODE)?.let { mode ->
                                        when(mode) {
                                            CaptureResult.CONTROL_AE_MODE_OFF -> "OFF"
                                            CaptureResult.CONTROL_AE_MODE_ON -> "ON"
                                            else -> "UNKNOWN"
                                        }
                                    } ?: "UNKNOWN"
                                    
                                    addResult("ae_mode", requested.ae_mode, appliedAeMode)
                                    addResult("exposure_time_ns", requested.exposure_time_ns?.toString(), result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.toString())
                                    addResult("iso", requested.iso?.toString(), result.get(CaptureResult.SENSOR_SENSITIVITY)?.toString())
                                    addResult("frame_duration_ns", requested.frame_duration_ns?.toString(), result.get(CaptureResult.SENSOR_FRAME_DURATION)?.toString())
                                    addResult("ae_compensation", requested.ae_compensation?.toString(), result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)?.toString())
                                    
                                    val appliedAwbMode = result.get(CaptureResult.CONTROL_AWB_MODE)?.let { mode ->
                                        when(mode) {
                                            CaptureResult.CONTROL_AWB_MODE_OFF -> "OFF"
                                            CaptureResult.CONTROL_AWB_MODE_AUTO -> "AUTO"
                                            else -> "UNKNOWN"
                                        }
                                    } ?: "UNKNOWN"
                                    
                                    addResult("awb_mode", requested.awb_mode, appliedAwbMode)
                                    addResult("awb_lock", requested.awb_lock?.toString(), result.get(CaptureResult.CONTROL_AWB_LOCK)?.toString())
                                    
                                    val appliedFpsRange = result.get(CaptureResult.CONTROL_AE_TARGET_FPS_RANGE)?.let { "${it.lower}-${it.upper}" } ?: "UNKNOWN"
                                    addResult("fps_range", requested.fps_range, appliedFpsRange)
                                    
                                    addResult("resolution", requested.resolution, currentControlParams.resolution)
                                    addResult("zoom_ratio", requested.zoom_ratio?.toString(), result.get(CaptureResult.CONTROL_ZOOM_RATIO)?.toString())
                                    addResult("crop_region", requested.crop_region, result.get(CaptureResult.SCALER_CROP_REGION)?.let { "${it.left},${it.top},${it.right},${it.bottom}" })
                                    
                                    val appliedOptStab = result.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE)?.let { mode ->
                                        when(mode) {
                                            CaptureResult.LENS_OPTICAL_STABILIZATION_MODE_OFF -> "OFF"
                                            CaptureResult.LENS_OPTICAL_STABILIZATION_MODE_ON -> "ON"
                                            else -> "UNKNOWN"
                                        }
                                    } ?: "UNKNOWN"
                                    addResult("optical_stabilization", requested.optical_stabilization, appliedOptStab)
                                    
                                    val appliedVidStab = result.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE)?.let { mode ->
                                        when(mode) {
                                            CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_OFF -> "OFF"
                                            CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_ON -> "ON"
                                            else -> "UNKNOWN"
                                        }
                                    } ?: "UNKNOWN"
                                    addResult("video_stabilization", requested.video_stabilization, appliedVidStab)
                                    
                                    val appliedTorch = result.get(CaptureResult.FLASH_MODE)?.let { mode ->
                                        when(mode) {
                                            CaptureResult.FLASH_MODE_OFF -> "OFF"
                                            CaptureResult.FLASH_MODE_TORCH -> "TORCH"
                                            else -> "UNKNOWN"
                                        }
                                    } ?: "UNKNOWN"
                                    addResult("torch", requested.torch, appliedTorch)
                                    
                                    sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                                        request_id = requestId,
                                        device_id = activeDeviceId,
                                        overall_status = "APPLIED",
                                        parameter_results = parameterResults
                                    )))
                                    
                                    if (continuation.isActive) continuation.resume(Unit)
                                }
                            }
                            
                            override fun onCaptureFailed(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                failure: CaptureFailure
                            ) {
                                droppedFrames++
                                if (!appliedSent) {
                                    appliedSent = true
                                    sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                                        request_id = requestId,
                                        device_id = activeDeviceId,
                                        overall_status = "FAILED",
                                        error_message = "HAL_REJECTED"
                                    )))
                                    if (continuation.isActive) continuation.resume(Unit)
                                }
                            }
                        },
                        handler
                    )
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update repeating request", e)
            sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                request_id = requestId,
                device_id = activeDeviceId,
                overall_status = "FAILED",
                error_message = e.message ?: "UNKNOWN"
            )))
        }
    }
    
    """
if old_upd:
    content = content[:old_upd.start()] + new_upd + "private suspend fun reconfigureSession" + content[old_upd.end():]
else:
    print("Could not find updateRepeatingRequest")

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
