import re

with open('app/src/main/java/com/example/protocol/v1/CameraMessages.kt', 'r') as f:
    content = f.read()

# 1. Replace CameraCapabilitiesMessage
old_caps = re.search(r'@Serializable\n@SerialName\("CAMERA_CAPABILITIES"\)\ndata class CameraCapabilitiesMessage\(.*?\) : BaseMessage\(\)', content, re.DOTALL)
new_caps = """@Serializable
@SerialName("CAMERA_CAPABILITIES")
data class CameraCapabilitiesMessage(
    override val message_type: String = ProtocolConstants.TYPE_CAMERA_CAPABILITIES,
    override val schema_version: String = ProtocolConstants.SCHEMA_VERSION,
    val device_id: String,
    val camera_stream_key: CameraStreamKey,
    val manual_sensor_supported: Boolean,
    val manual_focus_supported: Boolean,
    val exposure_time_range_ns: List<Long>?,
    val iso_range: List<Int>?,
    val minimum_focus_distance_diopters: Float?,
    val af_modes: List<String>,
    val ae_modes: List<String>,
    val ae_compensation_range: List<Int>?,
    val ae_compensation_step: Float?,
    val awb_modes: List<String>,
    val awb_lock_supported: Boolean,
    val fps_ranges: List<String>,
    val resolutions: List<String>,
    val zoom_ratio_range: List<Float>?,
    val stabilization_modes: List<String>,
    val torch_supported: Boolean,
    val raw_capability: Boolean
) : BaseMessage()"""

if old_caps:
    content = content[:old_caps.start()] + new_caps + content[old_caps.end():]
else:
    print("Could not find CameraCapabilitiesMessage")

# 2. Replace CameraStateMessage
old_state = re.search(r'@Serializable\n@SerialName\("CAMERA_STATE"\)\ndata class CameraStateMessage\(.*?\) : BaseMessage\(\)', content, re.DOTALL)
new_state = """@Serializable
@SerialName("CAMERA_STATE")
data class CameraStateMessage(
    override val message_type: String = ProtocolConstants.TYPE_CAMERA_STATE,
    override val schema_version: String = ProtocolConstants.SCHEMA_VERSION,
    val device_id: String,
    val camera_stream_key: CameraStreamKey,
    val current_exposure_time_ns: Long? = null,
    val current_iso: Int? = null,
    val current_focus_distance_diopters: Float? = null,
    val current_af_mode: String? = null,
    val thermal_status: String? = null
) : BaseMessage()"""

if old_state:
    content = content[:old_state.start()] + new_state + content[old_state.end():]
else:
    print("Could not find CameraStateMessage")

# 3. ScientificCaptureRequest must use parameters instead of requested_parameters
content = content.replace("val requested_parameters: ControlParameters? = null", "val parameters: ControlParameters? = null")

# 4. ParameterResult must preserve appropriate JSON value types
old_param_result = """@Serializable
data class ParameterResult(
    val requested: String? = null,
    val applied: String? = null,
    val status: String
)"""
new_param_result = """@Serializable
data class ParameterResult(
    val requested: kotlinx.serialization.json.JsonElement? = null,
    val applied: kotlinx.serialization.json.JsonElement? = null,
    val status: String
)"""
content = content.replace(old_param_result, new_param_result)

with open('app/src/main/java/com/example/protocol/v1/CameraMessages.kt', 'w') as f:
    f.write(content)
