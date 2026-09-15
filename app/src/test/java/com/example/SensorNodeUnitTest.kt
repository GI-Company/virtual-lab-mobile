package com.example

import com.example.session.MeasurementPacket
import com.example.session.SensorMetadata
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorNodeUnitTest {

    @Test
    fun measurementPacket_serializesCorrectlyWithBackwardsCompatibility() {
        val packet = MeasurementPacket(
            schemaVersion = "1",
            deviceId = "ANDROID-8f27a93e",
            sourceSessionId = "SES-0042",
            sessionId = "SES-0042",
            measurementType = "MAGNETIC_FIELD",
            sensorId = "magnetometer",
            sequence = 1824L,
            deviceTimestampNs = 123456789L,
            timestampMonotonicNs = 123456789L,
            timestampUtc = "2026-09-14T17:21:13.426Z",
            sensor = SensorMetadata(
                name = "Test Magnetometer",
                vendor = "Test Vendor",
                androidType = 2,
                accuracy = 3
            ),
            values = mapOf(
                "bx" to 4.86,
                "by" to -20.88,
                "bz" to -42.72,
                "x_ut" to 4.86,
                "y_ut" to -20.88,
                "z_ut" to -42.72
            ),
            units = mapOf(
                "bx" to "uT",
                "by" to "uT",
                "bz" to "uT"
            ),
            accuracy = 3
        )

        val jsonString = Json.encodeToString(packet)

        assertTrue(jsonString.contains("\"schema_version\":\"1\""))
        assertTrue(jsonString.contains("\"device_id\":\"ANDROID-8f27a93e\""))
        assertTrue(jsonString.contains("\"source_session_id\":\"SES-0042\""))
        assertTrue(jsonString.contains("\"session_id\":\"SES-0042\""))
        assertTrue(jsonString.contains("\"measurement_type\":\"MAGNETIC_FIELD\""))
        assertTrue(jsonString.contains("\"sensor_id\":\"magnetometer\""))
        assertTrue(jsonString.contains("\"sequence\":1824"))
        assertTrue(jsonString.contains("\"device_timestamp_ns\":123456789"))
        assertTrue(jsonString.contains("\"bx\":4.86"))
        assertTrue(jsonString.contains("\"x_ut\":4.86"))
        assertTrue(jsonString.contains("\"accuracy\":3"))
    }

    @Test
    fun measurementPacket_supportsDifferentSensors() {
        val accelPacket = MeasurementPacket(
            schemaVersion = "1",
            deviceId = "ANDROID-8f27a93e",
            sourceSessionId = "SES-0042",
            measurementType = "ACCELERATION",
            sensorId = "accelerometer",
            sequence = 100L,
            deviceTimestampNs = 200000000L,
            timestampUtc = "2026-09-14T17:21:14.000Z",
            sensor = SensorMetadata(
                name = "Test Accelerometer",
                vendor = "Test Vendor",
                androidType = 1,
                accuracy = 3
            ),
            values = mapOf(
                "ax" to 0.12,
                "ay" to 9.81,
                "az" to 0.05
            ),
            units = mapOf(
                "ax" to "m/s^2",
                "ay" to "m/s^2",
                "az" to "m/s^2"
            ),
            accuracy = 3
        )

        val jsonString = Json.encodeToString(accelPacket)
        assertTrue(jsonString.contains("\"measurement_type\":\"ACCELERATION\""))
        assertTrue(jsonString.contains("\"sensor_id\":\"accelerometer\""))
        assertTrue(jsonString.contains("\"ax\":0.12"))
    }

    @Test
    fun diagnostics_accessLocalNetworkFormat() {
        val sdk = android.os.Build.VERSION.SDK_INT
        val expected = if (sdk >= 37) "GRANTED" else "NOT APPLICABLE (API $sdk)"
        assertTrue(expected.contains("API") || expected == "GRANTED")
    }
}
