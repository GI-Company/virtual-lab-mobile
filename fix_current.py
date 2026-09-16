import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

old_start = """                                if (!appliedSent && request.tag == requestId) {
                                    appliedSent = true
                                    currentControlParams = requested
                                    appliedSent = true"""

new_start = """                                if (!appliedSent && request.tag == requestId) {
                                    appliedSent = true"""

content = content.replace(old_start, new_start)


# Find where overallStatus is calculated and ParameterResultMessage is built.
old_end = """                                    if (parameterResults.isNotEmpty()) {
                                        val allApplied = parameterResults.values.all { it.status == "APPLIED" }
                                        val allUnconfirmed = parameterResults.values.all { it.status == "UNCONFIRMED" }
                                        val allRejected = parameterResults.values.all { it.status == "REJECTED" }
                                        
                                        if (allApplied) {
                                            overallStatus = "APPLIED"
                                        } else if (allUnconfirmed) {
                                            overallStatus = "UNCONFIRMED"
                                        } else if (allRejected) {
                                            overallStatus = "REJECTED"
                                        } else {
                                            overallStatus = "PARTIALLY_APPLIED"
                                        }
                                    }
                                    
                                    sendResult(ProtocolSerializer.serialize(CameraControlResultMessage("""

new_end = """                                    if (parameterResults.isNotEmpty()) {
                                        val allApplied = parameterResults.values.all { it.status == "APPLIED" }
                                        val allUnconfirmed = parameterResults.values.all { it.status == "UNCONFIRMED" }
                                        val allRejected = parameterResults.values.all { it.status == "REJECTED" }
                                        
                                        if (allApplied) {
                                            overallStatus = "APPLIED"
                                        } else if (allUnconfirmed) {
                                            overallStatus = "UNCONFIRMED"
                                        } else if (allRejected) {
                                            overallStatus = "REJECTED"
                                        } else {
                                            overallStatus = "PARTIALLY_APPLIED"
                                        }
                                    }
                                    
                                    val safeParams = com.example.protocol.v1.ControlParameters(
                                        af_mode = if (parameterResults["af_mode"]?.status == "APPLIED") requested.af_mode else currentControlParams.af_mode,
                                        focus_distance_diopters = if (parameterResults["focus_distance_diopters"]?.status == "APPLIED") requested.focus_distance_diopters else currentControlParams.focus_distance_diopters,
                                        ae_mode = if (parameterResults["ae_mode"]?.status == "APPLIED") requested.ae_mode else currentControlParams.ae_mode,
                                        exposure_time_ns = if (parameterResults["exposure_time_ns"]?.status == "APPLIED") requested.exposure_time_ns else currentControlParams.exposure_time_ns,
                                        iso = if (parameterResults["iso"]?.status == "APPLIED") requested.iso else currentControlParams.iso,
                                        frame_duration_ns = if (parameterResults["frame_duration_ns"]?.status == "APPLIED") requested.frame_duration_ns else currentControlParams.frame_duration_ns,
                                        ae_compensation = if (parameterResults["ae_compensation"]?.status == "APPLIED") requested.ae_compensation else currentControlParams.ae_compensation,
                                        awb_mode = if (parameterResults["awb_mode"]?.status == "APPLIED") requested.awb_mode else currentControlParams.awb_mode,
                                        awb_lock = if (parameterResults["awb_lock"]?.status == "APPLIED") requested.awb_lock else currentControlParams.awb_lock,
                                        fps_range = if (parameterResults["fps_range"]?.status == "APPLIED") requested.fps_range else currentControlParams.fps_range,
                                        resolution = requested.resolution,
                                        zoom_ratio = if (parameterResults["zoom_ratio"]?.status == "APPLIED") requested.zoom_ratio else currentControlParams.zoom_ratio,
                                        crop_region = if (parameterResults["crop_region"]?.status == "APPLIED") requested.crop_region else currentControlParams.crop_region,
                                        optical_stabilization = if (parameterResults["optical_stabilization"]?.status == "APPLIED") requested.optical_stabilization else currentControlParams.optical_stabilization,
                                        video_stabilization = if (parameterResults["video_stabilization"]?.status == "APPLIED") requested.video_stabilization else currentControlParams.video_stabilization,
                                        torch = if (parameterResults["torch"]?.status == "APPLIED") requested.torch else currentControlParams.torch,
                                        stream_state = currentControlParams.stream_state
                                    )
                                    currentControlParams = safeParams
                                    
                                    sendResult(ProtocolSerializer.serialize(CameraControlResultMessage("""

content = content.replace(old_end, new_end)

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)

