package com.example.camera

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceThermalMonitor(private val context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _thermalStatus = MutableStateFlow(getCurrentThermalStatus())
    val thermalStatus: StateFlow<String> = _thermalStatus.asStateFlow()

    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                    _thermalStatus.value = formatStatus(status)
                }
                powerManager.addThermalStatusListener(context.mainExecutor, thermalListener!!)
            } catch (_: Exception) {}
        }
        update()
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null) {
            try {
                powerManager.removeThermalStatusListener(thermalListener!!)
            } catch (_: Exception) {}
            thermalListener = null
        }
    }

    fun update() {
        _thermalStatus.value = getCurrentThermalStatus()
    }

    private fun getCurrentThermalStatus(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            formatStatus(powerManager.currentThermalStatus)
        } else {
            "NORMAL"
        }
    }

    private fun formatStatus(status: Int): String {
        return when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "NORMAL"
            PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT THROTTLING"
            PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE THROTTLING"
            PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE THROTTLING"
            PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
            else -> "STATUS_$status"
        }
    }
}
