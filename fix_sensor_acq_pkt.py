import re

with open('app/src/main/java/com/example/acquisition/SensorAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Fix the MeasurementPacketMessage creation
old_pkt = """        return MeasurementPacketMessage(
            stream_id = streamId,
            sequence = sequence,
            device_id = deviceId,
            device_timestamp_ns = deviceTimestampNs,
            type = sensorClass.measurementType,
            values = valuesMap,
            units = unitsMap
        )"""

new_pkt = """        return MeasurementPacketMessage(
            device_id = deviceId,
            stream_id = streamId,
            source_session_id = streamId, // For compatibility or just use streamId
            measurement_type = sensorClass.measurementType,
            sensor_id = sensorClass.name,
            sequence = sequence,
            device_timestamp_ns = deviceTimestampNs,
            device_timebase = "MONOTONIC",
            device_utc_ns = null,
            values = valuesMap,
            units = unitsMap,
            accuracy = event.accuracy
        )"""
content = content.replace(old_pkt, new_pkt)

with open('app/src/main/java/com/example/acquisition/SensorAcquisitionManager.kt', 'w') as f:
    f.write(content)

