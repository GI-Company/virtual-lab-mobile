import re

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace('cameraAcquisition.captureScientificFrame("MANUAL-" + java.util.UUID.randomUUID().toString().take(6)) {}', 'cameraAcquisition.captureScientificFrame("MANUAL-" + java.util.UUID.randomUUID().toString().take(6), null) {}')

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
    f.write(content)
