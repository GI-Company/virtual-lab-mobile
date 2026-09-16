import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Replace "val params = currentControlParams" with "val params = requestedParams ?: currentControlParams" inside applyControlParametersToBuilder

old = "private fun applyControlParametersToBuilder(builder: CaptureRequest.Builder, requestedParams: com.example.protocol.v1.ControlParameters? = null): Map<String, String> {\n        val params = currentControlParams"
new = "private fun applyControlParametersToBuilder(builder: CaptureRequest.Builder, requestedParams: com.example.protocol.v1.ControlParameters? = null): Map<String, String> {\n        val params = requestedParams ?: currentControlParams"

if old in content:
    content = content.replace(old, new)
    with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
        f.write(content)
    print("Fixed applyControlParametersToBuilder")
else:
    print("Could not find applyControlParametersToBuilder target")

