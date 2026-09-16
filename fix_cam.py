import re
with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Fix line 85 stray declaration
content = content.replace("    private var frameSequence = 0L\n        currentStreamId = java.util.UUID.randomUUID().toString()\n    private var currentStreamId", "    private var frameSequence = 0L\n    private var currentStreamId")

# Fix line 460: device_timestamp_ns = timestampNs. In onPreviewImageAvailable it should be devTimestamp or similar, because it was already extracted. Let's look at it.
content = content.replace("device_timestamp_ns = timestampNs,", "device_timestamp_ns = devTimestamp,")

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
