import re

with open('app/src/main/java/com/example/camera/CameraWebSocketClient.kt', 'r') as f:
    content = f.read()

old_close = """        awaitClose {
            try {
                webSocket?.close(1000, "Camera client closed")
            } catch (_: Exception) {}
            webSocket = null
        val connectionId = java.util.UUID.randomUUID().toString()
        currentConnectionId = connectionId
        isOpen = false
        }"""
        
new_close = """        awaitClose {
            if (connectionId == currentConnectionId) {
                try {
                    webSocket?.close(1000, "Camera client closed")
                } catch (_: Exception) {}
                webSocket = null
                currentConnectionId = null
                isOpen = false
            }
        }"""

content = content.replace(old_close, new_close)

with open('app/src/main/java/com/example/camera/CameraWebSocketClient.kt', 'w') as f:
    f.write(content)

