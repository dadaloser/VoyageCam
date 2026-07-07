package com.voyagecam.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class RecordingAutoStartPolicy(private val context: Context) {
    private val settingsStore = VoyageCamSettingsStore(context)

    fun startIfAllowed(settings: VoyageCamSettings = settingsStore.load()) {
        if (!canStartRecordingFromBackground()) return
        val capability = settingsStore.loadCapability()
        RecordingForegroundService.start(
            context = context,
            dualCamera = settings.dualCameraEnabled && capability?.isAvailable == true,
            ambientAudio = settings.ambientAudioEnabled,
        )
    }

    fun canStartRecordingFromBackground(): Boolean {
        val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val hasNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        return hasCamera && hasNotifications
    }
}
