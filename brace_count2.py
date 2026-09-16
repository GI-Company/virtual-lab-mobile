with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    lines = f.readlines()

count = 0
found_first = False
for i, line in enumerate(lines):
    for c in line:
        if c == '{':
            count += 1
            found_first = True
        elif c == '}':
            count -= 1
    if found_first and count == 0:
        print(f"Class closed at line {i+1}")
        break
