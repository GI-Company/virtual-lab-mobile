import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# I will find processControlCommand
old_process = re.search(r'suspend fun processControlCommand\(.*?\{\n', content)
if old_process:
    start_idx = old_process.end()
    
    val_block = """        val reqDeviceId = when (command) {
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
            } else if (command is CameraInventoryMessage) {
                // Ignore or send empty
            }
            return
        }
        
"""
    content = content[:start_idx] + val_block + content[start_idx:]
    
    with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
        f.write(content)
else:
    print("Could not find processControlCommand")
