import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'sendResult(json.encodeToString(CameraControlResult(requestId = requestId, status = "FAILED", errorReason = "RECONFIGURATION_FAILED")))',
    'sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(request_id = requestId, device_id = activeDeviceId, overall_status = "FAILED", error_message = "RECONFIGURATION_FAILED")))'
)

# And remove `private val json = kotlinx.serialization.json.Json { encodeDefaults = true }` if present
content = re.sub(r'private val json = kotlinx\.serialization\.json\.Json.*?\}', '', content)

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
