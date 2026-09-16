import re
import os

for filename in ['app/src/main/java/com/example/camera/ControlWebSocketClient.kt', 'app/src/main/java/com/example/camera/CameraWebSocketClient.kt', 'app/src/main/java/com/example/transport/WebSocketClient.kt']:
    if not os.path.exists(filename):
        continue
    with open(filename, 'r') as f:
        content = f.read()

    if "currentConnectionId" not in content:
        content = content.replace("private var webSocket: WebSocket? = null", "private var webSocket: WebSocket? = null\n    private var currentConnectionId: String? = null")
    
    # inject connectionId generation
    content = content.replace("webSocket = null\n", "webSocket = null\n        val connectionId = java.util.UUID.randomUUID().toString()\n        currentConnectionId = connectionId\n")
    
    # replace listener checks
    content = content.replace("if (webSocket != this@ControlWebSocketClient.webSocket) return", "if (connectionId != currentConnectionId) return")
    content = content.replace("if (webSocket != this@CameraWebSocketClient.webSocket) return", "if (connectionId != currentConnectionId) return")
    content = content.replace("if (webSocket != this@WebSocketClient.webSocket) return", "if (connectionId != currentConnectionId) return")

    with open(filename, 'w') as f:
        f.write(content)

