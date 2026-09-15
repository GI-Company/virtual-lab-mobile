with open('./app/src/test/java/com/example/SensorNodeUnitTest.kt', 'r') as f:
    lines = f.readlines()

# find the last '}'
last_idx = -1
for i in range(len(lines) - 1, -1, -1):
    if '}' in lines[i]:
        last_idx = i
        break

if last_idx != -1:
    lines[last_idx] = lines[last_idx].replace('}', '', 1)

tests = """
    @Test
    fun cameraControlMessage_serializesCorrectly() {
        val msg = com.example.camera.CameraControlMessage(
            messageType = "SET_CAMERA_CONTROLS",
            requestId = "CTL-123",
            deviceId = "ANDROID-8f27a93e",
            cameraId = "5",
            logicalCameraId = "0",
            physicalCameraId = "5",
            timestampUtc = 1234567890L,
            parameters = com.example.camera.ControlParameters(
                afMode = "MANUAL",
                focusDistanceDiopters = 3.2f,
                aeMode = "OFF",
                exposureTimeNs = 8000000L,
                iso = 100,
                awbMode = "AUTO",
                awbLock = true
            )
        )
        val jsonString = json.encodeToString(msg)
        assertTrue(jsonString.contains("\"message_type\":\"SET_CAMERA_CONTROLS\""))
        assertTrue(jsonString.contains("\"request_id\":\"CTL-123\""))
        assertTrue(jsonString.contains("\"af_mode\":\"MANUAL\""))
        assertTrue(jsonString.contains("\"focus_distance_diopters\":3.2"))
        assertTrue(jsonString.contains("\"exposure_time_ns\":8000000"))
        assertTrue(jsonString.contains("\"iso\":100"))
    }

    @Test
    fun cameraControlResult_serializesCorrectly() {
        val res = com.example.camera.CameraControlResult(
            requestId = "CTL-123",
            status = "APPLIED",
            requested = com.example.camera.ControlParameters(
                exposureTimeNs = 8000000L,
                iso = 100
            ),
            applied = com.example.camera.ControlParameters(
                exposureTimeNs = 8333333L,
                iso = 100
            )
        )
        val jsonString = json.encodeToString(res)
        assertTrue(jsonString.contains("\"message_type\":\"CAMERA_CONTROL_RESULT\""))
        assertTrue(jsonString.contains("\"request_id\":\"CTL-123\""))
        assertTrue(jsonString.contains("\"status\":\"APPLIED\""))
        assertTrue(jsonString.contains("\"requested\":{"))
        assertTrue(jsonString.contains("\"applied\":{"))
    }
}
"""

with open('./app/src/test/java/com/example/SensorNodeUnitTest.kt', 'w') as f:
    f.writelines(lines)
    f.write(tests)
