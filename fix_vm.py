import re

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    content = f.read()

old_setup = """    private fun setupCameraFrameForwarding() {
        cameraFrameForwardJob = cameraAcquisition.frameFlow.onEach { (meta, bytes) ->
            if (cameraWebSocketClient.isConnected()) {
                val sent = cameraWebSocketClient.sendBinaryFrame(meta, bytes)
                if (!sent && meta.message_type == "CAMERA_PREVIEW_FRAME") {
                    cameraAcquisition.incrementDroppedFrames()
                }
            }
        }.launchIn(viewModelScope)
    }"""
new_setup = """    private fun setupCameraFrameForwarding() {
        cameraFrameForwardJob = cameraAcquisition.frameFlow.onEach { (meta, bytes) ->
            if (cameraWebSocketClient.isConnected()) {
                val sent = cameraWebSocketClient.sendBinaryFrame(meta, bytes)
                if (!sent && meta.message_type == "CAMERA_PREVIEW_FRAME") {
                    cameraAcquisition.incrementDroppedFrames()
                }
            }
        }.launchIn(viewModelScope)
        
        viewModelScope.launch {
            cameraAcquisition.scientificFrameChannel.collect { (meta, bytes) ->
                if (cameraWebSocketClient.isConnected()) {
                    val sent = cameraWebSocketClient.sendBinaryFrame(meta, bytes)
                    if (!sent) {
                        Log.e(TAG, "Failed to send scientific frame over WebSocket!")
                    }
                }
            }
        }
    }"""
if old_setup in content:
    content = content.replace(old_setup, new_setup)
else:
    print("Could not find setupCameraFrameForwarding")

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
    f.write(content)
