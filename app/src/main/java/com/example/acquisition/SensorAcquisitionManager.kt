package com.example.acquisition

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.session.MeasurementPacket
import com.example.session.SensorMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class SensorTypeClass(
    val androidType: Int,
    val displayName: String,
    val defaultDelay: Int,
    val rateDescription: String,
    val measurementType: String,
    val sensorId: String
) {
    MAGNETOMETER(
        Sensor.TYPE_MAGNETIC_FIELD,
        "Magnetometer",
        SensorManager.SENSOR_DELAY_GAME, // ~50 Hz
        "~50 Hz",
        "MAGNETIC_FIELD",
        "magnetometer"
    ),
    ACCELEROMETER(
        Sensor.TYPE_ACCELEROMETER,
        "Accelerometer",
        SensorManager.SENSOR_DELAY_GAME, // ~50 Hz
        "~50 Hz",
        "ACCELERATION",
        "accelerometer"
    ),
    GYROSCOPE(
        Sensor.TYPE_GYROSCOPE,
        "Gyroscope",
        SensorManager.SENSOR_DELAY_GAME, // ~50 Hz
        "~50 Hz",
        "ANGULAR_VELOCITY",
        "gyroscope"
    ),
    AMBIENT_LIGHT(
        Sensor.TYPE_LIGHT,
        "Ambient Light",
        SensorManager.SENSOR_DELAY_NORMAL,
        "event-driven",
        "ILLUMINANCE",
        "ambient_light"
    ),
    PRESSURE(
        Sensor.TYPE_PRESSURE,
        "Pressure / Barometer",
        SensorManager.SENSOR_DELAY_NORMAL,
        "event-driven",
        "PRESSURE",
        "pressure"
    )
}

data class DiscoveredSensorMeta(
    val sensorClass: SensorTypeClass,
    val isAvailable: Boolean,
    val name: String,
    val vendor: String,
    val version: Int,
    val resolution: Float,
    val maximumRange: Float,
    val minDelay: Int,
    val power: Float,
    val androidType: Int
)

data class LiveSensorReading(
    val sensorClass: SensorTypeClass,
    val values: FloatArray,
    val timestampNs: Long,
    val accuracy: Int,
    val observedHz: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as LiveSensorReading
        return sensorClass == other.sensorClass &&
                values.contentEquals(other.values) &&
                timestampNs == other.timestampNs &&
                accuracy == other.accuracy &&
                observedHz == other.observedHz
    }

    override fun hashCode(): Int {
        var result = sensorClass.hashCode()
        result = 31 * result + values.contentHashCode()
        result = 31 * result + timestampNs.hashCode()
        result = 31 * result + accuracy
        result = 31 * result + observedHz.hashCode()
        return result
    }
}

class SensorAcquisitionManager(private val context: Context) {
    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Discover actual sensors
    val discoveredSensors: Map<SensorTypeClass, DiscoveredSensorMeta> = discoverSensors()

    // Real-time live readings for UI
    private val _liveReadings = MutableStateFlow<Map<SensorTypeClass, LiveSensorReading>>(emptyMap())
    val liveReadings: StateFlow<Map<SensorTypeClass, LiveSensorReading>> = _liveReadings.asStateFlow()

    // Real packet stream for active capture
    private val _packetStream = MutableSharedFlow<MeasurementPacket>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val packetStream: SharedFlow<MeasurementPacket> = _packetStream.asSharedFlow()

    // Per-sensor sequence number
    private val sequenceNumbers = ConcurrentHashMap<SensorTypeClass, AtomicLong>()

    // Rate calculation state: rolling timestamps per sensor
    private val rateTimestamps = ConcurrentHashMap<SensorTypeClass, LongArray>()
    private val rateIndex = ConcurrentHashMap<SensorTypeClass, Int>()

    private val activeListeners = ConcurrentHashMap<SensorTypeClass, SensorEventListener>()

    data class SensorStreamState(
        var isStreamingPackets: Boolean = false,
        var deviceId: String = "",
        var sessionId: String = ""
    )

    private val streamStates = ConcurrentHashMap<SensorTypeClass, SensorStreamState>()


    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun discoverSensors(): Map<SensorTypeClass, DiscoveredSensorMeta> {
        val result = mutableMapOf<SensorTypeClass, DiscoveredSensorMeta>()
        for (sc in SensorTypeClass.values()) {
            val sensor = sensorManager.getDefaultSensor(sc.androidType)
            if (sensor != null) {
                result[sc] = DiscoveredSensorMeta(
                    sensorClass = sc,
                    isAvailable = true,
                    name = sensor.name,
                    vendor = sensor.vendor,
                    version = sensor.version,
                    resolution = sensor.resolution,
                    maximumRange = sensor.maximumRange,
                    minDelay = sensor.minDelay,
                    power = sensor.power,
                    androidType = sensor.type
                )
            } else {
                result[sc] = DiscoveredSensorMeta(
                    sensorClass = sc,
                    isAvailable = false,
                    name = "NOT AVAILABLE",
                    vendor = "N/A",
                    version = 0,
                    resolution = 0f,
                    maximumRange = 0f,
                    minDelay = 0,
                    power = 0f,
                    androidType = sc.androidType
                )
            }
        }
        return result
    }

    /**
     * Start live preview listening for a single sensor (e.g. for the LIVE card view).
     */
    fun startLivePreview(sensorClass: SensorTypeClass) {
        if (!isSensorAvailable(sensorClass)) return
        registerListener(sensorClass)
    }

    /**
     * Stop live preview for a sensor (if not currently in active capture).
     */
    fun stopLivePreview(sensorClass: SensorTypeClass) {
        unregisterListener(sensorClass)
    }

