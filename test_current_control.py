import re
with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# I need to fix how `currentControlParams` is reassigned in SET_PARAMETERS
# because I used `newRes` and `requested.af_mode` etc. but those field names in ControlParameters are af_mode.
# Let's find "val newParams = com.example.protocol.v1.ControlParameters("
# Wait, it was "val newParams = com.example.camera.ControlParameters(" 
# We need to change that to "val newParams = com.example.protocol.v1.ControlParameters("

content = content.replace("val newParams = com.example.camera.ControlParameters(", "val newParams = com.example.protocol.v1.ControlParameters(")

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
