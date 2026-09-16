import re

with open('app/src/main/java/com/example/transport/WebSocketClient.kt', 'r') as f:
    content = f.read()

old_close = """        awaitClose {
            try {
                webSocket?.close(1000, "Connection closed by client")
            } catch (_: Exception) {}
            webSocket = null
        }"""
        
new_close = """        val capturedSocket = webSocket
        awaitClose {
            if (webSocket == capturedSocket) {
                try {
                    webSocket?.close(1000, "Connection closed by client")
                } catch (_: Exception) {}
                webSocket = null
            }
        }"""

content = content.replace(old_close, new_close)

with open('app/src/main/java/com/example/transport/WebSocketClient.kt', 'w') as f:
    f.write(content)

