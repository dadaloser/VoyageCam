package com.voyagecam.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_POWER_CONNECTED) return

        val diagnostics = AutoStartDiagnosticsStore(context)
        val settings = VoyageCamSettingsStore(context).load()
        if (!settings.autoStartOnPowerConnected) {
            diagnostics.record(
                source = AutoStartSource.Power,
                result = AutoStartResult.Ignored,
                reason = "充电器自动启动开关未开启",
            )
            return
        }

        val blockedReason = RecordingAutoStartPolicy(context).startIfAllowed(settings)
        diagnostics.record(
            source = AutoStartSource.Power,
            result = if (blockedReason == null) AutoStartResult.Started else AutoStartResult.Ignored,
            reason = blockedReason ?: "已收到充电器连接广播并启动录制",
        )
    }
}
