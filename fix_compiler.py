import re

with open('app/src/main/java/com/example/camera/ControlWebSocketClient.kt', 'r') as f:
    content = f.read()

content = content.replace("capturedSocket", "currentConnectionId")
content = content.replace("this@ControlWebSocketClient.currentConnectionId", "currentConnectionId")
content = content.replace("if (webSocket != currentConnectionId) return", "if (connectionId != currentConnectionId) return")

with open('app/src/main/java/com/example/camera/ControlWebSocketClient.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace("captureScientificFrame(requestId, sendResult)", "captureScientificFrame(requestId, null, sendResult)")

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
    f.write(content)

