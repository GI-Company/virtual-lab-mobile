package com.example.identity

import android.content.Context
import android.os.Build
import java.util.UUID

data class DeviceMetadata(
    val deviceId: String,
    val identityType: String = "LOCAL APP ID",
    val androidVersion: String,
    val sdkLevel: Int,
    val targetSdk: Int
)

class DeviceIdentityManager(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "sensornode_identity"
        private const val KEY_SENSOR_NODE_ID = "sensor_node_id"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val deviceId: String = getOrCreateDeviceId()

    val metadata: DeviceMetadata = DeviceMetadata(
        deviceId = deviceId,
        identityType = "LOCAL APP ID",
        androidVersion = Build.VERSION.RELEASE ?: "Unknown",
        sdkLevel = Build.VERSION.SDK_INT,
        targetSdk = context.applicationInfo.targetSdkVersion
    )

    private fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_SENSOR_NODE_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }

        // Generate persistent UUID-based ID, e.g. ANDROID-8f27a93e
        // Do NOT use IMEI, MAC address, hardware serial, phone number, advertising ID
        val shortUuid = UUID.randomUUID().toString().replace("-", "").take(8).lowercase()
        val newId = "ANDROID-$shortUuid"
        prefs.edit().putString(KEY_SENSOR_NODE_ID, newId).apply()
        return newId
    }
}
