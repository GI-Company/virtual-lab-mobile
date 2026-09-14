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
data class MagnetometerValues(
    @SerialName("x_ut") val xUt: Float,
    @SerialName("y_ut") val yUt: Float,
    @SerialName("z_ut") val zUt: Float
)

@Serializable
data class MeasurementPacket(
    @SerialName("schema_version") val schemaVersion: String = "1.0",
    @SerialName("device_id") val deviceId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("measurement_type") val measurementType: String,
    @SerialName("timestamp_monotonic_ns") val timestampMonotonicNs: Long,
    @SerialName("timestamp_utc") val timestampUtc: String,
    val sensor: SensorMetadata,
    val values: MagnetometerValues
)
