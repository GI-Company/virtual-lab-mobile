import re

def add_imports(filepath):
    with open(filepath, 'r') as f:
        content = f.read()
    
    if "import com.example.protocol.v1.BaseMessage" not in content:
        content = content.replace("import android.", "import com.example.protocol.v1.BaseMessage\nimport com.example.protocol.v1.ProtocolSerializer\nimport android.")
    if "import com.example.protocol.v1.BaseMessage" not in content:
        content = content.replace("import kotlinx.", "import com.example.protocol.v1.BaseMessage\nimport com.example.protocol.v1.ProtocolSerializer\nimport kotlinx.")
    with open(filepath, 'w') as f:
        f.write(content)

add_imports('app/src/main/java/com/example/camera/CameraWebSocketClient.kt')
add_imports('app/src/main/java/com/example/camera/ControlWebSocketClient.kt')
add_imports('app/src/main/java/com/example/ui/MainViewModel.kt')

# Fix MainViewModel deviceId -> device_id
with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    content = f.read()
content = content.replace("deviceId = deviceId, channel = \"sensors\"", "device_id = deviceId, channel = \"sensors\"")
content = content.replace("deviceId = deviceId, channel = \"control\"", "device_id = deviceId, channel = \"control\"")
content = content.replace("deviceId = deviceId, channel = \"camera\"", "device_id = deviceId, channel = \"camera\"")
content = content.replace("meta.messageType", "meta.message_type")
with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
    f.write(content)
