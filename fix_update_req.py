import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# I will modify updateRepeatingRequest to add a tag to the CaptureRequest, and only check the result if the request tag matches.
# also update currentControlParams when the tag matches.

old_update = """    private suspend fun updateRepeatingRequest(
        requestId: String,
        sendResult: (String) -> Unit,
        requested: ControlParameters
    ) {
        val device = activeCameraDevice ?: return
        val session = activeCaptureSession ?: return
        val previewReader = previewImageReader ?: return
        val handler = backgroundHandler ?: return
        
        try {
            val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewReader.surface)
            }
            val builderFailures = applyControlParametersToBuilder(requestBuilder, requested)
            
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                try {
                    session.setRepeatingRequest(
                        requestBuilder.build(),
                        object : CameraCaptureSession.CaptureCallback() {
                            private var appliedSent = false
                            
                            override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: TotalCaptureResult
                            ) {
                                latestCaptureResult = result
                                if (!appliedSent) {"""

new_update = """    private suspend fun updateRepeatingRequest(
        requestId: String,
        sendResult: (String) -> Unit,
        requested: ControlParameters
    ) {
        val device = activeCameraDevice ?: return
        val session = activeCaptureSession ?: return
        val previewReader = previewImageReader ?: return
        val handler = backgroundHandler ?: return
        
        try {
            val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewReader.surface)
                setTag(requestId)
            }
            val builderFailures = applyControlParametersToBuilder(requestBuilder, requested)
            
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                try {
                    session.setRepeatingRequest(
                        requestBuilder.build(),
                        object : CameraCaptureSession.CaptureCallback() {
                            private var appliedSent = false
                            
                            override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: TotalCaptureResult
                            ) {
                                latestCaptureResult = result
                                if (!appliedSent && request.tag == requestId) {
                                    appliedSent = true
                                    currentControlParams = requested"""

content = content.replace(old_update, new_update)

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)

