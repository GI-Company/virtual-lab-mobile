import re

with open('app/src/main/java/com/example/protocol/v1/ProtocolSerializer.kt', 'r') as f:
    content = f.read()

# ProtocolSerializer currently sets classDiscriminator = "message_type"
# We need to remove this and explicitly specify how to serialize. Or better, just remove "classDiscriminator = message_type" and rely on the property in the BaseMessage subclass for JSON serialization, and parse via JSON tree for deserialization.

new_serializer = """package com.example.protocol.v1

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

object ProtocolSerializer {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun serialize(message: BaseMessage): String {
        return when (message) {
            is ChannelHelloMessage -> json.encodeToString(message)
            is InstrumentDescriptorMessage -> json.encodeToString(message)
            is MeasurementPacketMessage -> json.encodeToString(message)
            is ErrorEnvelopeMessage -> json.encodeToString(message)
            is CameraPreviewFrameMessage -> json.encodeToString(message)
            is CameraScientificFrameMessage -> json.encodeToString(message)
            is CameraControlRequestMessage -> json.encodeToString(message)
            is CameraControlResultMessage -> json.encodeToString(message)
            is CameraCapabilitiesMessage -> json.encodeToString(message)
            is CameraStateMessage -> json.encodeToString(message)
            is ScientificCaptureRequestMessage -> json.encodeToString(message)
            is ScientificCaptureResultMessage -> json.encodeToString(message)
            is ScientificFrameAckMessage -> json.encodeToString(message)
            is CameraInventoryMessage -> json.encodeToString(message)
            else -> throw IllegalArgumentException("Unknown message type")
        }
    }

    inline fun <reified T : BaseMessage> deserialize(jsonString: String): T {
        val jsonElement = json.parseToJsonElement(jsonString)
        val messageType = jsonElement.jsonObject["message_type"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing message_type")

        val message = when (messageType) {
            ProtocolConstants.TYPE_CHANNEL_HELLO -> json.decodeFromString<ChannelHelloMessage>(jsonString)
            ProtocolConstants.TYPE_INSTRUMENT_DESCRIPTOR -> json.decodeFromString<InstrumentDescriptorMessage>(jsonString)
            ProtocolConstants.TYPE_MEASUREMENT_PACKET -> json.decodeFromString<MeasurementPacketMessage>(jsonString)
            ProtocolConstants.TYPE_ERROR_ENVELOPE -> json.decodeFromString<ErrorEnvelopeMessage>(jsonString)
            ProtocolConstants.TYPE_CAMERA_PREVIEW_FRAME -> json.decodeFromString<CameraPreviewFrameMessage>(jsonString)
            ProtocolConstants.TYPE_CAMERA_SCIENTIFIC_FRAME -> json.decodeFromString<CameraScientificFrameMessage>(jsonString)
            ProtocolConstants.TYPE_CAMERA_CONTROL_REQUEST -> json.decodeFromString<CameraControlRequestMessage>(jsonString)
            ProtocolConstants.TYPE_CAMERA_CONTROL_RESULT -> json.decodeFromString<CameraControlResultMessage>(jsonString)
            ProtocolConstants.TYPE_CAMERA_CAPABILITIES -> json.decodeFromString<CameraCapabilitiesMessage>(jsonString)
            ProtocolConstants.TYPE_CAMERA_STATE -> json.decodeFromString<CameraStateMessage>(jsonString)
            ProtocolConstants.TYPE_SCIENTIFIC_CAPTURE_REQUEST -> json.decodeFromString<ScientificCaptureRequestMessage>(jsonString)
            ProtocolConstants.TYPE_SCIENTIFIC_CAPTURE_RESULT -> json.decodeFromString<ScientificCaptureResultMessage>(jsonString)
            ProtocolConstants.TYPE_SCIENTIFIC_FRAME_ACK -> json.decodeFromString<ScientificFrameAckMessage>(jsonString)
            ProtocolConstants.TYPE_CAMERA_INVENTORY -> json.decodeFromString<CameraInventoryMessage>(jsonString)
            else -> throw IllegalArgumentException("Unknown message_type: $messageType")
        }
        return message as T
    }
}
"""

with open('app/src/main/java/com/example/protocol/v1/ProtocolSerializer.kt', 'w') as f:
    f.write(new_serializer)
