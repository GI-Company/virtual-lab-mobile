import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# I need to fix how ScientificCaptureRequestMessage is handled. It now uses `parameters` instead of `requested_parameters`
content = content.replace("command.requested_parameters", "command.parameters")

# Also fix SET_PARAMETERS in processControlCommand
content = content.replace("val requested = command.requested_parameters", "val requested = command.requested_parameters") # Wait, CameraControlRequestMessage uses requested_parameters, ScientificCaptureRequest uses parameters.

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
