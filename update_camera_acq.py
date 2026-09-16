import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Replace imports
old_imports = """import com.example.camera.CameraControlMessage
import com.example.camera.CameraControlResult
import com.example.camera.CameraCapabilitiesResponse
import com.example.camera.CameraStateResponse
import com.example.camera.CameraInventoryResponse"""
# Wait, let's just replace all com.example.camera.* old models with com.example.protocol.v1.*
content = re.sub(r'import com\.example\.camera\.CameraControlMessage', 'import com.example.protocol.v1.*', content)
content = re.sub(r'import com\.example\.camera\.CameraControlResult\n', '', content)
content = re.sub(r'import com\.example\.camera\.CameraCapabilitiesResponse\n', '', content)
content = re.sub(r'import com\.example\.camera\.CameraStateResponse\n', '', content)
content = re.sub(r'import com\.example\.camera\.CameraInventoryResponse\n', '', content)
content = re.sub(r'import com\.example\.camera\.ControlParameters\n', '', content)
content = re.sub(r'import com\.example\.camera\.DiscoveredCamera\n', '', content)

# Process Control Command signature
content = content.replace("suspend fun processControlCommand(command: CameraControlMessage, sendResult: (String) -> Unit) {", 
                          "suspend fun processControlCommand(command: BaseMessage, deviceId: String, sendResult: (String) -> Unit) {")

content = content.replace("command.messageType", "command.message_type")
content = content.replace('command.requestId', 'if (command is CameraControlRequestMessage) command.request_id else if (command is ScientificCaptureRequestMessage) command.request_id else ""')

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
