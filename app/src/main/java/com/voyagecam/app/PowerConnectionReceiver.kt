package com.voyagecam.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_POWER_CONNECTED) return

        val settings = VoyageCamSettingsStore(context).load()
        if (!settings.autoStartOnPowerConnected) return

        RecordingAutoStartPolicy(context).startIfAllowed(settings)
    }
}
