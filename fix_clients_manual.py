import re

for filename in ['app/src/main/java/com/example/camera/ControlWebSocketClient.kt', 'app/src/main/java/com/example/camera/CameraWebSocketClient.kt', 'app/src/main/java/com/example/transport/WebSocketClient.kt']:
    import os
    if not os.path.exists(filename): continue
    
    with open(filename, 'r') as f:
        content = f.read()
        
    # We want to replace the `capturedSocket` stuff we added earlier, back to the UUID approach.
    # Actually, I can just fix the `awaitClose` blocks.
    
    if "ControlWebSocketClient.kt" in filename:
        old_close = """        val capturedSocket = webSocket
        awaitClose {
            if (webSocket == capturedSocket) {
                try {
                    webSocket?.close(1000, "Control client closed")
                } catch (_: Exception) {}
                webSocket = null
        val connectionId = java.util.UUID.randomUUID().toString()
        currentConnectionId = connectionId
                isOpen = false
            }
        }"""
        new_close = """        awaitClose {
            if (connectionId == currentConnectionId) {
                try {
                    webSocket?.close(1000, "Control client closed")
                } catch (_: Exception) {}
                webSocket = null
                currentConnectionId = null
                isOpen = false
            }
        }"""
        content = content.replace(old_close, new_close)
        
    if "CameraWebSocketClient.kt" in filename:
        old_close = """        awaitClose {
            try {
                webSocket?.close(1000, "Camera stream closed")
            } catch (_: Exception) {}
            webSocket = null
        val connectionId = java.util.UUID.randomUUID().toString()
        currentConnectionId = connectionId
            isOpen = false
        }"""
        new_close = """        awaitClose {
            if (connectionId == currentConnectionId) {
                try {
                    webSocket?.close(1000, "Camera stream closed")
                } catch (_: Exception) {}
                webSocket = null
                currentConnectionId = null
                isOpen = false
            }
        }"""
        content = content.replace(old_close, new_close)
        
    if "WebSocketClient.kt" in filename:
        old_close = """        awaitClose {
            try {
                webSocket?.close(1000, "Connection closed by client")
            } catch (_: Exception) {}
            webSocket = null
        val connectionId = java.util.UUID.randomUUID().toString()
        currentConnectionId = connectionId
        }"""
        new_close = """        awaitClose {
            if (connectionId == currentConnectionId) {
                try {
                    webSocket?.close(1000, "Connection closed by client")
                } catch (_: Exception) {}
                webSocket = null
                currentConnectionId = null
            }
        }"""
        content = content.replace(old_close, new_close)

    with open(filename, 'w') as f:
        f.write(content)

