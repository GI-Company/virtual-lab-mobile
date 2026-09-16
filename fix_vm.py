import re

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    content = f.read()

old_inst = """        val descriptor = ProtocolSerializer.serialize(com.example.protocol.v1.InstrumentDescriptorMessage(
            device_id = deviceId,
            manufacturer = android.os.Build.MANUFACTURER,
            model = android.os.Build.MODEL,
            software_version = "1.0"
        ))"""

new_inst = """        val descriptor = ProtocolSerializer.serialize(com.example.protocol.v1.InstrumentDescriptorMessage(
            device_id = deviceId,
            manufacturer = android.os.Build.MANUFACTURER,
            model = android.os.Build.MODEL,
            software_version = "1.0",
            capabilities = listOf("CAMERA", "SENSORS")
        ))"""

content = content.replace(old_inst, new_inst)

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
    f.write(content)
