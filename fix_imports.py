import sys
import re

with open('./app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Ensure the import is there
if "import kotlinx.serialization.encodeToString" not in content:
    content = content.replace("import java.util.UUID", "import java.util.UUID\nimport kotlinx.serialization.encodeToString\nimport kotlinx.serialization.json.Json")

with open('./app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)

print("Imports fixed")
