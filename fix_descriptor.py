import re

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    content = f.read()

# Add descriptor message sending after hello
old_hello = """        val controlHello = ProtocolSerializer.serialize(ChannelHelloMessage(device_id = deviceId, channel = "control"))
        controlWsJob = controlWebSocketClient.connect(controlUrl, controlHello).onEach { state ->"""
new_hello = """        val controlHello = ProtocolSerializer.serialize(ChannelHelloMessage(device_id = deviceId, channel = "control"))
        val descriptor = ProtocolSerializer.serialize(com.example.protocol.v1.InstrumentDescriptorMessage(
            device_id = deviceId,
            manufacturer = android.os.Build.MANUFACTURER,
            model = android.os.Build.MODEL,
            software_version = "1.0"
        ))
        controlWsJob = controlWebSocketClient.connect(controlUrl, controlHello).onEach { state ->
            if (state is ConnectionState.Connected) {
                controlWebSocketClient.sendResponse(descriptor)
            }"""
if old_hello in content:
    content = content.replace(old_hello, new_hello)
else:
    print("Could not find control hello")
    
with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
    f.write(content)
