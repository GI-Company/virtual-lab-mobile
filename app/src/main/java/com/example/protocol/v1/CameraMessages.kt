package com.example.protocol.v1

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CameraStreamKey(
    val camera_id: String,
    val logical_camera_id: String? = null,
    val physical_camera_id: String? = null
)

@Serializable
@SerialName("CAMERA_PREVIEW_FRAME")
data class CameraPreviewFrameMessage(
    override val message_type: String = ProtocolConstants.TYPE_CAMERA_PREVIEW_FRAME,
    override val schema_version: String = ProtocolConstants.SCHEMA_VERSION,
    val device_id: String,
    val stream_id: String,
    val camera_stream_key: CameraStreamKey,
    val frame_sequence: Long,
    val device_timestamp_ns: Long,
    val device_timebase: String = "MONOTONIC",
    val device_utc_ns: Long? = null,
    val width: Int,
    val height: Int,
    val encoding: String,
    val orientation: Int,
    val lens_facing: String,
    val focal_length_mm: Float? = null,
    val exposure_time_ns: Long? = null,
    val sensor_sensitivity_iso: Int? = null,
    val focus_distance_diopters: Float? = null,
    val scientific_state: String,
    val acquisition_type: String,
    val representation: String,
    val request_id: String? = null,
    val payload_size_bytes: Int,
    val payload_sha256: String? = null
) : BaseMessage()

@Serializable
@SerialName("CAMERA_SCIENTIFIC_FRAME")
data class CameraScientificFrameMessage(
    override val message_type: String = ProtocolConstants.TYPE_CAMERA_SCIENTIFIC_FRAME,
    override val schema_version: String = ProtocolConstants.SCHEMA_VERSION,
    val device_id: String,
    val stream_id: String,
    val camera_stream_key: CameraStreamKey,
    val frame_sequence: Long,
    val device_timestamp_ns: Long,
    val device_timebase: String = "MONOTONIC",
    val device_utc_ns: Long? = null,
    val width: Int,
    val height: Int,
    val encoding: String,
    val orientation: Int,
    val lens_facing: String,
    val focal_length_mm: Float? = null,
    val exposure_time_ns: Long? = null,
    val sensor_sensitivity_iso: Int? = null,
    val focus_distance_diopters: Float? = null,
    val scientific_state: String,
    val acquisition_type: String,
    val representation: String,
    val request_id: String,
    val payload_size_bytes: Int,
    val payload_sha256: String
) : BaseMessage()

@Serializable
@SerialName("CAMERA_CONTROL_REQUEST")
data class CameraControlRequestMessage(
    override val message_type: String = ProtocolConstants.TYPE_CAMERA_CONTROL_REQUEST,
    override val schema_version: String = ProtocolConstants.SCHEMA_VERSION,
    val request_id: String,
    val device_id: String,
    val control_type: String,
    val camera_stream_key: CameraStreamKey? = null,
    val requested_parameters: ControlParameters? = null
) : BaseMessage()

@Serializable
data class ParameterResult(
    val requested: kotlinx.serialization.json.JsonElement? = null,
    val applied: kotlinx.serialization.json.JsonElement? = null,
    val status: String
)

@Serializable
@SerialName("CAMERA_CONTROL_RESULT")
data class CameraControlResultMessage(
    override val message_type: String = ProtocolConstants.TYPE_CAMERA_CONTROL_RESULT,
    override val schema_version: String = ProtocolConstants.SCHEMA_VERSION,
    val request_id: String,
    val device_id: String,
    val overall_status: String,
    val parameter_results: Map<String, ParameterResult>? = null,
    val error_message: String? = null
) : BaseMessage()

@Serializable
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
    val fps_ranges: List<List<Int>>,
    val resolutions: List<List<Int>>,
    val zoom_ratio_range: List<Float>?,
    val stabilization_modes: List<String>,
    val torch_supported: Boolean,
    val raw_capability: Boolean
) : BaseMessage()

@Serializable
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
) : BaseMessage()

@Serializable
@SerialName("SCIENTIFIC_CAPTURE_REQUEST")
data class ScientificCaptureRequestMessage(
    override val message_type: String = ProtocolConstants.TYPE_SCIENTIFIC_CAPTURE_REQUEST,
    override val schema_version: String = ProtocolConstants.SCHEMA_VERSION,
    val request_id: String,
    val device_id: String,
    val camera_stream_key: CameraStreamKey,
    val parameters: ControlParameters? = null
) : BaseMessage()

@Serializable
@SerialName("SCIENTIFIC_CAPTURE_RESULT")
data class ScientificCaptureResultMessage(
    override val message_type: String = ProtocolConstants.TYPE_SCIENTIFIC_CAPTURE_RESULT,
    override val schema_version: String = ProtocolConstants.SCHEMA_VERSION,
    val request_id: String,
    val device_id: String,
    val status: String,
    val device_timestamp_ns: Long? = null,
    val error_message: String? = null
) : BaseMessage()

@Serializable
@SerialName("SCIENTIFIC_FRAME_ACK")
data class ScientificFrameAckMessage(
    override val message_type: String = ProtocolConstants.TYPE_SCIENTIFIC_FRAME_ACK,
    override val schema_version: String = ProtocolConstants.SCHEMA_VERSION,
    val request_id: String,
    val device_id: String,
    val status: String,
    val error_message: String? = null,
    val artifact_sha256: String? = null
) : BaseMessage()

@Serializable
data class DiscoveredCameraModel(
    val id: String,
    val is_logical: Boolean,
    val physical_camera_ids: List<String>,
    val parent_logical_camera_id: String? = null,
    val lens_facing: String,
    val focal_lengths: List<Float>,
    val sensor_physical_size_mm: List<Float>? = null,
    val pixel_array_size: List<Int>? = null,
    val active_array_size: String? = null,
    val timestamp_source: String,
    val hardware_level: String,
    val capabilities: List<String>,
    val has_raw: Boolean,
    val has_logical_multi: Boolean,
    val supported_resolutions: List<String>,
    val supported_fps_ranges: List<String>,
    val friendly_name: String,
    val mp_class: String,
    val max_stream_resolution: String,
    val status: String,
    val concurrency_status: String,
    val independently_openable: Boolean = true
)

@Serializable
@SerialName("CAMERA_INVENTORY")
data class CameraInventoryMessage(
    override val message_type: String = ProtocolConstants.TYPE_CAMERA_INVENTORY,
    override val schema_version: String = ProtocolConstants.SCHEMA_VERSION,
    val request_id: String,
    val device_id: String,
    val cameras: List<DiscoveredCameraModel>
) : BaseMessage()

@Serializable
data class ControlParameters(
    val af_mode: String? = null,
    val focus_distance_diopters: Float? = null,
    val ae_mode: String? = null,
    val exposure_time_ns: Long? = null,
    val iso: Int? = null,
    val frame_duration_ns: Long? = null,
    val ae_compensation: Int? = null,
    val awb_mode: String? = null,
    val awb_lock: Boolean? = null,
    val fps_range: String? = null,
    val resolution: String? = null,
    val zoom_ratio: Float? = null,
    val crop_region: String? = null,
    val optical_stabilization: String? = null,
    val video_stabilization: String? = null,
    val torch: String? = null,
    val stream_state: String? = null
)
