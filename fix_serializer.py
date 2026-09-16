import re

with open('app/src/main/java/com/example/protocol/v1/ProtocolSerializer.kt', 'r') as f:
    content = f.read()

old_des = """        val messageType = jsonElement.jsonObject["message_type"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing message_type")"""

new_des = """        val messageType = jsonElement.jsonObject["message_type"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing message_type")
            
        val schemaVersion = jsonElement.jsonObject["schema_version"]?.jsonPrimitive?.content
        if (schemaVersion != null && schemaVersion != "1" && schemaVersion != "1.0") {
            throw IllegalArgumentException("Unsupported schema_version: $schemaVersion")
        }"""

# Wait, if we only support "1" or "1.0". The user says: "Change error_envelope.json schema_version from "1.0" to "1". Add an explicit test that unsupported schema versions are rejected."
# If I just check `schemaVersion != ProtocolConstants.SCHEMA_VERSION`, wait - what if the incoming message has "1.0"?
# Let's reject if it's not "1".
new_des = """        val messageType = jsonElement.jsonObject["message_type"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing message_type")
            
        val schemaVersion = jsonElement.jsonObject["schema_version"]?.jsonPrimitive?.content
        if (schemaVersion != null && schemaVersion != ProtocolConstants.SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported schema_version: $schemaVersion")
        }"""
        
content = content.replace(old_des, new_des)

with open('app/src/main/java/com/example/protocol/v1/ProtocolSerializer.kt', 'w') as f:
    f.write(content)
