import re

with open('app/src/main/java/com/example/acquisition/SensorAcquisitionManager.kt', 'r') as f:
    content = f.read()

# Replace MeasurementPacket import
content = content.replace("import com.example.session.MeasurementPacket\n", "import com.example.protocol.v1.MeasurementPacketMessage\nimport java.util.UUID\n")

# Replace packet stream type
content = content.replace("private val _packetStream = MutableSharedFlow<MeasurementPacket>(", "private val _packetStream = MutableSharedFlow<MeasurementPacketMessage>(")
content = content.replace("val packetStream: SharedFlow<MeasurementPacket> =", "val packetStream: SharedFlow<MeasurementPacketMessage> =")

# Update stream state to hold stream_id
stream_state_def = """class SensorStreamState {
    var isStreamingPackets: Boolean = false
    var deviceId: String = ""
    var sessionId: String = ""
}"""
new_stream_state_def = """class SensorStreamState {
    var isStreamingPackets: Boolean = false
    var deviceId: String = ""
    var streamId: String = ""
}"""
content = content.replace(stream_state_def, new_stream_state_def)

# Update startCapture logic
start_capture = """                streamStates.getOrPut(sensorClass) { SensorStreamState() }.apply {
                    this.isStreamingPackets = true
                    this.deviceId = deviceId
                    this.sessionId = sessionId
                }"""
new_start_capture = """                sequenceNumbers[sensorClass] = java.util.concurrent.atomic.AtomicLong(0L)
                streamStates.getOrPut(sensorClass) { SensorStreamState() }.apply {
                    this.isStreamingPackets = true
                    this.deviceId = deviceId
                    this.streamId = UUID.randomUUID().toString()
                }"""
content = content.replace(start_capture, new_start_capture)

# Update createPacket
create_packet = """    private fun createPacket(
        sensorClass: SensorTypeClass,
        event: SensorEvent,
        deviceId: String,
        sessionId: String,
        sequence: Long
    ): MeasurementPacket {"""
new_create_packet = """    private fun createPacket(
        sensorClass: SensorTypeClass,
        event: SensorEvent,
        deviceId: String,
        streamId: String,
        sequence: Long
    ): MeasurementPacketMessage {"""
content = content.replace(create_packet, new_create_packet)

# Fix packet fields
create_packet_body_match = re.search(r'val nowUtc = dateFormat\.format\(Date\(\)\).*?return MeasurementPacket\([^)]+\)', content, re.DOTALL)
if create_packet_body_match:
    old_body = create_packet_body_match.group(0)
    
    new_body = """val (valuesMap, unitsMap) = when (sensorClass) {
            SensorTypeClass.MAGNETOMETER -> {
                val bx = event.values[0].toDouble()
                val by = event.values[1].toDouble()
                val bz = event.values[2].toDouble()
                Pair(
                    mapOf("bx" to bx, "by" to by, "bz" to bz),
                    mapOf("bx" to "uT", "by" to "uT", "bz" to "uT")
                )
            }
            SensorTypeClass.ACCELEROMETER -> {
                val ax = event.values[0].toDouble()
                val ay = event.values[1].toDouble()
                val az = event.values[2].toDouble()
                Pair(
                    mapOf("ax" to ax, "ay" to ay, "az" to az),
                    mapOf("ax" to "m/s^2", "ay" to "m/s^2", "az" to "m/s^2")
                )
            }
            SensorTypeClass.GYROSCOPE -> {
                val wx = event.values[0].toDouble()
                val wy = event.values[1].toDouble()
                val wz = event.values[2].toDouble()
                Pair(
                    mapOf("wx" to wx, "wy" to wy, "wz" to wz),
                    mapOf("wx" to "rad/s", "wy" to "rad/s", "wz" to "rad/s")
                )
            }
            SensorTypeClass.AMBIENT_LIGHT -> {
                val illuminance = event.values[0].toDouble()
                Pair(
                    mapOf("illuminance" to illuminance),
                    mapOf("illuminance" to "lx")
                )
            }
            SensorTypeClass.PRESSURE -> {
                val hPa = event.values[0].toDouble()
                Pair(
                    mapOf("pressure" to hPa),
                    mapOf("pressure" to "hPa")
                )
            }
        }
        
        // Android monotonic time
        val deviceTimestampNs = event.timestamp
        
        return MeasurementPacketMessage(
            stream_id = streamId,
            sequence = sequence,
            device_id = deviceId,
            device_timestamp_ns = deviceTimestampNs,
            type = sensorClass.measurementType,
            values = valuesMap,
            units = unitsMap
        )"""
    content = content.replace(old_body, new_body)

# Update _packetStream tryEmit call
call_site = """                        val packet = createPacket(
                            sensorClass = sensorClass,
                            event = event,
                            deviceId = state.deviceId,
                            sessionId = state.sessionId,
                            sequence = seq
                        )"""
new_call_site = """                        val packet = createPacket(
                            sensorClass = sensorClass,
                            event = event,
                            deviceId = state.deviceId,
                            streamId = state.streamId,
                            sequence = seq
                        )"""
content = content.replace(call_site, new_call_site)

with open('app/src/main/java/com/example/acquisition/SensorAcquisitionManager.kt', 'w') as f:
    f.write(content)
