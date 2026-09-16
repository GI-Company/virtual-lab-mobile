import re

with open('app/src/main/java/com/example/protocol/v1/ProtocolSerializer.kt', 'r') as f:
    content = f.read()

old_schema = """        val schemaVersion = jsonElement.jsonObject["schema_version"]?.jsonPrimitive?.content
        if (schemaVersion != null && schemaVersion != ProtocolConstants.SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported schema_version: $schemaVersion")
        }"""

new_schema = """        val schemaVersion = jsonElement.jsonObject["schema_version"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing schema_version")
        if (schemaVersion != ProtocolConstants.SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported schema_version: $schemaVersion")
        }"""

content = content.replace(old_schema, new_schema)

with open('app/src/main/java/com/example/protocol/v1/ProtocolSerializer.kt', 'w') as f:
    f.write(content)
