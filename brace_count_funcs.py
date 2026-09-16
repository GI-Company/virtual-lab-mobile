with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    lines = f.readlines()

count = 0
for i, line in enumerate(lines):
    for c in line:
        if c == '{':
            count += 1
        elif c == '}':
            count -= 1
    if "fun track" in line:
        print(f"Line {i+1}: track, count={count}")
    if "private suspend fun updateRepeatingRequest(" in line:
        print(f"Line {i+1}: updateRepeatingRequest, count={count}")
    if "private suspend fun reconfigureSession(" in line:
        print(f"Line {i+1}: reconfigureSession, count={count}")
    if "suspend fun processControlCommand(" in line:
        print(f"Line {i+1}: processControlCommand, count={count}")

print(f"Final count: {count}")
