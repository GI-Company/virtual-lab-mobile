import re
import os

files = [
    'app/src/main/java/com/example/camera/CameraWebSocketClient.kt',
    'app/src/main/java/com/example/camera/ControlWebSocketClient.kt',
    'app/src/main/java/com/example/transport/WebSocketClient.kt'
]

for file in files:
    with open(file, 'r') as f:
        content = f.read()

    # Add a connection ID check
    old_listener = """webSocket = client.newWebSocket(request, object : WebSocketListener() {"""
    new_listener = """
        val currentSocketId = java.util.UUID.randomUUID().toString()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket != this@ControlWebSocketClient.webSocket) return
"""
    if "ControlWebSocketClient" in content:
        content = content.replace("override fun onOpen(webSocket: WebSocket, response: Response) {", "override fun onOpen(webSocket: WebSocket, response: Response) {\n                if (webSocket != this@ControlWebSocketClient.webSocket) return")
        content = content.replace("override fun onMessage(webSocket: WebSocket, text: String) {", "override fun onMessage(webSocket: WebSocket, text: String) {\n                if (webSocket != this@ControlWebSocketClient.webSocket) return")
        content = content.replace("override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {", "override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {\n                if (webSocket != this@ControlWebSocketClient.webSocket) return")
        content = content.replace("override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {", "override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {\n                if (webSocket != this@ControlWebSocketClient.webSocket) return")
        content = content.replace("override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {", "override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {\n                if (webSocket != this@ControlWebSocketClient.webSocket) return")

    if "CameraWebSocketClient" in content:
        content = content.replace("override fun onOpen(webSocket: WebSocket, response: Response) {", "override fun onOpen(webSocket: WebSocket, response: Response) {\n                if (webSocket != this@CameraWebSocketClient.webSocket) return")
        content = content.replace("override fun onMessage(webSocket: WebSocket, text: String) {", "override fun onMessage(webSocket: WebSocket, text: String) {\n                if (webSocket != this@CameraWebSocketClient.webSocket) return")
        content = content.replace("override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {", "override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {\n                if (webSocket != this@CameraWebSocketClient.webSocket) return")
        content = content.replace("override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {", "override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {\n                if (webSocket != this@CameraWebSocketClient.webSocket) return")
        content = content.replace("override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {", "override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {\n                if (webSocket != this@CameraWebSocketClient.webSocket) return")
        content = content.replace("override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {", "override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {\n                if (webSocket != this@CameraWebSocketClient.webSocket) return")

    if "WebSocketClient" in content and "CameraWebSocketClient" not in content and "ControlWebSocketClient" not in content:
        content = content.replace("override fun onOpen(webSocket: WebSocket, response: Response) {", "override fun onOpen(webSocket: WebSocket, response: Response) {\n                if (webSocket != this@WebSocketClient.webSocket) return")
        content = content.replace("override fun onMessage(webSocket: WebSocket, text: String) {", "override fun onMessage(webSocket: WebSocket, text: String) {\n                if (webSocket != this@WebSocketClient.webSocket) return")
        content = content.replace("override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {", "override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {\n                if (webSocket != this@WebSocketClient.webSocket) return")
        content = content.replace("override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {", "override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {\n                if (webSocket != this@WebSocketClient.webSocket) return")
        content = content.replace("override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {", "override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {\n                if (webSocket != this@WebSocketClient.webSocket) return")
        
    with open(file, 'w') as f:
        f.write(content)

