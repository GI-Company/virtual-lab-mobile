import sys

with open('./app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

target = """            _lastCapturedFrame.value = scientificFrame
            Log.i(TAG, "Scientific frame captured: $captureId (${image.width}x${image.height}, ${bytes.size} bytes)")"""

replacement = """            _lastCapturedFrame.value = scientificFrame

            val metadata = CameraFrameMetadata(
                schemaVersion = "1",
                messageType = "CAMERA_SCIENTIFIC_FRAME",
                deviceId = activeDeviceId,
                cameraId = activeCameraId,
                logicalCameraId = activeParentLogicalId,
                physicalCameraId = if (isPhysicalMember) activeCameraId else null,
                frameSequence = frameSequence,
                deviceTimestampNs = timestampNs,
                width = image.width,
                height = image.height,
                pixelFormat = "JPEG",
                encoding = "JPEG",
                orientation = 0,
                lensFacing = activeLensFacingStr,
                focalLengthMm = focalLen,
                exposureTimeNs = expTime,
                sensorSensitivityIso = iso,
                focusDistance = focusDist,
                frameSizeBytes = bytes.size,
                scientificState = "MEASURED",
                acquisitionType = "CAMERA_FRAME",
                representation = "ISP_PROCESSED",
                captureId = captureId
            )
            _frameFlow.tryEmit(Pair(metadata, bytes))

            Log.i(TAG, "Scientific frame captured: $captureId (${image.width}x${image.height}, ${bytes.size} bytes)")"""

if target in content:
    content = content.replace(target, replacement)
    with open('./app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
        f.write(content)
    print("Success")
else:
    print("Target not found")
