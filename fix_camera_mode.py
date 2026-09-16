with open('app/src/main/java/com/example/camera/CameraModels.kt', 'r') as f:
    content = f.read()

if "enum class CameraMode" not in content:
    content += """
enum class CameraMode {
    SINGLE,
    CONCURRENT,
    LOGICAL
}
"""
with open('app/src/main/java/com/example/camera/CameraModels.kt', 'w') as f:
    f.write(content)
