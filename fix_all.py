import re

with open('app/src/main/java/com/example/acquisition/SensorAcquisitionManager.kt', 'r') as f:
    content = f.read()
content = re.sub(r'import com\.example\.session\..*?\n', '', content)
with open('app/src/main/java/com/example/acquisition/SensorAcquisitionManager.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Fix receiveAsFlow import
if "import kotlinx.coroutines.flow.receiveAsFlow" not in content:
    content = content.replace("import kotlinx.coroutines.flow.asSharedFlow", "import kotlinx.coroutines.flow.asSharedFlow\nimport kotlinx.coroutines.flow.receiveAsFlow")

# Fix onPreviewImageAvailable timestamp
content = content.replace("device_timestamp_ns = timestamp,", "device_timestamp_ns = timestampNs,")
content = content.replace("device_timestamp_ns = timestamp\n", "device_timestamp_ns = timestampNs\n")

# Fix frame_frame_sequence
content = content.replace("frame_frame_sequence", "frame_sequence")

# Fix private fun onStillImageAvailable
content = content.replace("private fun onStillImageAvailable(reader: ImageReader)", "private fun onStillImageAvailable(reader: ImageReader)")
content = re.sub(r'private fun onStillImageAvailablevate fun onStillImageAvailable', 'private fun onStillImageAvailable', content)
content = re.sub(r'private\s+fun private fun onStillImageAvailable', 'private fun onStillImageAvailable', content)

# I saw: "552:13 Function 'private' without a body must be abstract."
# "552:24 Syntax error: Expecting '('"
# Let's print out lines around 550 to see what happened.
