import re

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    content = f.read()

old_forward = """                    val json = Json.encodeToString(packet)
                    val sent = webSocketClient.send(json)"""
new_forward = """                    val json = ProtocolSerializer.serialize(packet)
                    val sent = webSocketClient.send(json)"""

content = content.replace(old_forward, new_forward)

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
    f.write(content)
