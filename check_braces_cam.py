with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    lines = f.readlines()

depth = 0
for i, line in enumerate(lines):
    for char in line:
        if char == '{':
            depth += 1
        elif char == '}':
            depth -= 1
            
print(f"Final depth: {depth}")
