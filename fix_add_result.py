import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# I will replace from `fun addResult(name: String, reqVal: Any?, appliedVal: Any?) {` 
# up to `addResult("af_mode", requested.af_mode, result.get(CaptureResult.CONTROL_AF_MODE))`

# Let's find exactly where it goes wrong.
start_str = "fun addResult(name: String, reqVal: Any?, appliedVal: Any?) {"
end_str = "addResult(\"af_mode\", requested.af_mode, result.get(CaptureResult.CONTROL_AF_MODE))"

start_idx = content.find(start_str)
end_idx = content.find(end_str)

if start_idx != -1 and end_idx != -1:
    new_add_result = """fun addResult(name: String, reqVal: Any?, appliedVal: Any?) {
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
    content = content[:start_idx] + new_add_result + content[end_idx:]

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)

