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

        _cameraErrorMessage.value = null
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
            val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = streamMap?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()

            // Find best preview size <= 1280x720
            val previewSize = selectPreviewSize(jpegSizes)
            val stillSize = jpegSizes.maxByOrNull { it.width * it.height } ?: previewSize

            _liveStats.value = LiveCameraStats(
                cameraId = camera.id,
                cameraLabel = camera.friendlyName,
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

            cameraManager.openCamera(camera.id, object : CameraDevice.StateCallback() {
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
            val surfaces = listOf(previewReader.surface, stillReader.surface)

            @Suppress("DEPRECATION")
            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    activeCaptureSession = session
                    _isStreaming.value = true
                    _cameraErrorMessage.value = null

                    // Set repeating preview request
                    try {
                        val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(previewReader.surface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

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
            }, handler)
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

            _liveStats.value = LiveCameraStats(
                cameraId = activeCameraId,
                cameraLabel = activeCameraLabel,
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
            val metadata = CameraFrameMetadata(
                schemaVersion = "1",
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
                representation = "ISP_PROCESSED"
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

    fun captureScientificFrame() {
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
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.JPEG_QUALITY, 95.toByte())
            }

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

            val captureId = "CAP-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().take(6)
            val result = latestCaptureResult
            val expTime = result?.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            val iso = result?.get(CaptureResult.SENSOR_SENSITIVITY)
            val focalLen = result?.get(CaptureResult.LENS_FOCAL_LENGTH)
            val focusDist = result?.get(CaptureResult.LENS_FOCUS_DISTANCE)
            val timestampNs = result?.get(CaptureResult.SENSOR_TIMESTAMP) ?: System.nanoTime()

            val scientificFrame = ScientificCapturedFrame(
                captureId = captureId,
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
            Log.i(TAG, "Scientific frame captured: $captureId (${image.width}x${image.height}, ${bytes.size} bytes)")
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
}
