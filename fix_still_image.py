import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# I will find private fun onStillImageAvailable and replace up to fun markScientificFrameSent
old_still = re.search(r'private fun onStillImageAvailable.*?fun markScientificFrameSent', content, re.DOTALL)
if old_still:
    new_still = """private fun onStillImageAvailable(reader: ImageReader) {
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
    
    """
    content = content[:old_still.start()] + new_still + "fun markScientificFrameSent" + content[old_still.end()-27:]
else:
    print("Could not find onStillImageAvailable")

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
