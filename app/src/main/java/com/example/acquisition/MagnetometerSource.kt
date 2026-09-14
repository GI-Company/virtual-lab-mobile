package com.example.acquisition

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MagnetometerSource(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    fun startListening(): Flow<MagnetometerData> = callbackFlow {
        if (magnetometer == null) {
            close(IllegalStateException("Magnetometer not available"))
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(
                    MagnetometerData(
                        x = event.values[0],
                        y = event.values[1],
                        z = event.values[2],
                        timestamp = event.timestamp,
                        accuracy = event.accuracy,
                        sensorName = event.sensor.name,
                        sensorVendor = event.sensor.vendor,
                        androidType = event.sensor.type
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        // Delay NORMAL is about 200,000 microsecond (5Hz)
        // Delay UI is about 60,000 microsecond (16Hz)
        // Delay GAME is about 20,000 microsecond (50Hz)
        // Delay FASTEST is 0 microsecond
        sensorManager.registerListener(
            listener,
            magnetometer,
            SensorManager.SENSOR_DELAY_GAME
        )

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}

data class MagnetometerData(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestamp: Long,
    val accuracy: Int,
    val sensorName: String,
    val sensorVendor: String,
    val androidType: Int
)
