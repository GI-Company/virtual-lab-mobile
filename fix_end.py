import re
with open('./app/src/test/java/com/example/SensorNodeUnitTest.kt', 'r') as f:
    content = f.read()

# Replace all combinations of multiple closing braces at the end of the file with a single one.
content = re.sub(r'\}\s*\}\s*\}\s*$', '}\n}\n', content)

with open('./app/src/test/java/com/example/SensorNodeUnitTest.kt', 'w') as f:
    f.write(content)
