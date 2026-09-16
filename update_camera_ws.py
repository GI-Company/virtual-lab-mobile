import re

with open('app/src/main/java/com/example/camera/CameraWebSocketClient.kt', 'r') as f:
    content = f.read()

content = content.replace("import com.example.camera.CameraFrameMetadata\n", "import com.example.protocol.v1.BaseMessage\nimport com.example.protocol.v1.ProtocolSerializer\n")
content = content.replace("fun sendBinaryFrame(metadata: CameraFrameMetadata, jpegBytes: ByteArray): Boolean {", "fun sendBinaryFrame(metadata: BaseMessage, jpegBytes: ByteArray): Boolean {")

# Backpressure check update
old_bp = """        if (ws.queueSize() > 2_000_000 && metadata.messageType == "CAMERA_PREVIEW_FRAME") { // e.g. 2MB"""
new_bp = """        if (ws.queueSize() > 2_000_000 && metadata.message_type == "CAMERA_PREVIEW_FRAME") { // e.g. 2MB"""
content = content.replace(old_bp, new_bp)

# JSON serialization update
old_json = """            val jsonString = Json.encodeToString(metadata)"""
new_json = """            val jsonString = ProtocolSerializer.serialize(metadata)"""
content = content.replace(old_json, new_json)

with open('app/src/main/java/com/example/camera/CameraWebSocketClient.kt', 'w') as f:
    f.write(content)
