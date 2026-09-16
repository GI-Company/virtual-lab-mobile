import re

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    content = f.read()

# Replace the control message loop
old_loop = """        viewModelScope.launch {
            controlWebSocketClient.incomingMessages.collect { msg ->
                if (msg.deviceId != deviceId) {
                    controlWebSocketClient.sendResponse(Json.encodeToString(com.example.camera.CameraControlResult(
                        requestId = msg.requestId,
                        status = "REJECTED",
                        errorReason = "DEVICE_ID_MISMATCH"
                    )))
                    return@collect
                }
                cameraAcquisition.processControlCommand(msg) { responseJson: String ->
                    controlWebSocketClient.sendResponse(responseJson)
                }
            }
        }"""
new_loop = """        viewModelScope.launch {
            controlWebSocketClient.incomingMessages.collect { msg ->
                // device_id validation is now handled inside cameraAcquisition to properly unwrap the polymorphic type
                cameraAcquisition.processControlCommand(msg, deviceId) { responseJson: String ->
                    controlWebSocketClient.sendResponse(responseJson)
                }
            }
        }"""
content = content.replace(old_loop, new_loop)

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
    f.write(content)
