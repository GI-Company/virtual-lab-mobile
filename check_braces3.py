with open('./app/src/main/java/com/example/ui/SensorNodeApp.kt', 'r') as f:
    lines = f.readlines()

depth = 0
for i, line in enumerate(lines):
    for char in line:
        if char == '{':
            depth += 1
        elif char == '}':
            depth -= 1
    if "@Composable" in line:
        print(f"Line {i+1} depth (before @Composable): {depth}")

