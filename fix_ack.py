import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Replace processedRequests set with a state map
content = content.replace("private val processedRequests = ConcurrentHashMap.newKeySet<String>()", "private val captureState = java.util.concurrent.ConcurrentHashMap<String, String>()")

# In captureScientificFrame
content = content.replace("if (!processedRequests.add(captureId)) {", "if (captureState.putIfAbsent(captureId, \"REQUESTED\") != null) {")
content = content.replace("processedRequests.remove(captureId)", "captureState[captureId] = \"FAILED\"")

# When frame is sent in onStillImageAvailable, update state to AWAITING_ACK
content = content.replace("_frameFlow.tryEmit(Pair(metadata, bytes))", "_frameFlow.tryEmit(Pair(metadata, bytes))\n            captureState[realCaptureId] = \"AWAITING_ACK\"")

# Handle SCIENTIFIC_FRAME_ACK in processControlCommand
ack_handler = """        if (command is ScientificCaptureRequestMessage) {
             captureScientificFrame(command.request_id, sendResult)
             return
        }"""
new_ack_handler = """        if (command is ScientificCaptureRequestMessage) {
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
        }"""
content = content.replace(ack_handler, new_ack_handler)

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
