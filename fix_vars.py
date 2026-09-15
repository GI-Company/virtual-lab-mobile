import re

with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'r') as f:
    content = f.read()

content = content.replace("    val connectedEndpoint by viewModel.connectedEndpoint.collectAsState()",
"""    val connectedEndpoint by viewModel.connectedEndpoint.collectAsState()
    val transportMode by viewModel.transportMode.collectAsState()
    val usbBaseUrl by viewModel.usbBaseUrl.collectAsState()""")

with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'w') as f:
    f.write(content)
