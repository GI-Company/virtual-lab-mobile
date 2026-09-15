package com.example.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

enum class CameraPermissionState {
    NOT_REQUESTED,
    GRANTED,
    DENIED
}

class CameraPermissionManager(private val context: Context) {
    companion object {
        const val PERMISSION_CAMERA = Manifest.permission.CAMERA
    }

    fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getPermissionState(): CameraPermissionState {
        return if (isCameraPermissionGranted()) {
            CameraPermissionState.GRANTED
        } else {
            CameraPermissionState.DENIED
        }
    }
}
