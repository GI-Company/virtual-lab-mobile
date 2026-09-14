package com.example.acquisition

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

class SensorDiscovery(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun isMagnetometerAvailable(): Boolean {
        return sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
    }
}
