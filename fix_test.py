import re

with open('app/src/test/java/com/example/protocol/v1/ProtocolV1Test.kt', 'r') as f:
    content = f.read()

new_test = """    @Test
    fun testErrorEnvelope() {
        testRoundtrip<ErrorEnvelopeMessage>("error_envelope.json")
    }
"""

if "testErrorEnvelope" not in content:
    content = content.replace("    @Test\n    fun testChannelHello() {", new_test + "\n    @Test\n    fun testChannelHello() {")
    with open('app/src/test/java/com/example/protocol/v1/ProtocolV1Test.kt', 'w') as f:
        f.write(content)

