import re

with open('app/src/main/java/com/example/camera/ControlWebSocketClient.kt', 'r') as f:
    content = f.read()

# Fix imports
content = content.replace("import com.example.camera.CameraControlMessage\n", "import com.example.protocol.v1.BaseMessage\nimport com.example.protocol.v1.ProtocolSerializer\n")

# Fix _incomingMessages type
content = content.replace("MutableSharedFlow<CameraControlMessage>", "MutableSharedFlow<BaseMessage>")
content = content.replace("SharedFlow<CameraControlMessage>", "SharedFlow<BaseMessage>")

# Fix decoding
old_decode = """                try {
                    val message = json.decodeFromString<CameraControlMessage>(text)
                    _incomingMessages.tryEmit(message)
                } catch (e: Exception) {"""
new_decode = """                try {
                    val message = ProtocolSerializer.deserialize<BaseMessage>(text)
                    _incomingMessages.tryEmit(message)
                } catch (e: Exception) {"""
content = content.replace(old_decode, new_decode)

with open('app/src/main/java/com/example/camera/ControlWebSocketClient.kt', 'w') as f:
    f.write(content)
