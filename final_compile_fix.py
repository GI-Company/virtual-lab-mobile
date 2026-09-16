import re

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    content = f.read()

# Fix conflicting imports by replacing all occurrences of these imports with nothing, then adding them once
content = content.replace("import com.example.protocol.v1.BaseMessage\n", "")
content = content.replace("import com.example.protocol.v1.ProtocolSerializer\n", "")
content = "import com.example.protocol.v1.BaseMessage\nimport com.example.protocol.v1.ProtocolSerializer\n" + content

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
    f.write(content)


with open('app/src/main/java/com/example/acquisition/SensorAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Check SensorStreamState
content = content.replace("var sessionId: String = \"\"", "var streamId: String = \"\"")
# Check createPacket call
content = content.replace("sessionId = state.sessionId,", "streamId = state.streamId,")

with open('app/src/main/java/com/example/acquisition/SensorAcquisitionManager.kt', 'w') as f:
    f.write(content)


with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Fix devTimestamp in onPreviewImageAvailable
content = content.replace("device_timestamp_ns = devTimestamp,", "device_timestamp_ns = timestampNs,")

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)