    /**
     * Start recording capture for a set of selected sensors.
     */
    fun startCapture(
        selectedSensors: Set<SensorTypeClass>,
        deviceId: String,
        sessionId: String
    ) {
        for (sensorClass in selectedSensors) {
            if (isSensorAvailable(sensorClass)) {
                sequenceNumbers.putIfAbsent(sensorClass, AtomicLong(0L))
                streamStates.getOrPut(sensorClass) { SensorStreamState() }.apply {
                    this.isStreamingPackets = true
                    this.deviceId = deviceId
                    this.sessionId = sessionId
                }
                registerListener(sensorClass)
            }
        }
    }

    fun stopCapture(sensors: Set<SensorTypeClass>) {
        for (sensor in sensors) {
            streamStates[sensor]?.isStreamingPackets = false
        }
    }

    fun stopCapture() {
        val keys = streamStates.keys.toList()
        for (key in keys) {
            streamStates[key]?.isStreamingPackets = false
        }
    }

    fun isSensorAvailable(sensorClass: SensorTypeClass): Boolean {
        return discoveredSensors[sensorClass]?.isAvailable == true
    }

    private fun registerListener(
        sensorClass: SensorTypeClass
    ) {
        if (activeListeners.containsKey(sensorClass)) {
            // Already registered
            return
        }

        val sensor = sensorManager.getDefaultSensor(sensorClass.androidType) ?: return

        // Buffer for rate calculation (last 16 event timestamps in nanoseconds)
        val timestampsBuffer = LongArray(16) { 0L }
        rateTimestamps[sensorClass] = timestampsBuffer
        rateIndex[sensorClass] = 0

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val nowNs = event.timestamp
                val observedHz = calculateObservedRate(sensorClass, nowNs)

                // Update live state
                val currentCopy = event.values.clone()
                val reading = LiveSensorReading(
                    sensorClass = sensorClass,
                    values = currentCopy,
                    timestampNs = event.timestamp,
                    accuracy = event.accuracy,
                    observedHz = observedHz
                )

                val updatedMap = _liveReadings.value.toMutableMap()
                updatedMap[sensorClass] = reading
                _liveReadings.value = updatedMap

                // If streaming is enabled, construct and emit packet
                val state = streamStates[sensorClass]
                if (state?.isStreamingPackets == true) {
                    val seq = sequenceNumbers.getOrPut(sensorClass) { AtomicLong(0L) }.incrementAndGet()
                    val packet = createPacket(
                        sensorClass = sensorClass,
                        event = event,
                        deviceId = state.deviceId,
                        sessionId = state.sessionId,
                        sequence = seq
                    )
                    _packetStream.tryEmit(packet)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        activeListeners[sensorClass] = listener
        sensorManager.registerListener(listener, sensor, sensorClass.defaultDelay)
    }

    private fun unregisterListener(sensorClass: SensorTypeClass) {
        val listener = activeListeners.remove(sensorClass)
        if (listener != null) {
            sensorManager.unregisterListener(listener)
        }
    }

    private fun calculateObservedRate(sensorClass: SensorTypeClass, currentNs: Long): Double {
        val buffer = rateTimestamps[sensorClass] ?: return 0.0
        val idx = rateIndex[sensorClass] ?: 0

        buffer[idx % buffer.size] = currentNs
        rateIndex[sensorClass] = (idx + 1)

        // Find oldest recorded timestamp
        val count = minOf(idx + 1, buffer.size)
        if (count < 2) return 0.0

        val oldestIdx = if (idx + 1 >= buffer.size) (idx + 1) % buffer.size else 0
        val oldestNs = buffer[oldestIdx]
        val timeDiffSec = (currentNs - oldestNs) / 1_000_000_000.0

        return if (timeDiffSec > 0.001) {
            (count - 1) / timeDiffSec
        } else {
            0.0
        }
    }

    private fun createPacket(
        sensorClass: SensorTypeClass,
        event: SensorEvent,
        deviceId: String,
        sessionId: String,
        sequence: Long
    ): MeasurementPacket {
        val nowUtc = dateFormat.format(Date())
        val meta = discoveredSensors[sensorClass]
        val sensorName = meta?.name ?: event.sensor.name
        val sensorVendor = meta?.vendor ?: event.sensor.vendor

        val (valuesMap, unitsMap) = when (sensorClass) {
            SensorTypeClass.MAGNETOMETER -> {
                val bx = event.values[0].toDouble()
                val by = event.values[1].toDouble()
                val bz = event.values[2].toDouble()
                Pair(
                    mapOf(
                        "bx" to bx,
                        "by" to by,
                        "bz" to bz,
                        // Compatibility with existing VirtualLab gateway
                        "x_ut" to bx,
                        "y_ut" to by,
                        "z_ut" to bz
                    ),
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
                val lux = event.values[0].toDouble()
                Pair(
                    mapOf("lux" to lux),
                    mapOf("lux" to "lx")
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

        return MeasurementPacket(
            schemaVersion = "1",
            deviceId = deviceId,
            sourceSessionId = sessionId,
            sessionId = sessionId,
            measurementType = sensorClass.measurementType,
            sensorId = sensorClass.sensorId,
            sequence = sequence,
            deviceTimestampNs = event.timestamp,
            timestampMonotonicNs = event.timestamp,
            timestampUtc = nowUtc,
            sensor = SensorMetadata(
                name = sensorName,
                vendor = sensorVendor,
                androidType = event.sensor.type,
                accuracy = event.accuracy
            ),
            values = valuesMap,
            units = unitsMap,
            accuracy = event.accuracy
        )
    }

    fun release() {
        stopCapture()
        _liveReadings.value = emptyMap()
    }
}
