import re

with open('app/src/main/java/com/example/acquisition/SensorAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Fix the duplicate return statement
old_return = re.search(r'return MeasurementPacketMessage.*?units = unitsMap\n        \),\n            values = valuesMap,\n            units = unitsMap,\n            accuracy = event\.accuracy\n        \)', content, re.DOTALL)
new_return = """return MeasurementPacketMessage(
            stream_id = streamId,
            sequence = sequence,
            device_id = deviceId,
            device_timestamp_ns = deviceTimestampNs,
            type = sensorClass.measurementType,
            values = valuesMap,
            units = unitsMap
        )"""
if old_return:
    content = content[:old_return.start()] + new_return + content[old_return.end():]

# Fix line 223: Unresolved reference 'streamId'.
content = content.replace("this.streamId = UUID.randomUUID().toString()", "this.streamId = java.util.UUID.randomUUID().toString()")
# Wait, line 223 unresolved reference might be `streamId = state.streamId` ?
# Ah! I changed `SensorStreamState` to have `streamId`, did it take?
# Let's see: `sessionId = state.sessionId` was changed to `streamId = state.streamId` but it was called `streamId`. Let's just blindly fix it.

with open('app/src/main/java/com/example/acquisition/SensorAcquisitionManager.kt', 'w') as f:
    f.write(content)
