import re
import uuid

# Fix ControlWebSocketClient
with open('app/src/main/java/com/example/camera/ControlWebSocketClient.kt', 'r') as f:
    content = f.read()

old_close = """        awaitClose {
            try {
                webSocket?.close(1000, "Control client closed")
            } catch (_: Exception) {}
            webSocket = null
        isOpen = false
        }"""
        
new_close = """        val capturedSocket = webSocket
        awaitClose {
            if (webSocket == capturedSocket) {
                try {
                    webSocket?.close(1000, "Control client closed")
                } catch (_: Exception) {}
                webSocket = null
                isOpen = false
            }
        }"""

content = content.replace(old_close, new_close)

# I can also generate a UUID token for connect
uuid_connect_old = "fun connect(url: String, helloMessage: ChannelHelloMessage): Flow<ConnectionState> = callbackFlow {"
uuid_connect_new = "fun connect(url: String, helloMessage: ChannelHelloMessage): Flow<ConnectionState> = callbackFlow {\n        val connectionToken = java.util.UUID.randomUUID().toString()"

content = content.replace(uuid_connect_old, uuid_connect_new)
content = content.replace("if (webSocket != this@ControlWebSocketClient.webSocket) return", "if (webSocket != capturedSocket) return")

# Wait, `this@ControlWebSocketClient.webSocket` is used in the listeners! Let me just use `capturedSocket` in the listeners.

# Wait, the listeners are created before `capturedSocket` is declared if I declare it after. 
# Better: assign `webSocket` to a local variable `val currentSocket = client.newWebSocket(...)` and then assign `webSocket = currentSocket`. Then `currentSocket` is in scope for the listener and `awaitClose`.

with open('app/src/main/java/com/example/camera/ControlWebSocketClient.kt', 'w') as f:
    f.write(content)

