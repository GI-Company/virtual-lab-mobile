package com.example.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SensorMetadata(
    val name: String,
    val vendor: String,
    @SerialName("android_type") val androidType: Int,
    val accuracy: Int
)

@Serializable
data class MeasurementPacket(
    @SerialName("schema_version") val schemaVersion: String = "1",
    @SerialName("device_id") val deviceId: String,
    @SerialName("source_session_id") val sourceSessionId: String,
    // Backwards compatibility with existing VirtualLab /sensors gateway
    @SerialName("session_id") val sessionId: String = sourceSessionId,
    @SerialName("measurement_type") val measurementType: String,
    @SerialName("sensor_id") val sensorId: String,
    val sequence: Long,
    @SerialName("device_timestamp_ns") val deviceTimestampNs: Long,
    // Backwards compatibility with existing VirtualLab parser
    @SerialName("timestamp_monotonic_ns") val timestampMonotonicNs: Long = deviceTimestampNs,
    @SerialName("timestamp_utc") val timestampUtc: String,
    val sensor: SensorMetadata,
    val values: Map<String, Double>,
    val units: Map<String, String>,
    val accuracy: Int
)
