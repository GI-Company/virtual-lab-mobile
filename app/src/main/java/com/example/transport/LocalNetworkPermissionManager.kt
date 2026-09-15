package com.example.transport

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class PermissionStatus {
    GRANTED,
    DENIED,
    NOT_REQUIRED,
    NOT_SUPPORTED
}

class LocalNetworkPermissionManager(private val context: Context) {
    companion object {
        const val PERMISSION_NEARBY_WIFI_DEVICES = Manifest.permission.NEARBY_WIFI_DEVICES
        const val PERMISSION_ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }

    /**
     * Checks if all permissions required for local network NSD & socket access are granted.
     * On Android 13+ (API 33+), NEARBY_WIFI_DEVICES is required for mDNS/NSD discovery.
     * On Android 17+ (API 37+), ACCESS_LOCAL_NETWORK will be required for LAN socket access.
     */
    fun hasRequiredPermissions(): Boolean {
        // Check NEARBY_WIFI_DEVICES for API 33+ (including Android 16 / API 36)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                PERMISSION_NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        // Architecture prepared for API 37+ (Android 17+)
        if (Build.VERSION.SDK_INT >= 37) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                PERMISSION_ACCESS_LOCAL_NETWORK
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        return true
    }

    fun getNearbyWifiDevicesStatus(): PermissionStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                PERMISSION_NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
        } else {
            PermissionStatus.NOT_REQUIRED
        }
    }

    fun getLocalNetworkPermissionStatus(): PermissionStatus {
        return if (Build.VERSION.SDK_INT >= 37) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                PERMISSION_ACCESS_LOCAL_NETWORK
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
        } else {
            PermissionStatus.NOT_SUPPORTED
        }
    }

    fun getPermissionsToRequest(): List<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (getNearbyWifiDevicesStatus() == PermissionStatus.DENIED) {
                permissions.add(PERMISSION_NEARBY_WIFI_DEVICES)
            }
        }
        if (Build.VERSION.SDK_INT >= 37) {
            if (getLocalNetworkPermissionStatus() == PermissionStatus.DENIED) {
                permissions.add(PERMISSION_ACCESS_LOCAL_NETWORK)
            }
        }
        return permissions
    }
}
