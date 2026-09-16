import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

old_sci = """            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(stillReader.surface)
                // Need to apply control parameters manually since we rewrote applyControlParametersToBuilder
                val dummyFailures = applyControlParametersToBuilder(this, parameters ?: currentControlParams)
                set(CaptureRequest.JPEG_QUALITY, 95.toByte())
                setTag(captureId)
            }
            
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->"""

new_sci = """            var failedParams = mapOf<String, String>()
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(stillReader.surface)
                failedParams = applyControlParametersToBuilder(this, parameters ?: currentControlParams)
                set(CaptureRequest.JPEG_QUALITY, 95.toByte())
                setTag(captureId)
            }
            
            if (failedParams.isNotEmpty()) {
                sendResult(ProtocolSerializer.serialize(ScientificCaptureResultMessage(
                    request_id = captureId, 
                    device_id = activeDeviceId, 
                    status = "FAILED", 
                    error_message = "PARAMETERS_REJECTED"
                )))
                captureState[captureId] = "FAILED"
                return
            }
            
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->"""

content = content.replace(old_sci, new_sci)

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
