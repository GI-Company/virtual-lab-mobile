import sys

with open('./app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# 1. Fix the `""`
content = content.replace("private var currentControlParams = ControlParameters()\"\"", "private var currentControlParams = ControlParameters()")

# 2. Fix the encodeToString
# Kotlin's kotlinx.serialization.encodeToString is an inline reified extension. 
# But maybe we need to import it properly.
import_str = "import kotlinx.serialization.encodeToString"
if import_str not in content:
    content = content.replace("import kotlinx.serialization.json.Json", "import kotlinx.serialization.encodeToString\nimport kotlinx.serialization.json.Json")

with open('./app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)

with open('./app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    mv_content = f.read()

if "import kotlinx.coroutines.launch" not in mv_content:
    mv_content = mv_content.replace("import kotlinx.coroutines.flow.launchIn", "import kotlinx.coroutines.flow.launchIn\nimport kotlinx.coroutines.launch")

mv_content = mv_content.replace("cameraAcquisition.processControlCommand(msg) { responseJson ->", "cameraAcquisition.processControlCommand(msg) { responseJson: String ->")

with open('./app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
    f.write(mv_content)

print("Errors fixed")
