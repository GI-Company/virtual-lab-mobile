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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import com.example.protocol.v1.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CameraAcquisitionManager(
    private val context: Context,
    private val permissionManager: CameraPermissionManager,
    private val thermalMonitor: DeviceThermalMonitor,
    private val cameraInventory: CameraHardwareInventory
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
    
    private val scientificCaptureResults = ConcurrentHashMap<Long, Pair<String, TotalCaptureResult>>()
    private val captureState = java.util.concurrent.ConcurrentHashMap<String, String>()
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
    private val _frameFlow = MutableSharedFlow<Pair<BaseMessage, ByteArray>>(extraBufferCapacity = 8)
    val frameFlow: kotlinx.coroutines.flow.SharedFlow<Pair<BaseMessage, ByteArray>> = _frameFlow.asSharedFlow()
    
    // The scientificFrameChannel is an un-bounded suspending queue backed by a Kotlin Channel.
    // We intentionally use capacity = 16 to buffer transient burst frames without dropping.
    // If the channel is saturated, Channel.send will suspend the calling CoroutineScope,
    // naturally applying backpressure rather than silently dropping frames like tryEmit().
    private val _scientificFrameChannel = kotlinx.coroutines.channels.Channel<Pair<com.example.protocol.v1.CameraScientificFrameMessage, ByteArray>>(capacity = 16)
    val scientificFrameChannel: kotlinx.coroutines.flow.Flow<Pair<com.example.protocol.v1.CameraScientificFrameMessage, ByteArray>> = _scientificFrameChannel.receiveAsFlow()


    // Internal metrics
    private var frameSequence = 0L
    private var currentStreamId = java.util.UUID.randomUUID().toString()
    private var totalFrames = 0L
    private var droppedFrames = 0L
    fun incrementDroppedFrames() { droppedFrames++ }
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
        currentStreamId = java.util.UUID.randomUUID().toString()
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
            
                                    val afStr = if (afModeInt == CaptureResult.CONTROL_AF_MODE_OFF) "OFF" else if (afModeInt == CaptureResult.CONTROL_AF_MODE_AUTO) "AUTO" else "UNKNOWN"
            val aeStr = if (aeModeInt == CaptureResult.CONTROL_AE_MODE_OFF) "OFF" else if (aeModeInt == CaptureResult.CONTROL_AE_MODE_ON) "ON" else "UNKNOWN"
            val awbStr = if (awbModeInt == CaptureResult.CONTROL_AWB_MODE_OFF) "OFF" else if (awbModeInt == CaptureResult.CONTROL_AWB_MODE_AUTO) "AUTO" else "UNKNOWN"

            val metadata = com.example.protocol.v1.CameraPreviewFrameMessage(
                stream_id = currentStreamId,
                frame_sequence = frameSequence++,
                device_id = activeDeviceId,
                camera_stream_key = com.example.protocol.v1.CameraStreamKey(activeCameraId, activeParentLogicalId, if (isPhysicalMember) activeCameraId else null),
                device_timestamp_ns = devTimestamp,
                device_timebase = "MONOTONIC",
                width = image.width,
                height = image.height,
                encoding = "JPEG",
                orientation = 0,
                lens_facing = activeLensFacingStr,
                focal_length_mm = focalLen,
                exposure_time_ns = expTime,
                sensor_sensitivity_iso = iso,
                focus_distance_diopters = focusDist,
                scientific_state = "MEASURED",
                acquisition_type = "CAMERA_FRAME",
                representation = "ISP_PROCESSED",
                payload_size_bytes = bytes.size

            )
            
            _frameFlow.tryEmit(Pair(metadata, bytes))

        } catch (e: Exception) {
            droppedFrames++
            Log.e(TAG, "Error handling preview image: ${e.message}", e)
        } finally {
            image.close()
        }
    }

    suspend fun captureScientificFrame(captureId: String, parameters: com.example.protocol.v1.ControlParameters?, sendResult: (String) -> Unit) {
        val session = activeCaptureSession ?: run {
            sendResult(ProtocolSerializer.serialize(ScientificCaptureResultMessage(request_id = captureId, device_id = activeDeviceId, status = "FAILED", error_message = "SESSION_NOT_ACTIVE")))
            return
        }
        val device = activeCameraDevice ?: return
        val stillReader = stillImageReader ?: return
        val handler = backgroundHandler ?: return
        
        if (captureState.putIfAbsent(captureId, "REQUESTED") != null) {
            return
        }
        
        try {
            var failedParams = mapOf<String, String>()
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
            
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                try {
                    session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            latestCaptureResult = result
                            val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                            if (timestamp != null) {
                                scientificCaptureResults[timestamp] = Pair(captureId, result)
                                sendResult(ProtocolSerializer.serialize(ScientificCaptureResultMessage(request_id = captureId, device_id = activeDeviceId, status = "COMPLETED", device_timestamp_ns = timestamp)))
                            } else {
                                sendResult(ProtocolSerializer.serialize(ScientificCaptureResultMessage(request_id = captureId, device_id = activeDeviceId, status = "FAILED", error_message = "NO_TIMESTAMP")))
                                captureState[captureId] = "FAILED"
                            }
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        
                        override fun onCaptureFailed(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            failure: CaptureFailure
                        ) {
                            sendResult(ProtocolSerializer.serialize(ScientificCaptureResultMessage(request_id = captureId, device_id = activeDeviceId, status = "FAILED", error_message = "HAL_REJECTED")))
                            captureState[captureId] = "FAILED"
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }, handler)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        } catch (e: Exception) {
            sendResult(ProtocolSerializer.serialize(ScientificCaptureResultMessage(request_id = captureId, device_id = activeDeviceId, status = "FAILED", error_message = e.message ?: "UNKNOWN")))
            captureState[captureId] = "FAILED"
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
            
            val timestamp = image.timestamp
            val matchedResult = scientificCaptureResults.remove(timestamp)
            
            if (matchedResult == null) {
                Log.e(TAG, "Uncorrelated scientific frame! Dropping. timestamp=$timestamp")
                return
            }
            val realCaptureId = matchedResult.first
            val result = matchedResult.second
            
            val expTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
            val focalLen = result.get(CaptureResult.LENS_FOCAL_LENGTH)
            val focusDist = result.get(CaptureResult.LENS_FOCUS_DISTANCE)
            val timestampNs = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: image.timestamp
            
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = md.digest(bytes)
            val sha256Hex = hashBytes.joinToString("") { "%02x".format(it) }

            val metadata = com.example.protocol.v1.CameraScientificFrameMessage(
                stream_id = currentStreamId,
                frame_sequence = frameSequence++,
                request_id = realCaptureId,
                device_id = activeDeviceId,
                camera_stream_key = com.example.protocol.v1.CameraStreamKey(activeCameraId, activeParentLogicalId, if (isPhysicalMember) activeCameraId else null),
                device_timestamp_ns = timestampNs,
                device_timebase = "MONOTONIC",
                width = image.width,
                height = image.height,
                encoding = "JPEG",
                orientation = 0,
                lens_facing = activeLensFacingStr,
                focal_length_mm = focalLen,
                exposure_time_ns = expTime,
                sensor_sensitivity_iso = iso,
                focus_distance_diopters = focusDist,
                scientific_state = "MEASURED",
                acquisition_type = "CAMERA_FRAME",
                representation = "ISP_PROCESSED",
                payload_size_bytes = bytes.size,
                payload_sha256 = sha256Hex
            )
            
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                try {
                    _scientificFrameChannel.send(Pair(metadata, bytes))
                    Log.i(TAG, "Scientific frame queued: $realCaptureId")
                } catch (e: Exception) {
                    captureState[realCaptureId] = "FAILED"
                    Log.e(TAG, "Failed to send scientific frame into channel", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling still image: ${e.message}", e)
        } finally {
            image.close()
        }
    }
    
    fun markScientificFrameSent(captureId: String) {
        captureState[captureId] = "AWAITING_ACK"
    }

    fun markScientificFrameFailed(captureId: String, reason: String) {
        captureState[captureId] = "FAILED"
        Log.e(TAG, "Scientific frame transport failed: $captureId ($reason)")
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


    private fun <T> setKey(builder: CaptureRequest.Builder, key: CaptureRequest.Key<T>, value: T): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && isPhysicalMember && activeParentLogicalId != null) {
            return try {
                builder.setPhysicalCameraKey(key, value, activeCameraId)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Unsupported physical key: ${key.name}")
                false
            }
        } else {
            builder.set(key, value)
            return true
        }
    }
    
        private fun applyControlParametersToBuilder(builder: CaptureRequest.Builder, requestedParams: com.example.protocol.v1.ControlParameters? = null): Map<String, String> {
        val params = requestedParams ?: currentControlParams
        val failures = mutableMapOf<String, String>()
        
        fun track(paramName: String, success: Boolean) {
            if (!success) failures[paramName] = "REJECTED"
        }

        // Base auto mode if no manual controls are specified
        setKey(builder, CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        
        var effectiveAfMode = params.af_mode
        if (params.focus_distance_diopters != null) {
             effectiveAfMode = "OFF"
        }

        var effectiveAeMode = params.ae_mode
        if (params.exposure_time_ns != null || params.iso != null) {
             effectiveAeMode = "OFF"
        }

        // Focus
        when (effectiveAfMode) {
            "OFF", "MANUAL" -> {
                track("af_mode", setKey(builder, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF))
                params.focus_distance_diopters?.let {
                    track("focus_distance_diopters", setKey(builder, CaptureRequest.LENS_FOCUS_DISTANCE, it))
                }
            }
            "AUTO" -> track("af_mode", setKey(builder, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO))
            "MACRO" -> track("af_mode", setKey(builder, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_MACRO))
            "CONTINUOUS_VIDEO" -> track("af_mode", setKey(builder, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO))
            "CONTINUOUS_PICTURE" -> track("af_mode", setKey(builder, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE))
            "EDOF" -> track("af_mode", setKey(builder, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_EDOF))
            else -> track("af_mode", setKey(builder, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE))
        }
        
        // Exposure
        when (effectiveAeMode) {
            "OFF", "MANUAL" -> {
                track("ae_mode", setKey(builder, CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF))
                params.exposure_time_ns?.let {
                    track("exposure_time_ns", setKey(builder, CaptureRequest.SENSOR_EXPOSURE_TIME, it))
                }
                params.iso?.let {
                    track("iso", setKey(builder, CaptureRequest.SENSOR_SENSITIVITY, it))
                }
                params.frame_duration_ns?.let {
                    track("frame_duration_ns", setKey(builder, CaptureRequest.SENSOR_FRAME_DURATION, it))
                }
            }
            "ON" -> track("ae_mode", setKey(builder, CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON))
            "ON_AUTO_FLASH" -> track("ae_mode", setKey(builder, CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH))
            "ON_ALWAYS_FLASH" -> track("ae_mode", setKey(builder, CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH))
            "ON_AUTO_FLASH_REDEYE" -> track("ae_mode", setKey(builder, CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE))
            else -> track("ae_mode", setKey(builder, CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON))
        }
        
        params.ae_compensation?.let {
            track("ae_compensation", setKey(builder, CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, it))
        }
        
        // AWB
        when (params.awb_mode) {
            "OFF" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF))
            "AUTO" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO))
            "INCANDESCENT" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT))
            "FLUORESCENT" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT))
            "WARM_FLUORESCENT" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT))
            "DAYLIGHT" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT))
            "CLOUDY_DAYLIGHT" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT))
            "TWILIGHT" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_TWILIGHT))
            "SHADE" -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_SHADE))
            else -> track("awb_mode", setKey(builder, CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO))
        }
        
        params.awb_lock?.let {
            track("awb_lock", setKey(builder, CaptureRequest.CONTROL_AWB_LOCK, it))
        }
        
        // FPS Range
        params.fps_range?.let { fpsStr ->
            try {
                val parts = fpsStr.split("-")
                if (parts.size == 2) {
                    val lower = parts[0].toInt()
                    val upper = parts[1].toInt()
                    track("fps_range", setKey(builder, CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(lower, upper)))
                }
            } catch (_: Exception) {}
        }
        
        // Zoom
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            params.zoom_ratio?.let {
                track("zoom_ratio", setKey(builder, CaptureRequest.CONTROL_ZOOM_RATIO, it))
            }
        }
        
        // Stabilization
        when (params.optical_stabilization) {
            "ON" -> track("optical_stabilization", setKey(builder, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON))
            "OFF" -> track("optical_stabilization", setKey(builder, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF))
        }
        
        when (params.video_stabilization) {
            "ON" -> track("video_stabilization", setKey(builder, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON))
            "OFF" -> track("video_stabilization", setKey(builder, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF))
        }
        
        // Torch
        when (params.torch) {
            "ON", "TORCH" -> track("torch", setKey(builder, CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH))
            "OFF" -> track("torch", setKey(builder, CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF))
        }
        
        return failures
    }

    private suspend fun updateRepeatingRequest(
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
                                    
                                    val parameterResults = mutableMapOf<String, com.example.protocol.v1.ParameterResult>()
                                    
                                    // Iterate over all fields in requested
                                    fun addResult(name: String, reqVal: Any?, appliedVal: Any?) {
                                        if (reqVal != null) {
                                            val buildFail = builderFailures[name]
                                            val reqJson = when (reqVal) {
                                                is Number -> kotlinx.serialization.json.JsonPrimitive(reqVal)
                                                is Boolean -> kotlinx.serialization.json.JsonPrimitive(reqVal)
                                                is String -> kotlinx.serialization.json.JsonPrimitive(reqVal)
                                                else -> kotlinx.serialization.json.JsonPrimitive(reqVal.toString())
                                            }
                                            val appJson = if (appliedVal == null) null else when (appliedVal) {
                                                is Number -> kotlinx.serialization.json.JsonPrimitive(appliedVal)
                                                is Boolean -> kotlinx.serialization.json.JsonPrimitive(appliedVal)
                                                is String -> kotlinx.serialization.json.JsonPrimitive(appliedVal)
                                                else -> kotlinx.serialization.json.JsonPrimitive(appliedVal.toString())
                                            }
                                            
                                            var paramStatus = if (buildFail != null) buildFail else "APPLIED"
                                            if (paramStatus == "APPLIED") {
                                                if (appJson == null) {
                                                    paramStatus = "UNCONFIRMED"
                                                } else if (reqJson != appJson) {
                                                    paramStatus = "PARTIALLY_APPLIED"
                                                }
                                            }
                                            
                                            parameterResults[name] = com.example.protocol.v1.ParameterResult(reqJson, appJson, paramStatus)
                                    }
                                    }
                                    
                                    val appliedAfMode = result.get(CaptureResult.CONTROL_AF_MODE)?.let { mode ->
                                        when(mode) {
                                            CaptureResult.CONTROL_AF_MODE_OFF -> "OFF"
                                            CaptureResult.CONTROL_AF_MODE_AUTO -> "AUTO"
                                            CaptureResult.CONTROL_AF_MODE_MACRO -> "MACRO"
                                            CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "CONTINUOUS_VIDEO"
                                            CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "CONTINUOUS_PICTURE"
                                            else -> "UNKNOWN"
                                        }
                                    } ?: "UNKNOWN"
                                    
                                    addResult("af_mode", requested.af_mode, appliedAfMode)
                                    addResult("focus_distance_diopters", requested.focus_distance_diopters, result.get(CaptureResult.LENS_FOCUS_DISTANCE))
                                    
                                    val appliedAeMode = result.get(CaptureResult.CONTROL_AE_MODE)?.let { mode ->
                                        when(mode) {
                                            CaptureResult.CONTROL_AE_MODE_OFF -> "OFF"
                                            CaptureResult.CONTROL_AE_MODE_ON -> "ON"
                                            else -> "UNKNOWN"
                                        }
                                    } ?: "UNKNOWN"
                                    
                                    addResult("ae_mode", requested.ae_mode, appliedAeMode)
                                    addResult("exposure_time_ns", requested.exposure_time_ns, result.get(CaptureResult.SENSOR_EXPOSURE_TIME))
                                    addResult("iso", requested.iso, result.get(CaptureResult.SENSOR_SENSITIVITY))
                                    addResult("frame_duration_ns", requested.frame_duration_ns, result.get(CaptureResult.SENSOR_FRAME_DURATION))
                                    addResult("ae_compensation", requested.ae_compensation, result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION))
                                    
                                    val appliedAwbMode = result.get(CaptureResult.CONTROL_AWB_MODE)?.let { mode ->
                                        when(mode) {
                                            CaptureResult.CONTROL_AWB_MODE_OFF -> "OFF"
                                            CaptureResult.CONTROL_AWB_MODE_AUTO -> "AUTO"
                                            else -> "UNKNOWN"
                                        }
                                    } ?: "UNKNOWN"
                                    
                                    addResult("awb_mode", requested.awb_mode, appliedAwbMode)
                                    addResult("awb_lock", requested.awb_lock, result.get(CaptureResult.CONTROL_AWB_LOCK))
                                    
                                    val appliedFpsRange = result.get(CaptureResult.CONTROL_AE_TARGET_FPS_RANGE)?.let { "${it.lower}-${it.upper}" } ?: "UNKNOWN"
                                    addResult("fps_range", requested.fps_range, appliedFpsRange)
                                    
                                    addResult("resolution", requested.resolution, currentControlParams.resolution)
                                    addResult("zoom_ratio", requested.zoom_ratio, result.get(CaptureResult.CONTROL_ZOOM_RATIO))
                                    addResult("crop_region", requested.crop_region, result.get(CaptureResult.SCALER_CROP_REGION)?.let { "${it.left},${it.top},${it.right},${it.bottom}" })
                                    
                                    val appliedOptStab = result.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE)?.let { mode ->
                                        when(mode) {
                                            CaptureResult.LENS_OPTICAL_STABILIZATION_MODE_OFF -> "OFF"
                                            CaptureResult.LENS_OPTICAL_STABILIZATION_MODE_ON -> "ON"
                                            else -> "UNKNOWN"
                                        }
                                    } ?: "UNKNOWN"
                                    addResult("optical_stabilization", requested.optical_stabilization, appliedOptStab)
                                    
                                    val appliedVidStab = result.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE)?.let { mode ->
                                        when(mode) {
                                            CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_OFF -> "OFF"
                                            CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE_ON -> "ON"
                                            else -> "UNKNOWN"
                                        }
                                    } ?: "UNKNOWN"
                                    addResult("video_stabilization", requested.video_stabilization, appliedVidStab)
                                    
                                    val appliedTorch = result.get(CaptureResult.FLASH_MODE)?.let { mode ->
                                        when(mode) {
                                            CaptureResult.FLASH_MODE_OFF -> "OFF"
                                            CaptureResult.FLASH_MODE_TORCH -> "TORCH"
                                            else -> "UNKNOWN"
                                        }
                                    } ?: "UNKNOWN"
                                    addResult("torch", requested.torch, appliedTorch)
                                    
                                    
                                    var overallStatus = "APPLIED"
                                    if (parameterResults.isNotEmpty()) {
                                        val allApplied = parameterResults.values.all { it.status == "APPLIED" }
                                        val allUnconfirmed = parameterResults.values.all { it.status == "UNCONFIRMED" }
                                        val allRejected = parameterResults.values.all { it.status == "REJECTED" }
                                        
                                        if (allApplied) {
                                            overallStatus = "APPLIED"
                                        } else if (allUnconfirmed) {
                                            overallStatus = "UNCONFIRMED"
                                        } else if (allRejected) {
                                            overallStatus = "REJECTED"
                                        } else {
                                            overallStatus = "PARTIALLY_APPLIED"
                                        }
                                    }
                                    
                                    val safeParams = com.example.protocol.v1.ControlParameters(
                                        af_mode = if (parameterResults["af_mode"]?.status == "APPLIED") requested.af_mode else currentControlParams.af_mode,
                                        focus_distance_diopters = if (parameterResults["focus_distance_diopters"]?.status == "APPLIED") requested.focus_distance_diopters else currentControlParams.focus_distance_diopters,
                                        ae_mode = if (parameterResults["ae_mode"]?.status == "APPLIED") requested.ae_mode else currentControlParams.ae_mode,
                                        exposure_time_ns = if (parameterResults["exposure_time_ns"]?.status == "APPLIED") requested.exposure_time_ns else currentControlParams.exposure_time_ns,
                                        iso = if (parameterResults["iso"]?.status == "APPLIED") requested.iso else currentControlParams.iso,
                                        frame_duration_ns = if (parameterResults["frame_duration_ns"]?.status == "APPLIED") requested.frame_duration_ns else currentControlParams.frame_duration_ns,
                                        ae_compensation = if (parameterResults["ae_compensation"]?.status == "APPLIED") requested.ae_compensation else currentControlParams.ae_compensation,
                                        awb_mode = if (parameterResults["awb_mode"]?.status == "APPLIED") requested.awb_mode else currentControlParams.awb_mode,
                                        awb_lock = if (parameterResults["awb_lock"]?.status == "APPLIED") requested.awb_lock else currentControlParams.awb_lock,
                                        fps_range = if (parameterResults["fps_range"]?.status == "APPLIED") requested.fps_range else currentControlParams.fps_range,
                                        resolution = requested.resolution,
                                        zoom_ratio = if (parameterResults["zoom_ratio"]?.status == "APPLIED") requested.zoom_ratio else currentControlParams.zoom_ratio,
                                        crop_region = if (parameterResults["crop_region"]?.status == "APPLIED") requested.crop_region else currentControlParams.crop_region,
                                        optical_stabilization = if (parameterResults["optical_stabilization"]?.status == "APPLIED") requested.optical_stabilization else currentControlParams.optical_stabilization,
                                        video_stabilization = if (parameterResults["video_stabilization"]?.status == "APPLIED") requested.video_stabilization else currentControlParams.video_stabilization,
                                        torch = if (parameterResults["torch"]?.status == "APPLIED") requested.torch else currentControlParams.torch,
                                        stream_state = currentControlParams.stream_state
                                    )
                                    currentControlParams = safeParams
                                    
                                    sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                                        request_id = requestId,
                                        device_id = activeDeviceId,
                                        overall_status = overallStatus,
                                        parameter_results = parameterResults
                                    )))
                                    
                                    if (continuation.isActive) continuation.resume(Unit)
                                }
                            }
                            
                            override fun onCaptureFailed(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                failure: CaptureFailure
                            ) {
                                droppedFrames++
                                if (!appliedSent) {
                                    appliedSent = true
                                    sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                                        request_id = requestId,
                                        device_id = activeDeviceId,
                                        overall_status = "FAILED",
                                        error_message = "HAL_REJECTED"
                                    )))
                                    if (continuation.isActive) continuation.resume(Unit)
                                }
                            }
                        },
                        handler
                    )
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update repeating request", e)
            sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                request_id = requestId,
                device_id = activeDeviceId,
                overall_status = "FAILED",
                error_message = e.message ?: "UNKNOWN"
            )))
        }
    }
    
    private suspend fun reconfigureSession(requestId: String, sendResult: (String) -> Unit, requested: ControlParameters) {
        val device = activeCameraDevice ?: return
        val chars = activeCharacteristics ?: return
        val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return
        
        val jpegSizes = streamMap.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
        
        val reqRes = requested.resolution ?: currentControlParams.resolution
        val parts = reqRes?.split("x")
        val targetW = parts?.getOrNull(0)?.toIntOrNull() ?: 1280
        val targetH = parts?.getOrNull(1)?.toIntOrNull() ?: 720
        
        // Find matching size or closest
        val matchSize = jpegSizes.find { it.width == targetW && it.height == targetH } ?: selectPreviewSize(jpegSizes)
        
        // Stop repeating request
        activeCaptureSession?.stopRepeating()
        activeCaptureSession?.close()
        activeCaptureSession = null
        
        previewImageReader?.close()
        val handler = backgroundHandler ?: return
        
        previewImageReader = ImageReader.newInstance(
            matchSize.width,
            matchSize.height,
            ImageFormat.JPEG,
            3
        ).apply {
            setOnImageAvailableListener({ reader ->
                onPreviewImageAvailable(reader, activeDeviceId, matchSize)
            }, handler)
        }
        
        // Setup session
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            try {
                device.createCaptureSession(
                    listOf(previewImageReader!!.surface, stillImageReader!!.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            activeCaptureSession = session
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                                updateRepeatingRequest(requestId, sendResult, requested)
                            }
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(request_id = requestId, device_id = activeDeviceId, overall_status = "FAILED", error_message = "RECONFIGURATION_FAILED")))
                            if (continuation.isActive) continuation.resumeWithException(Exception("Reconfig failed"))
                        }
                    },
                    handler
                )
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    }

    
    suspend fun processControlCommand(command: BaseMessage, deviceId: String, sendResult: (String) -> Unit) {
        val reqDeviceId = when (command) {
            is CameraControlRequestMessage -> command.device_id
            is ScientificCaptureRequestMessage -> command.device_id
            else -> null
        }
        
        if (reqDeviceId != null && reqDeviceId != deviceId) {
            val reqId = when (command) {
                is CameraControlRequestMessage -> command.request_id
                is ScientificCaptureRequestMessage -> command.request_id
                else -> ""
            }
            if (command is CameraControlRequestMessage) {
                sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                    request_id = reqId,
                    device_id = deviceId,
                    overall_status = "REJECTED",
                    error_message = "INVALID_DEVICE_ID"
                )))
            } else if (command is ScientificCaptureRequestMessage) {
                sendResult(ProtocolSerializer.serialize(ScientificCaptureResultMessage(
                    request_id = reqId,
                    device_id = deviceId,
                    status = "FAILED",
                    error_message = "INVALID_DEVICE_ID"
                )))
            }
            return
        }

        
        val requestId = when (command) {
            is CameraControlRequestMessage -> command.request_id
            is ScientificCaptureRequestMessage -> command.request_id
            else -> ""
        }

        if (command is CameraControlRequestMessage) {
            when (command.control_type) {
                "GET_CAMERA_INVENTORY" -> {
                    val cameras = cameraInventory.discoverCameras().map { c ->
                        com.example.protocol.v1.DiscoveredCameraModel(
                            id = c.id,
                            is_logical = c.isLogical,
                            physical_camera_ids = c.physicalCameraIds,
                            parent_logical_camera_id = c.parentLogicalCameraId,
                            lens_facing = c.lensFacing.name,
                            focal_lengths = c.focalLengths,
                            sensor_physical_size_mm = c.sensorPhysicalSizeMm?.let { listOf(it.first, it.second) },
                            pixel_array_size = c.pixelArraySize?.let { listOf(it.first, it.second) },
                            active_array_size = c.activeArraySize,
                            timestamp_source = c.timestampSource,
                            hardware_level = c.hardwareLevel,
                            capabilities = c.capabilities,
                            has_raw = c.hasRaw,
                            has_logical_multi = c.hasLogicalMulti,
                            supported_resolutions = c.supportedResolutions.map { "${it.first}x${it.second}" },
                            supported_fps_ranges = c.supportedFpsRanges.map { "${it.first}-${it.second}" },
                            friendly_name = c.friendlyName,
                            mp_class = c.mpClass,
                            max_stream_resolution = c.maxStreamResolution,
                            status = c.status,
                            concurrency_status = c.concurrencyStatus,
                            independently_openable = c.independentlyOpenable
                        )
                    }
                    sendResult(ProtocolSerializer.serialize(CameraInventoryMessage(
                        request_id = requestId,
                        device_id = deviceId,
                        cameras = cameras
                    )))
                    return
                }
                "GET_CAPABILITIES" -> {
                    val targetCameraId = command.camera_stream_key?.camera_id ?: activeCameraId
                    if (targetCameraId.isEmpty()) {
                        sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                            request_id = requestId,
                            device_id = deviceId,
                            overall_status = "FAILED",
                            error_message = "NO_CAMERA_TARGET"
                        )))
                        return
                    }
                    try {
                        val chars = cameraManager.getCameraCharacteristics(targetCameraId)
                        val caps = chars.getCameraCapabilitiesPayload()
                        sendResult(ProtocolSerializer.serialize(CameraCapabilitiesMessage(
                            device_id = deviceId,
                            camera_stream_key = com.example.protocol.v1.CameraStreamKey(targetCameraId),
                            manual_sensor_supported = caps.manualSensorSupported,
                            manual_focus_supported = caps.manualFocusSupported,
                            exposure_time_range_ns = caps.exposureTimeRange,
                            iso_range = caps.isoRange,
                            minimum_focus_distance_diopters = caps.minFocusDistance,
                            af_modes = caps.availableAfModes,
                            ae_modes = caps.availableAeModes,
                            ae_compensation_range = caps.aeCompensationRange,
                            ae_compensation_step = caps.aeCompensationStep,
                            awb_modes = caps.availableAwbModes,
                            awb_lock_supported = caps.awbLockSupported,
                            fps_ranges = caps.availableFpsRanges,
                            resolutions = caps.supportedResolutions,
                            zoom_ratio_range = caps.zoomRatioRange,
                            stabilization_modes = caps.opticalStabilizationModes + caps.videoStabilizationModes, // simplified
                            torch_supported = caps.flashSupported,
                            raw_capability = caps.rawSensorSupported
                        )))
                    } catch (e: Exception) {
                        sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                            request_id = requestId,
                            device_id = deviceId,
                            overall_status = "FAILED",
                            error_message = "CAMERA_ACCESS_ERROR"
                        )))
                    }
                    return
                }
                "SELECT_CAMERA", "START_CAMERA_STREAM" -> {
                    val targetCameraId = command.camera_stream_key?.camera_id
                    if (targetCameraId.isNullOrEmpty()) {
                        sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                            request_id = requestId,
                            device_id = deviceId,
                            overall_status = "FAILED",
                            error_message = "NO_CAMERA_TARGET"
                        )))
                        return
                    }
                    val camera = cameraInventory.discoverCameras().find { it.id == targetCameraId }
                    if (camera == null) {
                        sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                            request_id = requestId,
                            device_id = deviceId,
                            overall_status = "FAILED",
                            error_message = "CAMERA_NOT_FOUND"
                        )))
                        return
                    }
                    startCameraStream(camera, deviceId)
                    sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                        request_id = requestId,
                        device_id = deviceId,
                        overall_status = "APPLIED"
                    )))
                    return
                }
                "STOP_CAMERA_STREAM" -> {
                    stopCameraStream()
                    sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                        request_id = requestId,
                        device_id = deviceId,
                        overall_status = "APPLIED"
                    )))
                    return
                }
                "GET_STATE" -> {
                    val result = latestCaptureResult
                    val afMode = result?.get(CaptureResult.CONTROL_AF_MODE)?.let { mode ->
                        when(mode) {
                            CaptureResult.CONTROL_AF_MODE_OFF -> "OFF"
                            CaptureResult.CONTROL_AF_MODE_AUTO -> "AUTO"
                            CaptureResult.CONTROL_AF_MODE_MACRO -> "MACRO"
                            CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "CONTINUOUS_VIDEO"
                            CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "CONTINUOUS_PICTURE"
                            else -> "UNKNOWN"
                        }
                    }
                    sendResult(ProtocolSerializer.serialize(CameraStateMessage(
                        device_id = deviceId,
                        camera_stream_key = com.example.protocol.v1.CameraStreamKey(activeCameraId, activeParentLogicalId, if (isPhysicalMember) activeCameraId else null),
                        current_exposure_time_ns = result?.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                        current_iso = result?.get(CaptureResult.SENSOR_SENSITIVITY),
                        current_focus_distance_diopters = result?.get(CaptureResult.LENS_FOCUS_DISTANCE),
                        current_af_mode = afMode,
                        thermal_status = thermalMonitor.thermalStatus.value
                    )))
                    return
                }
                "SET_PARAMETERS" -> {
                    val requested = command.requested_parameters
                    if (requested != null) {
                        val oldRes = currentControlParams.resolution
                        val newRes = requested.resolution ?: oldRes
                        val needsReconfig = newRes != oldRes
                        
                        val newParams = com.example.protocol.v1.ControlParameters(
                            af_mode = requested.af_mode ?: currentControlParams.af_mode,
                            focus_distance_diopters = requested.focus_distance_diopters ?: currentControlParams.focus_distance_diopters,
                            ae_mode = requested.ae_mode ?: currentControlParams.ae_mode,
                            exposure_time_ns = requested.exposure_time_ns ?: currentControlParams.exposure_time_ns,
                            iso = requested.iso ?: currentControlParams.iso,
                            frame_duration_ns = requested.frame_duration_ns ?: currentControlParams.frame_duration_ns,
                            ae_compensation = requested.ae_compensation ?: currentControlParams.ae_compensation,
                            awb_mode = requested.awb_mode ?: currentControlParams.awb_mode,
                            awb_lock = requested.awb_lock ?: currentControlParams.awb_lock,
                            fps_range = requested.fps_range ?: currentControlParams.fps_range,
                            resolution = newRes,
                            zoom_ratio = requested.zoom_ratio ?: currentControlParams.zoom_ratio,
                            crop_region = requested.crop_region ?: currentControlParams.crop_region,
                            optical_stabilization = requested.optical_stabilization ?: currentControlParams.optical_stabilization,
                            video_stabilization = requested.video_stabilization ?: currentControlParams.video_stabilization,
                            torch = requested.torch ?: currentControlParams.torch
                        )

                        if (needsReconfig) {
                            reconfigureSession(requestId, sendResult, newParams)
                        } else {
                            updateRepeatingRequest(requestId, sendResult, newParams)
                        }
                    } else {
                        sendResult(ProtocolSerializer.serialize(CameraControlResultMessage(
                            request_id = requestId,
                            device_id = deviceId,
                            overall_status = "FAILED",
                            error_message = "MISSING_PARAMETERS"
                        )))
                    }
                    return
                }
            }
        }
        
        if (command is ScientificCaptureRequestMessage) {
             captureScientificFrame(command.request_id, command.parameters, sendResult)
             return
        }
        
        if (command is ScientificFrameAckMessage) {
             if (command.status == "COMMITTED") {
                 captureState[command.request_id] = "COMMITTED"
             } else {
                 captureState[command.request_id] = "REJECTED"
             }
             return
        }
    }
}
