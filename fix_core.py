import re

with open('app/src/main/java/com/example/protocol/v1/CoreMessages.kt', 'r') as f:
    content = f.read()

content = content.replace("val capabilities: JsonElement? = null", "val capabilities: List<String>")

with open('app/src/main/java/com/example/protocol/v1/CoreMessages.kt', 'w') as f:
    f.write(content)
