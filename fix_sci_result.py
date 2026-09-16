with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

content = content.replace("ScientificCaptureResultMessage(requestId = ", "ScientificCaptureResultMessage(request_id = ")
# Also we need device_id in ScientificCaptureResultMessage
content = content.replace("ScientificCaptureResultMessage(request_id = captureId, status = \"FAILED\", error_reason = \"SESSION_NOT_ACTIVE\")", "ScientificCaptureResultMessage(request_id = captureId, device_id = activeDeviceId, status = \"FAILED\", error_reason = \"SESSION_NOT_ACTIVE\")")
content = content.replace("ScientificCaptureResultMessage(request_id = captureId, status = \"CAPTURING\")", "ScientificCaptureResultMessage(request_id = captureId, device_id = activeDeviceId, status = \"CAPTURING\")")
content = content.replace("ScientificCaptureResultMessage(request_id = captureId, status = \"FAILED\", error_reason = \"HAL_REJECTED\")", "ScientificCaptureResultMessage(request_id = captureId, device_id = activeDeviceId, status = \"FAILED\", error_reason = \"HAL_REJECTED\")")
content = content.replace("ScientificCaptureResultMessage(request_id = captureId, status = \"FAILED\", error_reason = e.message ?: \"UNKNOWN\")", "ScientificCaptureResultMessage(request_id = captureId, device_id = activeDeviceId, status = \"FAILED\", error_reason = e.message ?: \"UNKNOWN\")")

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
