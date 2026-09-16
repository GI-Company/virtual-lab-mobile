import re

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace("import kotlinx.serialization.json.Json", "import kotlinx.serialization.json.Json\nimport com.example.protocol.v1.ProtocolSerializer")
content = re.sub(r'Json\.encodeToString\(ChannelHelloMessage\(([^)]+)\)\)', r'ProtocolSerializer.serialize(ChannelHelloMessage(\1))', content)

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
    f.write(content)
