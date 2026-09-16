import re

with open('app/src/test/java/com/example/protocol/v1/ProtocolV1Test.kt', 'r') as f:
    content = f.read()

old_pkt = """        val packet = MeasurementPacketMessage(
            stream_id = "test-stream",
            sequence = 1L,
            device_id = "android-device",
            device_timestamp_ns = 123456789L,
            type = "MAGNETIC_FIELD",
            values = mapOf("bx" to 1.0, "by" to 2.0, "bz" to 3.0),
            units = mapOf("bx" to "uT", "by" to "uT", "bz" to "uT")
        )"""
new_pkt = """        val packet = MeasurementPacketMessage(
            device_id = "android-device",
            stream_id = "test-stream",
            source_session_id = "test-stream",
            measurement_type = "MAGNETIC_FIELD",
            sensor_id = "MAGNETIC_FIELD",
            sequence = 1L,
            device_timestamp_ns = 123456789L,
            device_timebase = "MONOTONIC",
            device_utc_ns = null,
            values = mapOf("bx" to 1.0, "by" to 2.0, "bz" to 3.0),
            units = mapOf("bx" to "uT", "by" to "uT", "bz" to "uT"),
            accuracy = 3
        )"""

content = content.replace(old_pkt, new_pkt)

with open('app/src/test/java/com/example/protocol/v1/ProtocolV1Test.kt', 'w') as f:
    f.write(content)
