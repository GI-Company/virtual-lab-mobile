with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'r') as f:
    lines = f.readlines()

depth = 0
for i, line in enumerate(lines):
    for char in line:
        if char == '{':
            depth += 1
        elif char == '}':
            depth -= 1
    if i+1 in [310, 315, 320, 322, 323]:
        print(f"Line {i+1} depth: {depth}")

