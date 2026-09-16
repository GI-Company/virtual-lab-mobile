with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    lines = f.readlines()

count = 0
for i, line in enumerate(lines):
    for c in line:
        if c == '{':
            count += 1
        elif c == '}':
            count -= 1
    if count == 0 and i > 50:
        print(f"Count drops to 0 at line {i+1}")
        break
