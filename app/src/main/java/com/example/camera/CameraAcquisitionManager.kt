package com.example.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CameraAcquisitionManager(
    private val context: Context,
    private val permissionManager: CameraPermissionManager,
    private val thermalMonitor: DeviceThermalMonitor
) {
    companion object {
        private const val TAG = "CameraAcquisitionMgr"
        private const val PREVIEW_MAX_WIDTH = 1280
        private const val PREVIEW_MAX_HEIGHT = 720
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // Active Camera Session
    private var activeCameraDevice: CameraDevice? = null
    private var activeCaptureSession: CameraCaptureSession? = null
    private var previewImageReader: ImageReader? = null
    private var stillImageReader: ImageReader? = null

    // State flows
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _liveStats = MutableStateFlow(LiveCameraStats())
    val liveStats: StateFlow<LiveCameraStats> = _liveStats.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private val _lastCapturedFrame = MutableStateFlow<ScientificCapturedFrame?>(null)
    val lastCapturedFrame: StateFlow<ScientificCapturedFrame?> = _lastCapturedFrame.asStateFlow()

    private val _cameraErrorMessage = MutableStateFlow<String?>(null)
    val cameraErrorMessage: StateFlow<String?> = _cameraErrorMessage.asStateFlow()

    // Outgoing frame flow for WebSocket streaming
    private val _frameFlow = MutableSharedFlow<Pair<CameraFrameMetadata, ByteArray>>(extraBufferCapacity = 8)
    val frameFlow: SharedFlow<Pair<CameraFrameMetadata, ByteArray>> = _frameFlow.asSharedFlow()

    // Internal metrics
    private var frameSequence = 0L
    private var totalFrames = 0L
    private var droppedFrames = 0L
    private var lastFrameTimeNs = 0L
    private var frameCountForFps = 0
    private var fpsWindowStartTimeNs = 0L
    private var bytesInWindow = 0L

    // Pending capture result metadata
    @Volatile private var latestCaptureResult: CaptureResult? = null
    private var activeCameraId: String = ""
    private var activeCameraLabel: String = ""
    private var activeLensFacingStr: String = "BACK"
    private var activeParentLogicalId: String? = null
    private var isPhysicalMember = false
    private var activeDeviceId: String = ""
    
    private var activeCharacteristics: CameraCharacteristics? = null
    private var currentControlParams = ControlParameters()

    init {
        startBackgroundThread()
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraBackground").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
        } catch (_: Exception) {}
        backgroundThread = null
        backgroundHandler = null
    }

    @SuppressLint("MissingPermission")
    fun startCameraStream(
        camera: DiscoveredCamera,
        deviceId: String
    ) {
        if (!permissionManager.isCameraPermissionGranted()) {
            _cameraErrorMessage.value = "CAMERA PERMISSION REQUIRED"
            return
        }

        stopCameraStream()
        startBackgroundThread()

        activeCameraId = camera.id
        activeCameraLabel = camera.friendlyName
        activeLensFacingStr = camera.lensFacing.name
        activeParentLogicalId = camera.parentLogicalCameraId
        isPhysicalMember = camera.parentLogicalCameraId != null
        activeDeviceId = deviceId
        _cameraErrorMessage.value = null
        currentControlParams = ControlParameters()
        frameSequence = 0L
        totalFrames = 0L
        droppedFrames = 0L
        lastFrameTimeNs = 0L
        frameCountForFps = 0
        fpsWindowStartTimeNs = System.nanoTime()
        bytesInWindow = 0L

        val handler = backgroundHandler ?: return

        try {
            val chars = cameraManager.getCameraCharacteristics(camera.id)
            activeCharacteristics = chars
            val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = streamMap?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()

            // Find best preview size <= 1280x720
            val previewSize = selectPreviewSize(jpegSizes)
            val stillSize = jpegSizes.maxByOrNull { it.width * it.height } ?: previewSize

            val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            val targetRange = fpsRanges?.firstOrNull { it.lower in 10..15 && it.upper in 15..30 }
                                ?: fpsRanges?.firstOrNull { it.upper <= 30 }
            val rangeStr = targetRange?.let { "${it.lower}-${it.upper}" } ?: "UNKNOWN"

            _liveStats.value = LiveCameraStats(
                cameraId = camera.id,
                cameraLabel = camera.friendlyName,
                logicalCameraId = camera.parentLogicalCameraId,
                physicalCameraId = if (camera.parentLogicalCameraId != null) camera.id else null,
                lensFacing = camera.lensFacing.name,
                hardwareLevel = camera.hardwareLevel,
                hasRaw = camera.hasRaw,
                configuredFpsRange = rangeStr,
                resolution = "${previewSize.width}x${previewSize.height}",
                thermalStatus = thermalMonitor.thermalStatus.value
            )

            // Setup ImageReader for preview
            previewImageReader = ImageReader.newInstance(
                previewSize.width,
                previewSize.height,
                ImageFormat.JPEG,
                3
            ).apply {
                setOnImageAvailableListener({ reader ->
                    onPreviewImageAvailable(reader, deviceId, previewSize)
                }, handler)
            }

            // Setup ImageReader for still capture
            stillImageReader = ImageReader.newInstance(
                stillSize.width,
                stillSize.height,
                ImageFormat.JPEG,
                2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    onStillImageAvailable(reader)
                }, handler)
            }

            val cameraToOpen = camera.parentLogicalCameraId ?: camera.id
            cameraManager.openCamera(cameraToOpen, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    activeCameraDevice = device
                    createCaptureSession(device, chars, previewSize)
                }

                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    activeCameraDevice = null
                    _isStreaming.value = false
                    _cameraErrorMessage.value = "Camera $activeCameraId disconnected"
                }

                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    activeCameraDevice = null
                    _isStreaming.value = false
                    val errStr = when (error) {
                        ERROR_CAMERA_IN_USE -> "Camera already in use"
                        ERROR_MAX_CAMERAS_IN_USE -> "Max cameras already in use"
                        ERROR_CAMERA_DISABLED -> "Camera disabled by device policy"
                        ERROR_CAMERA_DEVICE -> "Camera device fatal error"
                        ERROR_CAMERA_SERVICE -> "Camera service error"
                        else -> "Camera error $error"
                    }
                    _cameraErrorMessage.value = errStr
                    Log.e(TAG, "Camera open error: $errStr")
                }
            }, handler)

        } catch (e: CameraAccessException) {
            _cameraErrorMessage.value = "CameraAccessException: ${e.message}"
            Log.e(TAG, "CameraAccessException", e)
        } catch (e: Exception) {
            _cameraErrorMessage.value = "Error starting camera: ${e.message}"
            Log.e(TAG, "Error starting camera", e)
        }
    }

    private fun selectPreviewSize(sizes: Array<Size>): Size {
        // Preferred 1280x720 or 960x540 or 640x480
        val candidates = sizes.filter {
            it.width <= PREVIEW_MAX_WIDTH && it.height <= PREVIEW_MAX_HEIGHT
        }.sortedByDescending { it.width * it.height }

        // Look specifically for 1280x720
        candidates.firstOrNull { it.width == 1280 && it.height == 720 }?.let { return it }

        // Or best size below max
        return candidates.firstOrNull() ?: sizes.minByOrNull { it.width * it.height } ?: Size(640, 480)
    }

    private fun createCaptureSession(
        device: CameraDevice,
        chars: CameraCharacteristics,
        previewSize: Size
    ) {
        val handler = backgroundHandler ?: return
        val previewReader = previewImageReader ?: return
        val stillReader = stillImageReader ?: return

        try {
            val stateCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    activeCaptureSession = session
                    _isStreaming.value = true
                    _cameraErrorMessage.value = null

                    // Set repeating preview request
                    try {
                        val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(previewReader.surface)
                            applyControlParametersToBuilder(this)

                            // Set conservative FPS ~10-15 FPS
                            val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                            val targetRange = fpsRanges?.firstOrNull { it.lower in 10..15 && it.upper in 15..30 }
                                ?: fpsRanges?.firstOrNull { it.upper <= 30 }
                            if (targetRange != null) {
                                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetRange)
                            }
                        }

                        session.setRepeatingRequest(
                            requestBuilder.build(),
                            object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureCompleted(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    result: TotalCaptureResult
                                ) {
                                    latestCaptureResult = result
                                }

                                override fun onCaptureFailed(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    failure: CaptureFailure
                                ) {
                                    droppedFrames++
                                }
                            },
                            handler
                        )
                    } catch (e: Exception) {
                        _cameraErrorMessage.value = "Failed starting repeating request: ${e.message}"
                        Log.e(TAG, "Failed starting repeating request", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    _isStreaming.value = false
                    _cameraErrorMessage.value = "HAL rejected camera session configuration"
                    Log.e(TAG, "Camera session configuration failed")
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val previewOutput = android.hardware.camera2.params.OutputConfiguration(previewReader.surface)
                if (isPhysicalMember) previewOutput.setPhysicalCameraId(activeCameraId)
                
                val stillOutput = android.hardware.camera2.params.OutputConfiguration(stillReader.surface)
                if (isPhysicalMember) stillOutput.setPhysicalCameraId(activeCameraId)

                val executor = java.util.concurrent.Executor { handler.post(it) }
                val sessionConfig = android.hardware.camera2.params.SessionConfiguration(
                    android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR,
                    listOf(previewOutput, stillOutput),
                    executor,
                    stateCallback
                )
                device.createCaptureSession(sessionConfig)
            } else {
                val surfaces = listOf(previewReader.surface, stillReader.surface)
                @Suppress("DEPRECATION")
                device.createCaptureSession(surfaces, stateCallback, handler)
            }
        } catch (e: Exception) {
            _cameraErrorMessage.value = "Failed creating capture session: ${e.message}"
            Log.e(TAG, "Failed creating capture session", e)
        }
    }

    private fun onPreviewImageAvailable(
        reader: ImageReader,
        deviceId: String,
        previewSize: Size
    ) {
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            droppedFrames++
            null
        } ?: return

        try {
            val planes = image.planes
            if (planes.isEmpty()) {
                image.close()
                return
            }

            val buffer = planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            totalFrames++
            val nowNs = System.nanoTime()

            // Update FPS & Bitrate calculation
            frameCountForFps++
            bytesInWindow += bytes.size
            val elapsedNs = nowNs - fpsWindowStartTimeNs
            var observedFps = _liveStats.value.observedFps
            var bitrateKbps = _liveStats.value.bitrateKbps

            if (elapsedNs >= 1_000_000_000L) {
                observedFps = (frameCountForFps.toDouble() * 1_000_000_000L) / elapsedNs
                bitrateKbps = ((bytesInWindow * 8.0) / 1000.0) / (elapsedNs / 1_000_000_000.0)
                frameCountForFps = 0
                bytesInWindow = 0L
                fpsWindowStartTimeNs = nowNs
            }

            val result = latestCaptureResult
            val expTime = result?.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            val iso = result?.get(CaptureResult.SENSOR_SENSITIVITY)
            val focalLen = result?.get(CaptureResult.LENS_FOCAL_LENGTH)
            val focusDist = result?.get(CaptureResult.LENS_FOCUS_DISTANCE)
            val devTimestamp = result?.get(CaptureResult.SENSOR_TIMESTAMP) ?: nowNs

            _liveStats.value = _liveStats.value.copy(
                resolution = "${image.width}x${image.height}",
                observedFps = Math.round(observedFps * 10.0) / 10.0,
                totalFrames = totalFrames,
                droppedFrames = droppedFrames,
                exposureTimeNs = expTime,
                iso = iso,
                focalLengthMm = focalLen,
                focusDistance = focusDist,
                timestampNs = devTimestamp,
                bitrateKbps = Math.round(bitrateKbps * 10.0) / 10.0,
                thermalStatus = thermalMonitor.thermalStatus.value
            )

            // Decode bitmap for UI preview
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                _previewBitmap.value = bitmap
            }

            // Create metadata packet
            val afModeInt = result?.get(CaptureResult.CONTROL_AF_MODE)
            val afStateInt = result?.get(CaptureResult.CONTROL_AF_STATE)
            val aeModeInt = result?.get(CaptureResult.CONTROL_AE_MODE)
            val aeStateInt = result?.get(CaptureResult.CONTROL_AE_STATE)
            val awbModeInt = result?.get(CaptureResult.CONTROL_AWB_MODE)
            val awbStateInt = result?.get(CaptureResult.CONTROL_AWB_STATE)
            val awbLockResult = result?.get(CaptureResult.CONTROL_AWB_LOCK)
            val frameDurationNs = result?.get(CaptureResult.SENSOR_FRAME_DURATION)
            val fpsRangeResult = result?.get(CaptureResult.CONTROL_AE_TARGET_FPS_RANGE)
            val fpsRangeStr = fpsRangeResult?.let { "${it.lower}-${it.upper}" }
            val cropRegion = result?.get(CaptureResult.SCALER_CROP_REGION)
            val zoomRatio = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) result?.get(CaptureResult.CONTROL_ZOOM_RATIO) else null
            
            val stabModeInt = result?.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE)
            val vidStabModeInt = result?.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE)
            val stabStr = if (vidStabModeInt == CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_ON) "VIDEO_ON" 
                          else if (stabModeInt == CaptureResult.LENS_OPTICAL_STABILIZATION_MODE_ON) "OPTICAL_ON"
                          else "OFF"
                          
            val flashModeInt = result?.get(CaptureResult.FLASH_MODE)
            val torchStr = if (flashModeInt == CaptureResult.FLASH_MODE_TORCH) "ON" else "OFF"
            
            val metadata = CameraFrameMetadata(
                schemaVersion = "1",
                messageType = "CAMERA_PREVIEW_FRAME",
                deviceId = deviceId,
                cameraId = activeCameraId,
                logicalCameraId = activeParentLogicalId,
                physicalCameraId = if (isPhysicalMember) activeCameraId else null,
                frameSequence = frameSequence++,
                deviceTimestampNs = devTimestamp,
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
                frameDurationNs = frameDurationNs,
                afMode = afModeInt?.toString(),
                afState = afStateInt?.toString(),
                aeMode = aeModeInt?.toString(),
                aeState = aeStateInt?.toString(),
                awbMode = awbModeInt?.toString(),
                awbState = awbStateInt?.toString(),
                awbLock = awbLockResult,
                fpsRange = fpsRangeStr,
                cropRegion = cropRegion?.let { "${it.left},${it.top},${it.width()},${it.height()}" },
                zoomRatio = zoomRatio,
                stabilizationMode = stabStr,
                torchState = torchStr
            )

            // Emit frame for WebSocket streaming
            _frameFlow.tryEmit(Pair(metadata, bytes))

        } catch (e: Exception) {
            droppedFrames++
            Log.e(TAG, "Error handling preview image: ${e.message}", e)
        } finally {
            image.close()
        }
    }

    fun captureScientificFrame(captureId: String? = null) {
        val session = activeCaptureSession ?: run {
            _cameraErrorMessage.value = "Cannot capture: camera session not active"
            return
        }
        val device = activeCameraDevice ?: return
        val stillReader = stillImageReader ?: return
        val handler = backgroundHandler ?: return

        try {
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(stillReader.surface)
                applyControlParametersToBuilder(this)
                set(CaptureRequest.JPEG_QUALITY, 95.toByte())
                captureId?.let { setTag(it) } // Use tag to pass captureId if possible, or just generate internally
            }
            val finalCaptureId = captureId ?: ("CAP-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().take(6))

            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    latestCaptureResult = result
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: CaptureFailure
                ) {
                    _cameraErrorMessage.value = "Scientific still capture failed in HAL"
                }
            }, handler)
        } catch (e: Exception) {
            _cameraErrorMessage.value = "Capture error: ${e.message}"
            Log.e(TAG, "Capture error", e)
        }
    }

    private fun onStillImageAvailable(reader: ImageReader) {
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            null
        } ?: return

        try {
            val planes = image.planes
            if (planes.isEmpty()) return

            val buffer = planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val captureId = image.timestamp.toString() // fallback
            // To properly match, we'd need to extract from request tag, but we will use the same ID logic or rely on timestamp correlation. We'll just generate one if not passed.
            // Wait, we need to pass the real captureId. We can pull it from the capture queue or keep it simple.
            val result = latestCaptureResult
            val realCaptureId = result?.request?.tag as? String ?: ("CAP-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().take(6))
            val expTime = result?.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            val iso = result?.get(CaptureResult.SENSOR_SENSITIVITY)
            val focalLen = result?.get(CaptureResult.LENS_FOCAL_LENGTH)
            val focusDist = result?.get(CaptureResult.LENS_FOCUS_DISTANCE)
            val timestampNs = result?.get(CaptureResult.SENSOR_TIMESTAMP) ?: System.nanoTime()

            val scientificFrame = ScientificCapturedFrame(
                captureId = realCaptureId,
                cameraId = activeCameraId,
                cameraLabel = activeCameraLabel,
                timestampNs = timestampNs,
                width = image.width,
                height = image.height,
                exposureTimeNs = expTime,
                iso = iso,
                focalLengthMm = focalLen,
                focusDistance = focusDist,
                jpegSizeBytes = bytes.size,
                jpegBytes = bytes
            )

            _lastCapturedFrame.value = scientificFrame

            val afModeInt = result?.get(CaptureResult.CONTROL_AF_MODE)
            val afStateInt = result?.get(CaptureResult.CONTROL_AF_STATE)
            val aeModeInt = result?.get(CaptureResult.CONTROL_AE_MODE)
            val aeStateInt = result?.get(CaptureResult.CONTROL_AE_STATE)
            val awbModeInt = result?.get(CaptureResult.CONTROL_AWB_MODE)
            val awbStateInt = result?.get(CaptureResult.CONTROL_AWB_STATE)
            val awbLockResult = result?.get(CaptureResult.CONTROL_AWB_LOCK)
            val frameDurationNs = result?.get(CaptureResult.SENSOR_FRAME_DURATION)
            val fpsRangeResult = result?.get(CaptureResult.CONTROL_AE_TARGET_FPS_RANGE)
            val fpsRangeStr = fpsRangeResult?.let { "${it.lower}-${it.upper}" }
            val cropRegion = result?.get(CaptureResult.SCALER_CROP_REGION)
            val zoomRatio = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) result?.get(CaptureResult.CONTROL_ZOOM_RATIO) else null
            
            val stabModeInt = result?.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE)
            val vidStabModeInt = result?.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE)
            val stabStr = if (vidStabModeInt == CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_ON) "VIDEO_ON" 
                          else if (stabModeInt == CaptureResult.LENS_OPTICAL_STABILIZATION_MODE_ON) "OPTICAL_ON"
                          else "OFF"
                          
            val flashModeInt = result?.get(CaptureResult.FLASH_MODE)
            val torchStr = if (flashModeInt == CaptureResult.FLASH_MODE_TORCH) "ON" else "OFF"
            
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
                frameDurationNs = frameDurationNs,
                afMode = afModeInt?.toString(),
                afState = afStateInt?.toString(),
                aeMode = aeModeInt?.toString(),
                aeState = aeStateInt?.toString(),
                awbMode = awbModeInt?.toString(),
                awbState = awbStateInt?.toString(),
                awbLock = awbLockResult,
                fpsRange = fpsRangeStr,
                cropRegion = cropRegion?.let { "${it.left},${it.top},${it.width()},${it.height()}" },
                zoomRatio = zoomRatio,
                stabilizationMode = stabStr,
                torchState = torchStr,
                captureId = realCaptureId
            )
            _frameFlow.tryEmit(Pair(metadata, bytes))

            Log.i(TAG, "Scientific frame captured: $realCaptureId (${image.width}x${image.height}, ${bytes.size} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling still image: ${e.message}", e)
        } finally {
            image.close()
        }
    }

    fun stopCameraStream() {
        try {
            activeCaptureSession?.stopRepeating()
            activeCaptureSession?.close()
        } catch (_: Exception) {}
        activeCaptureSession = null

        try {
            activeCameraDevice?.close()
        } catch (_: Exception) {}
        activeCameraDevice = null

        try {
            previewImageReader?.close()
        } catch (_: Exception) {}
        previewImageReader = null

        try {
            stillImageReader?.close()
        } catch (_: Exception) {}
        stillImageReader = null

        _isStreaming.value = false
        _previewBitmap.value = null
    }

    fun dismissError() {
        _cameraErrorMessage.value = null
    }

    fun release() {
        stopCameraStream()
        stopBackgroundThread()
    }

    private fun applyControlParametersToBuilder(builder: CaptureRequest.Builder) {
        val params = currentControlParams
        
        // Base auto mode if no manual controls are specified
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)

        // Focus
        when (params.afMode) {
            "OFF", "MANUAL" -> {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                params.focusDistanceDiopters?.let {
                    builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, it)
                }
            }
            "AUTO" -> builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            "MACRO" -> builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_MACRO)
            "CONTINUOUS_VIDEO" -> builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            "CONTINUOUS_PICTURE" -> builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            "EDOF" -> builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_EDOF)
            else -> builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        // Exposure
        when (params.aeMode) {
            "OFF", "MANUAL" -> {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                params.exposureTimeNs?.let {
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, it)
                }
                params.iso?.let {
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, it)
                }
                params.frameDurationNs?.let {
                    builder.set(CaptureRequest.SENSOR_FRAME_DURATION, it)
                }
            }
            "ON" -> builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            "ON_AUTO_FLASH" -> builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            "ON_ALWAYS_FLASH" -> builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
            "ON_AUTO_FLASH_REDEYE" -> builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE)
            else -> builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }
        
        params.aeCompensation?.let {
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, it)
        }

        // AWB
        when (params.awbMode) {
            "OFF" -> builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            "AUTO" -> builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            "INCANDESCENT" -> builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT)
            "FLUORESCENT" -> builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT)
            "WARM_FLUORESCENT" -> builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT)
            "DAYLIGHT" -> builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT)
            "CLOUDY_DAYLIGHT" -> builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)
            "TWILIGHT" -> builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_TWILIGHT)
            "SHADE" -> builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_SHADE)
            else -> builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        }
        
        params.awbLock?.let {
            builder.set(CaptureRequest.CONTROL_AWB_LOCK, it)
        }
        
        // FPS Range
        params.fpsRange?.let { rangeStr ->
            val parts = rangeStr.split("-")
            if (parts.size == 2) {
                val min = parts[0].toIntOrNull()
                val max = parts[1].toIntOrNull()
                if (min != null && max != null) {
                    builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(min, max))
                }
            }
        }
        
        // Stabilization
        when (params.opticalStabilization) {
            "ON" -> builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
            "OFF" -> builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF)
        }
        
        when (params.videoStabilization) {
            "ON" -> builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
            "OFF" -> builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
        }
        
        // Torch
        when (params.torch) {
            "ON" -> builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            "OFF" -> builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }
        
        // Digital Zoom (Crop Region)
        params.zoomRatio?.let { ratio ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, ratio)
            }
        }
    }

    private fun updateRepeatingRequest() {
        val device = activeCameraDevice ?: return
        val session = activeCaptureSession ?: return
        val previewReader = previewImageReader ?: return
        val handler = backgroundHandler ?: return
        
        try {
            val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewReader.surface)
                applyControlParametersToBuilder(this)
            }
            session.setRepeatingRequest(
                requestBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        latestCaptureResult = result
                    }
                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                    ) {
                        droppedFrames++
                    }
                },
                handler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update repeating request", e)
        }
    }

    private val json = kotlinx.serialization.json.Json { encodeDefaults = true }

    fun processControlCommand(command: CameraControlMessage, sendResult: (String) -> Unit) {
        if (activeCameraDevice == null || activeCharacteristics == null) {
            sendResult(json.encodeToString(CameraControlResult(
                requestId = command.requestId,
                status = "FAILED",
                errorReason = "CAMERA_NOT_ACTIVE"
            )))
            return
        }
        
        when (command.messageType) {
            "GET_CAMERA_CAPABILITIES" -> {
                val caps = activeCharacteristics!!.getCameraCapabilitiesPayload()
                sendResult(json.encodeToString(CameraCapabilitiesResponse(
                    requestId = command.requestId,
                    deviceId = activeDeviceId,
                    cameraId = activeCameraId,
                    capabilities = caps
                )))
            }
            "GET_CAMERA_STATE" -> {
                sendResult(json.encodeToString(CameraStateResponse(
                    requestId = command.requestId,
                    deviceId = activeDeviceId,
                    cameraId = activeCameraId,
                    state = currentControlParams
                )))
            }
            "SET_CAMERA_CONTROLS" -> {
                val requested = command.parameters
                if (requested != null) {
                    val oldRes = currentControlParams.resolution
                    val newRes = requested.resolution ?: oldRes
                    
                    currentControlParams = currentControlParams.copy(
                        afMode = requested.afMode ?: currentControlParams.afMode,
                        focusDistanceDiopters = requested.focusDistanceDiopters ?: currentControlParams.focusDistanceDiopters,
                        aeMode = requested.aeMode ?: currentControlParams.aeMode,
                        exposureTimeNs = requested.exposureTimeNs ?: currentControlParams.exposureTimeNs,
                        iso = requested.iso ?: currentControlParams.iso,
                        frameDurationNs = requested.frameDurationNs ?: currentControlParams.frameDurationNs,
                        aeCompensation = requested.aeCompensation ?: currentControlParams.aeCompensation,
                        awbMode = requested.awbMode ?: currentControlParams.awbMode,
                        awbLock = requested.awbLock ?: currentControlParams.awbLock,
                        fpsRange = requested.fpsRange ?: currentControlParams.fpsRange,
                        resolution = newRes,
                        zoomRatio = requested.zoomRatio ?: currentControlParams.zoomRatio,
                        cropRegion = requested.cropRegion ?: currentControlParams.cropRegion,
                        opticalStabilization = requested.opticalStabilization ?: currentControlParams.opticalStabilization,
                        videoStabilization = requested.videoStabilization ?: currentControlParams.videoStabilization,
                        torch = requested.torch ?: currentControlParams.torch
                    )
                    
                    updateRepeatingRequest()
                    
                    sendResult(json.encodeToString(CameraControlResult(
                        requestId = command.requestId,
                        status = "APPLIED",
                        requested = requested,
                        applied = currentControlParams
                    )))
                }
            }
            "CAPTURE_SCIENTIFIC_FRAME" -> {
                captureScientificFrame(command.requestId)
            }
        }
    }

}