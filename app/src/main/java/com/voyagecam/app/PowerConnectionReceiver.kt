package com.voyagecam.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_POWER_CONNECTED) return

        val settings = VoyageCamSettingsStore(context).load()
        if (!settings.autoStartOnPowerConnected) return
        if (!context.canStartRecordingFromBackground()) return

        val capability = VoyageCamSettingsStore(context).loadCapability()
        RecordingForegroundService.start(
            context = context,
            dualCamera = settings.dualCameraEnabled && capability?.isAvailable == true,
            ambientAudio = settings.ambientAudioEnabled,
        )
    }

    private fun Context.canStartRecordingFromBackground(): Boolean {
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val hasNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        return hasCamera && hasNotifications
    }
}
