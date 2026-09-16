import re

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Add documentation for scientific channel saturation behavior
doc_string = """
    // The scientificFrameChannel is an un-bounded suspending queue backed by a Kotlin Channel.
    // We intentionally use capacity = 16 to buffer transient burst frames without dropping.
    // If the channel is saturated, Channel.send will suspend the calling CoroutineScope,
    // naturally applying backpressure rather than silently dropping frames like tryEmit().
    private val _scientificFrameChannel = kotlinx.coroutines.channels.Channel<Pair<com.example.protocol.v1.CameraScientificFrameMessage, ByteArray>>(capacity = 16)
"""
content = re.sub(r'private val _scientificFrameChannel = kotlinx\.coroutines\.channels\.Channel.*?\n', doc_string.lstrip(), content)

with open('app/src/main/java/com/example/camera/CameraAcquisitionManager.kt', 'w') as f:
    f.write(content)
