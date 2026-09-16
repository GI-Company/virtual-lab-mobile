import re

with open('app/src/main/java/com/example/protocol/v1/CameraMessages.kt', 'r') as f:
    content = f.read()

content = content.replace("val fps_ranges: List<String>", "val fps_ranges: List<List<Int>>")
content = content.replace("val resolutions: List<String>", "val resolutions: List<List<Int>>")

# Should I also change ControlParameters? Let's check if the prompt asks for it. 
# Prompt: "FIX CameraCapabilities wire types. fps_ranges: List<List<Int>> resolutions: List<List<Int>>... Update: - CameraMessages.kt - CameraControlExtensions.kt - camera_capabilities.json"
# It doesn't explicitly mention ControlParameters. But if I change it, I have to change CameraAcquisitionManager as well.
# "val reqRes = requested.resolution ?: currentControlParams.resolution \n val parts = reqRes?.split('x')"
# Let's see CameraAcquisitionManager.kt.

with open('app/src/main/java/com/example/protocol/v1/CameraMessages.kt', 'w') as f:
    f.write(content)
