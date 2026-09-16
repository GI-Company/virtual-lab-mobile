import re

with open('app/src/main/java/com/example/protocol/v1/CoreMessages.kt', 'r') as f:
    content = f.read()

# Fix MeasurementPacketMessage semantics
# source_session_id is nullable and must represent a real source/local session if one exists.
# We don't need to change the data class definition for MeasurementPacketMessage if it already has source_session_id: String?
if "val source_session_id: String?" not in content:
    content = content.replace("val source_session_id: String,", "val source_session_id: String? = null,")

with open('app/src/main/java/com/example/protocol/v1/CoreMessages.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/acquisition/SensorAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Do NOT set: source_session_id = stream_id
content = content.replace("source_session_id = currentStreamId,", "source_session_id = null,")

with open('app/src/main/java/com/example/acquisition/SensorAcquisitionManager.kt', 'w') as f:
    f.write(content)
