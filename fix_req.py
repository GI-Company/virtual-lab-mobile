import re

with open('app/src/main/java/com/example/protocol/v1/CameraMessages.kt', 'r') as f:
    content = f.read()

old = """data class CameraControlRequestMessage(
    override val message_type: String = ProtocolConstants.TYPE_CAMERA_CONTROL_REQUEST,
    override val schema_version: String = ProtocolConstants.SCHEMA_VERSION,
    val request_id: String,
    val device_id: String,
    val control_type: String,
    val camera_stream_key: CameraStreamKey? = null,
    val parameters: ControlParameters? = null
)"""
new = """data class CameraControlRequestMessage(
    override val message_type: String = ProtocolConstants.TYPE_CAMERA_CONTROL_REQUEST,
    override val schema_version: String = ProtocolConstants.SCHEMA_VERSION,
    val request_id: String,
    val device_id: String,
    val control_type: String,
    val camera_stream_key: CameraStreamKey? = null,
    val requested_parameters: ControlParameters? = null
)"""
content = content.replace(old, new)

with open('app/src/main/java/com/example/protocol/v1/CameraMessages.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()
content = content.replace("val requested = command.parameters", "val requested = command.requested_parameters")
with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
