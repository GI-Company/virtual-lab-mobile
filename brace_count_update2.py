with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    lines = f.readlines()

start_line = -1
for i, line in enumerate(lines):
    if "private suspend fun updateRepeatingRequest(" in line:
        start_line = i
        break

count = 0
for i in range(start_line, 990):
    for c in lines[i]:
        if c == '{':
            count += 1
        elif c == '}':
            count -= 1
            if count == -1:
                print(f"Extra closing brace found at line {i+1}!")
print(f"Final count for updateRepeatingRequest: {count}")
