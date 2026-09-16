import re

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    content = f.read()

# Update toggleRecording
old_toggle = """    fun toggleRecording() {
        if (_isRecording.value) {
            _isRecording.value = false
            sensorManager.stopCapture()
            sensorManager.startLivePreview(_selectedPreviewSensor.value)
        } else {
            _streamedPacketsCount.value = 0L
            _isRecording.value = true
            sensorManager.startCapture(
                selectedSensors = _selectedCaptureSensors.value,
                deviceId = deviceId,
                sessionId = _sessionId.value
            )
        }
    }"""
new_toggle = """    fun toggleRecording() {
        if (_isRecording.value) {
            _isRecording.value = false
        } else {
            _streamedPacketsCount.value = 0L
            _isRecording.value = true
        }
    }"""
content = content.replace(old_toggle, new_toggle)

# Update selectPreviewSensor
old_select = """    fun selectPreviewSensor(sensorClass: SensorTypeClass) {
        val old = _selectedPreviewSensor.value
        if (old != sensorClass) {
            if (!_isRecording.value || !_selectedCaptureSensors.value.contains(old)) {
                sensorManager.stopLivePreview(old)
            }
            _selectedPreviewSensor.value = sensorClass
            sensorManager.startLivePreview(sensorClass)
        }
    }"""
new_select = """    fun selectPreviewSensor(sensorClass: SensorTypeClass) {
        val old = _selectedPreviewSensor.value
        if (old != sensorClass) {
            if (!_selectedCaptureSensors.value.contains(old)) {
                sensorManager.stopLivePreview(old)
            }
            _selectedPreviewSensor.value = sensorClass
            sensorManager.startLivePreview(sensorClass)
        }
    }"""
content = content.replace(old_select, new_select)

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
    f.write(content)
