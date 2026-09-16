import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Fix onStillImageAvailable to remove AWAITING_ACK logic
old_send = """                    _scientificFrameChannel.send(Pair(metadata, bytes))
                    captureState[realCaptureId] = "AWAITING_ACK"
                    Log.i(TAG, "Scientific frame queued: $realCaptureId")
                } catch (e: Exception) {
                    captureState[realCaptureId] = "FAILED"
                    Log.e(TAG, "Failed to send scientific frame into channel", e)
                }"""
new_send = """                    _scientificFrameChannel.send(Pair(metadata, bytes))
                    Log.i(TAG, "Scientific frame queued: $realCaptureId")
                } catch (e: Exception) {
                    captureState[realCaptureId] = "FAILED"
                    Log.e(TAG, "Failed to send scientific frame into channel", e)
                }"""
content = content.replace(old_send, new_send)

# Add helper methods
helpers = """    fun markScientificFrameSent(captureId: String) {
        captureState[captureId] = "AWAITING_ACK"
    }

    fun markScientificFrameFailed(captureId: String, reason: String) {
        captureState[captureId] = "FAILED"
        Log.e(TAG, "Scientific frame transport failed: $captureId ($reason)")
    }
"""
content = content.replace("    fun stopCameraStream() {", helpers + "\n    fun stopCameraStream() {")

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    content = f.read()

old_vm = """        viewModelScope.launch {
            cameraAcquisition.scientificFrameChannel.collect { (meta, bytes) ->
                if (cameraWebSocketClient.isConnected()) {
                    val sent = cameraWebSocketClient.sendBinaryFrame(meta, bytes)
                    if (!sent) {
                        Log.e(TAG, "Failed to send scientific frame over WebSocket!")
                    }
                }
            }
        }"""
new_vm = """        viewModelScope.launch {
            cameraAcquisition.scientificFrameChannel.collect { (meta, bytes) ->
                if (cameraWebSocketClient.isConnected()) {
                    val sent = cameraWebSocketClient.sendBinaryFrame(meta, bytes)
                    if (sent) {
                        cameraAcquisition.markScientificFrameSent(meta.request_id)
                    } else {
                        cameraAcquisition.markScientificFrameFailed(meta.request_id, "TRANSPORT_QUEUE_FULL")
                    }
                } else {
                    cameraAcquisition.markScientificFrameFailed(meta.request_id, "TRANSPORT_DISCONNECTED")
                }
            }
        }"""
content = content.replace(old_vm, new_vm)

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
    f.write(content)
