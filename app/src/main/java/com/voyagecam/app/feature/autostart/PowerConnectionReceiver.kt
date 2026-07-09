package com.voyagecam.app.feature.autostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.voyagecam.app.R
import com.voyagecam.app.core.model.AutoStartResult
import com.voyagecam.app.core.model.AutoStartSource
import com.voyagecam.app.data.autostart.AutoStartDiagnosticsStore
import com.voyagecam.app.data.settings.VoyageCamSettingsStore

class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_POWER_CONNECTED) return

        val diagnostics = AutoStartDiagnosticsStore(context)
        val settings = VoyageCamSettingsStore(context).load()
        if (!settings.autoStartOnPowerConnected) {
            diagnostics.record(
                source = AutoStartSource.Power,
                result = AutoStartResult.Ignored,
                reason = context.getString(R.string.autostart_power_toggle_off),
            )
            return
        }

        val blockedReason = RecordingAutoStartPolicy(context).startIfAllowed(
            source = AutoStartSource.Power,
            settings = settings,
        )
        diagnostics.record(
            source = AutoStartSource.Power,
            result = if (blockedReason == null) AutoStartResult.Started else AutoStartResult.Ignored,
            reason = blockedReason ?: context.getString(R.string.autostart_power_started),
        )
    }
}
