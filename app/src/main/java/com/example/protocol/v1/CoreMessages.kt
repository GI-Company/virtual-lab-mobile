package com.example.protocol.v1

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
sealed class BaseMessage {
    abstract val message_type: String
    abstract val schema_version: String
}

@Serializable
@SerialName("CHANNEL_HELLO")
data class ChannelHelloMessage(
    override val message_type: String = ProtocolConstants.TYPE_CHANNEL_HELLO,
    override val schema_version: String = ProtocolConstants.SCHEMA_VERSION,
    val device_id: String,
    val channel: String
) : BaseMessage()

@Serializable
@SerialName("INSTRUMENT_DESCRIPTOR")
data class InstrumentDescriptorMessage(
    override val message_type: String = ProtocolConstants.TYPE_INSTRUMENT_DESCRIPTOR,
    override val schema_version: String = ProtocolConstants.SCHEMA_VERSION,
    val device_id: String,
    val instrument_type: String = "SENSOR_NODE",
    val manufacturer: String,
    val model: String,
    val software_version: String,
    val protocol_profiles: List<String> = listOf("CORE_V1", "CAMERA_V1"),
    val capabilities: JsonElement? = null
) : BaseMessage()

@Serializable
@SerialName("MEASUREMENT_PACKET")
data class MeasurementPacketMessage(
    override val message_type: String = ProtocolConstants.TYPE_MEASUREMENT_PACKET,
    override val schema_version: String = ProtocolConstants.SCHEMA_VERSION,
    val device_id: String,
    val stream_id: String,
    val source_session_id: String? = null,
    val measurement_type: String,
    val sensor_id: String,
    val sequence: Long,
    val device_timestamp_ns: Long,
    val device_timebase: String = "MONOTONIC",
    val device_utc_ns: Long? = null,
    val values: Map<String, Double>,
    val units: Map<String, String>,
    val accuracy: Int
) : BaseMessage()

@Serializable
@SerialName("ERROR_ENVELOPE")
data class ErrorEnvelopeMessage(
    override val message_type: String = ProtocolConstants.TYPE_ERROR_ENVELOPE,
    override val schema_version: String = ProtocolConstants.SCHEMA_VERSION,
    val device_id: String,
    val severity: String,
    val error_code: String,
    val message: String,
    val details: Map<String, String>? = null
) : BaseMessage()
