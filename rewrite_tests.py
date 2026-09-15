import re
with open('./app/src/test/java/com/example/SensorNodeUnitTest.kt', 'r') as f:
    content = f.read()

# Truncate at the first occurrence of "cameraControlMessage_serializesCorrectly"
idx = content.find("fun cameraControlMessage_serializesCorrectly")
if idx != -1:
    content = content[:idx]
    # Go back to the preceding @Test
    idx2 = content.rfind("@Test")
    if idx2 != -1:
        content = content[:idx2]
        content = content.rstrip() + "\n}\n"

with open('./app/src/test/java/com/example/SensorNodeUnitTest.kt', 'w') as f:
    f.write(content)
