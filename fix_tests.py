import re

with open('app/src/test/java/com/example/protocol/v1/ProtocolV1Test.kt', 'r') as f:
    content = f.read()

new_test = """    @Test
    fun testUnsupportedSchemaVersion() {
        val json = \"\"\"
        {
          "message_type": "CHANNEL_HELLO",
          "schema_version": "2.0",
          "device_id": "test_device"
        }
        \"\"\"
        try {
            ProtocolSerializer.deserialize<ChannelHelloMessage>(json)
            org.junit.Assert.fail("Expected exception for unsupported schema version")
        } catch (e: IllegalArgumentException) {
            org.junit.Assert.assertTrue(e.message?.contains("Unsupported schema_version") == true)
        }
    }
"""

content = content.replace("    @Test\n    fun testErrorEnvelope()", new_test + "\n    @Test\n    fun testErrorEnvelope()")

with open('app/src/test/java/com/example/protocol/v1/ProtocolV1Test.kt', 'w') as f:
    f.write(content)
