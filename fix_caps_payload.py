import re

with open('app/src/main/java/com/example/camera/CameraModels.kt', 'r') as f:
    content = f.read()

content = content.replace("val availableFpsRanges: List<String>", "val availableFpsRanges: List<List<Int>>")
content = content.replace("val supportedResolutions: List<String>", "val supportedResolutions: List<List<Int>>")

with open('app/src/main/java/com/example/camera/CameraModels.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/camera/CameraControlExtensions.kt', 'r') as f:
    content = f.read()

content = content.replace('availableFpsRanges = fpsRanges.map { "${it.lower}-${it.upper}" }', 'availableFpsRanges = fpsRanges.map { listOf(it.lower, it.upper) }')
content = content.replace('supportedResolutions = jpegSizes.map { "${it.width}x${it.height}" }', 'supportedResolutions = jpegSizes.map { listOf(it.width, it.height) }')

with open('app/src/main/java/com/example/camera/CameraControlExtensions.kt', 'w') as f:
    f.write(content)

