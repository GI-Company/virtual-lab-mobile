import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# I need to fix how parameterResults is built.
# Right now we have:
# fun addResult(name: String, reqVal: String?, appliedVal: String?) {
# We need to change that to accept Any? and convert it to JsonElement using kotlinx.serialization.json.JsonPrimitive

new_add_result = """
                                    fun addResult(name: String, reqVal: Any?, appliedVal: Any?) {
                                        if (reqVal != null) {
                                            val buildFail = builderFailures[name]
                                            val reqJson = when (reqVal) {
                                                is Number -> kotlinx.serialization.json.JsonPrimitive(reqVal)
                                                is Boolean -> kotlinx.serialization.json.JsonPrimitive(reqVal)
                                                is String -> kotlinx.serialization.json.JsonPrimitive(reqVal)
                                                else -> kotlinx.serialization.json.JsonPrimitive(reqVal.toString())
                                            }
                                            val appJson = if (appliedVal == null) null else when (appliedVal) {
                                                is Number -> kotlinx.serialization.json.JsonPrimitive(appliedVal)
                                                is Boolean -> kotlinx.serialization.json.JsonPrimitive(appliedVal)
                                                is String -> kotlinx.serialization.json.JsonPrimitive(appliedVal)
                                                else -> kotlinx.serialization.json.JsonPrimitive(appliedVal.toString())
                                            }
                                            
                                            var paramStatus = if (buildFail != null) buildFail else "APPLIED"
                                            if (paramStatus == "APPLIED" && reqJson != appJson && appJson != null) {
                                                paramStatus = "PARTIALLY_APPLIED"
                                            }
                                            
                                            parameterResults[name] = com.example.protocol.v1.ParameterResult(reqJson, appJson, paramStatus)
                                        }
                                    }
"""

old_add_result_pattern = r'fun addResult\(name: String, reqVal: String\?, appliedVal: String\?\).*?\}'
old_add_result_match = re.search(old_add_result_pattern, content, re.DOTALL)
if old_add_result_match:
    content = content[:old_add_result_match.start()] + new_add_result.strip() + content[old_add_result_match.end():]
else:
    print("Could not find addResult")

# Now I need to update all addResult calls to not use .toString() where appropriate
content = content.replace("requested.focus_distance_diopters?.toString()", "requested.focus_distance_diopters")
content = content.replace("result.get(CaptureResult.LENS_FOCUS_DISTANCE)?.toString()", "result.get(CaptureResult.LENS_FOCUS_DISTANCE)")

content = content.replace("requested.exposure_time_ns?.toString()", "requested.exposure_time_ns")
content = content.replace("result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.toString()", "result.get(CaptureResult.SENSOR_EXPOSURE_TIME)")

content = content.replace("requested.iso?.toString()", "requested.iso")
content = content.replace("result.get(CaptureResult.SENSOR_SENSITIVITY)?.toString()", "result.get(CaptureResult.SENSOR_SENSITIVITY)")

content = content.replace("requested.frame_duration_ns?.toString()", "requested.frame_duration_ns")
content = content.replace("result.get(CaptureResult.SENSOR_FRAME_DURATION)?.toString()", "result.get(CaptureResult.SENSOR_FRAME_DURATION)")

content = content.replace("requested.ae_compensation?.toString()", "requested.ae_compensation")
content = content.replace("result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)?.toString()", "result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)")

content = content.replace("requested.awb_lock?.toString()", "requested.awb_lock")
content = content.replace("result.get(CaptureResult.CONTROL_AWB_LOCK)?.toString()", "result.get(CaptureResult.CONTROL_AWB_LOCK)")

content = content.replace("requested.zoom_ratio?.toString()", "requested.zoom_ratio")
content = content.replace("result.get(CaptureResult.CONTROL_ZOOM_RATIO)?.toString()", "result.get(CaptureResult.CONTROL_ZOOM_RATIO)")

# Calculate overall_status based on parameter results
# We have: overall_status = "APPLIED" inside CameraControlResultMessage
# Let's dynamically calculate it.
old_result_message = """sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                                        request_id = requestId,
                                        device_id = activeDeviceId,
                                        overall_status = "APPLIED",
                                        parameter_results = parameterResults
                                    )))"""
new_result_message = """
                                    var overallStatus = "APPLIED"
                                    if (parameterResults.values.any { it.status == "FAILED" || it.status == "REJECTED" || it.status == "REJECTED_PHYSICAL_CAMERA" }) {
                                        overallStatus = "PARTIALLY_APPLIED" // Or FAILED? "Derive overall_status from the per-parameter results."
                                    }
                                    if (parameterResults.isNotEmpty() && parameterResults.values.all { it.status == "REJECTED_PHYSICAL_CAMERA" }) {
                                        overallStatus = "REJECTED"
                                    }
                                    
                                    sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                                        request_id = requestId,
                                        device_id = activeDeviceId,
                                        overall_status = overallStatus,
                                        parameter_results = parameterResults
                                    )))"""
content = content.replace(old_result_message, new_result_message)

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
