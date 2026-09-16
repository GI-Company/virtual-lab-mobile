with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

count = 0
for i, c in enumerate(content):
    if c == '{':
        count += 1
    elif c == '}':
        count -= 1
    if count == 0:
        line_num = content.count('\n', 0, i) + 1
        print(f"Class closes at line {line_num}!")
        break
