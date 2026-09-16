import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Replace processControlCommand fully
old_process = re.search(r'suspend fun processControlCommand.*?suspend fun captureScientificFrame', content, re.DOTALL)
if old_process:
    new_process = """suspend fun processControlCommand(command: BaseMessage, deviceId: String, sendResult: (String) -> Unit) {
        val reqDeviceId = when (command) {
            is CameraControlRequestMessage -> command.device_id
            is ScientificCaptureRequestMessage -> command.device_id
            is CameraInventoryMessage -> command.device_id
            else -> null
        }
        
        if (reqDeviceId != null && reqDeviceId != deviceId) {
            val reqId = when (command) {
                is CameraControlRequestMessage -> command.request_id
                is ScientificCaptureRequestMessage -> command.request_id
                is CameraInventoryMessage -> command.request_id
                else -> ""
            }
            if (command is CameraControlRequestMessage) {
                sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                    request_id = reqId,
                    device_id = deviceId,
                    overall_status = "REJECTED",
                    error_message = "INVALID_DEVICE_ID"
                )))
            } else if (command is ScientificCaptureRequestMessage) {
                sendResult(ProtocolSerializer.serialize(ScientificCaptureResultMessage(
                    request_id = reqId,
                    device_id = deviceId,
                    status = "FAILED",
                    error_message = "INVALID_DEVICE_ID"
                )))
            }
            return
        }

        if (command is CameraInventoryMessage) {
            val cameras = cameraInventory.discoverCameras().map { c ->
                DiscoveredCameraModel(
                    id = c.id,
                    is_logical = c.isLogical,
                    physical_camera_ids = c.physicalCameraIds,
                    parent_logical_camera_id = c.parentLogicalCameraId,
                    lens_facing = c.lensFacing.name,
                    focal_lengths = c.focalLengths,
                    sensor_physical_size_mm = c.sensorPhysicalSizeMm?.let { listOf(it.first, it.second) },
                    pixel_array_size = c.pixelArraySize?.let { listOf(it.first, it.second) },
                    active_array_size = c.activeArraySize,
                    timestamp_source = c.timestampSource,
                    hardware_level = c.hardwareLevel,
                    capabilities = c.capabilities,
                    has_raw = c.hasRaw,
                    has_logical_multi = c.hasLogicalMulti,
                    supported_resolutions = c.supportedResolutions.map { "${it.first}x${it.second}" },
                    supported_fps_ranges = c.supportedFpsRanges.map { "${it.first}-${it.second}" },
                    friendly_name = c.friendlyName,
                    mp_class = c.mpClass,
                    max_stream_resolution = c.maxStreamResolution,
                    status = c.status,
                    concurrency_status = c.concurrencyStatus,
                    independently_openable = c.independentlyOpenable
                )
            }
            sendResult(ProtocolSerializer.serialize(CameraInventoryMessage(
                request_id = command.request_id,
                device_id = deviceId,
                cameras = cameras
            )))
            return
        }

        val requestId = when (command) {
            is CameraControlRequestMessage -> command.request_id
            is ScientificCaptureRequestMessage -> command.request_id
            else -> ""
        }

        if (command is CameraControlRequestMessage) {
            when (command.control_type) {
                "GET_CAPABILITIES" -> {
                    val targetCameraId = command.camera_stream_key?.camera_id ?: activeCameraId
                    if (targetCameraId.isEmpty()) {
                        sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                            request_id = requestId,
                            device_id = deviceId,
                            overall_status = "FAILED",
                            error_message = "NO_CAMERA_TARGET"
                        )))
                        return
                    }
                    try {
                        val chars = cameraManager.getCameraCharacteristics(targetCameraId)
                        val caps = chars.getCameraCapabilitiesPayload()
                        sendResult(ProtocolSerializer.serialize(CameraCapabilitiesMessage(
                            request_id = requestId,
                            device_id = deviceId,
                            camera_stream_key = CameraStreamKey(targetCameraId),
                            manual_focus_supported = caps.manualFocusSupported,
                            min_focus_distance = caps.minFocusDistance,
                            available_af_modes = caps.availableAfModes,
                            manual_sensor_supported = caps.manualSensorSupported,
                            exposure_time_range = caps.exposureTimeRange,
                            iso_range = caps.isoRange,
                            frame_duration_range = caps.frameDurationRange,
                            available_ae_modes = caps.availableAeModes,
                            ae_compensation_range = caps.aeCompensationRange,
                            ae_compensation_step = caps.aeCompensationStep,
                            available_awb_modes = caps.availableAwbModes,
                            awb_lock_supported = caps.awbLockSupported,
                            available_fps_ranges = caps.availableFpsRanges,
                            supported_resolutions = caps.supportedResolutions,
                            zoom_ratio_range = caps.zoomRatioRange,
                            optical_stabilization_modes = caps.opticalStabilizationModes,
                            video_stabilization_modes = caps.videoStabilizationModes,
                            flash_supported = caps.flashSupported,
                            raw_sensor_supported = caps.rawSensorSupported,
                            yuv_supported = caps.yuvSupported
                        )))
                    } catch (e: Exception) {
                        sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                            request_id = requestId,
                            device_id = deviceId,
                            overall_status = "FAILED",
                            error_message = "CAMERA_ACCESS_ERROR"
                        )))
                    }
                    return
                }
                "SELECT_CAMERA", "START_CAMERA_STREAM" -> {
                    val targetCameraId = command.camera_stream_key?.camera_id
                    if (targetCameraId.isNullOrEmpty()) {
                        sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                            request_id = requestId,
                            device_id = deviceId,
                            overall_status = "FAILED",
                            error_message = "NO_CAMERA_TARGET"
                        )))
                        return
                    }
                    val camera = cameraInventory.discoverCameras().find { it.id == targetCameraId }
                    if (camera == null) {
                        sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                            request_id = requestId,
                            device_id = deviceId,
                            overall_status = "FAILED",
                            error_message = "CAMERA_NOT_FOUND"
                        )))
                        return
                    }
                    startCameraStream(camera, deviceId)
                    sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                        request_id = requestId,
                        device_id = deviceId,
                        overall_status = "APPLIED"
                    )))
                    return
                }
                "STOP_CAMERA_STREAM" -> {
                    stopCameraStream()
                    sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                        request_id = requestId,
                        device_id = deviceId,
                        overall_status = "APPLIED"
                    )))
                    return
                }
                "GET_STATE" -> {
                    sendResult(ProtocolSerializer.serialize(CameraStateMessage(
                        request_id = requestId,
                        device_id = deviceId,
                        camera_stream_key = CameraStreamKey(activeCameraId, activeParentLogicalId, if (isPhysicalMember) activeCameraId else null),
                        resolution = currentControlParams.resolution,
                        af_mode = currentControlParams.afMode,
                        focus_distance_diopters = currentControlParams.focusDistanceDiopters,
                        ae_mode = currentControlParams.aeMode,
                        exposure_time_ns = currentControlParams.exposureTimeNs,
                        iso = currentControlParams.iso,
                        frame_duration_ns = currentControlParams.frameDurationNs,
                        ae_compensation = currentControlParams.aeCompensation,
                        awb_mode = currentControlParams.awbMode,
                        awb_lock = currentControlParams.awbLock,
                        fps_range = currentControlParams.fpsRange,
                        zoom_ratio = currentControlParams.zoomRatio,
                        crop_region = currentControlParams.cropRegion,
                        optical_stabilization = currentControlParams.opticalStabilization,
                        video_stabilization = currentControlParams.videoStabilization,
                        torch = currentControlParams.torch
                    )))
                    return
                }
                "SET_PARAMETERS" -> {
                    val requested = command.requested_parameters
                    if (requested != null) {
                        val oldRes = currentControlParams.resolution
                        val newRes = requested.resolution ?: oldRes
                        val needsReconfig = newRes != oldRes
                        
                        val newParams = com.example.camera.ControlParameters(
                            afMode = requested.af_mode ?: currentControlParams.afMode,
                            focusDistanceDiopters = requested.focus_distance_diopters ?: currentControlParams.focusDistanceDiopters,
                            aeMode = requested.ae_mode ?: currentControlParams.aeMode,
                            exposureTimeNs = requested.exposure_time_ns ?: currentControlParams.exposureTimeNs,
                            iso = requested.iso ?: currentControlParams.iso,
                            frameDurationNs = requested.frame_duration_ns ?: currentControlParams.frameDurationNs,
                            aeCompensation = requested.ae_compensation ?: currentControlParams.aeCompensation,
                            awbMode = requested.awb_mode ?: currentControlParams.awbMode,
                            awbLock = requested.awb_lock ?: currentControlParams.awbLock,
                            fpsRange = requested.fps_range ?: currentControlParams.fpsRange,
                            resolution = newRes,
                            zoomRatio = requested.zoom_ratio ?: currentControlParams.zoomRatio,
                            cropRegion = requested.crop_region ?: currentControlParams.cropRegion,
                            opticalStabilization = requested.optical_stabilization ?: currentControlParams.opticalStabilization,
                            videoStabilization = requested.video_stabilization ?: currentControlParams.videoStabilization,
                            torch = requested.torch ?: currentControlParams.torch
                        )

                        if (needsReconfig) {
                            reconfigureSession(requestId, sendResult, newParams)
                        } else {
                            updateRepeatingRequest(requestId, sendResult, newParams)
                        }
                    } else {
                        sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                            request_id = requestId,
                            device_id = deviceId,
                            overall_status = "FAILED",
                            error_message = "MISSING_PARAMETERS"
                        )))
                    }
                    return
                }
            }
        }
        
        if (command is ScientificCaptureRequestMessage) {
             captureScientificFrame(command.request_id, sendResult)
             return
        }
        
        if (command is ScientificFrameAckMessage) {
             if (command.status == "COMMITTED") {
                 captureState[command.request_id] = "COMMITTED"
             } else {
                 captureState[command.request_id] = "REJECTED"
             }
             return
        }
    }
    
    """
    
    content = content[:old_process.start()] + new_process + "\n    " + content[old_process.end():]
    with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
        f.write(content)
else:
    print("Could not find processControlCommand")
