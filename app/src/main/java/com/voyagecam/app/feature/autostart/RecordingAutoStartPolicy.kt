package com.voyagecam.app.feature.autostart

import android.Manifest
import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.voyagecam.app.R
import com.voyagecam.app.core.model.AutoStartSource
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.data.settings.VoyageCamSettingsStore
import com.voyagecam.app.data.settings.resolveRecordingConfig
import com.voyagecam.app.feature.recording.RecordingForegroundService

class RecordingAutoStartPolicy(private val context: Context) {
    private val settingsStore = VoyageCamSettingsStore(context)

    fun startIfAllowed(
        source: AutoStartSource,
        settings: VoyageCamSettings = settingsStore.load(),
        detail: String = "",
    ): String? {
        val blockedReason = backgroundStartBlockedReason()
        if (blockedReason != null) return blockedReason

        val capability = settingsStore.loadCapability()
        val resolved = settings.resolveRecordingConfig(capability)

        if (requiresUserInitiatedStart()) {
            AutoStartPromptNotifier(context).showStartPrompt(
                source = source,
                dualCamera = resolved.dualCameraActive,
                ambientAudio = resolved.ambientAudioActive,
                detail = detail,
            )
            return context.getString(R.string.autostart_android14_prompt)
        }

        return runCatching {
            RecordingForegroundService.start(
                context = context,
                dualCamera = resolved.dualCameraActive,
                ambientAudio = resolved.ambientAudioActive,
            )
        }.exceptionOrNull()?.toAutoStartBlockedReason()
    }

    fun backgroundStartBlockedReason(): String? {
        val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val hasNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        return when {
            !hasCamera -> context.getString(R.string.autostart_block_camera_permission)
            !hasNotifications -> context.getString(R.string.autostart_block_notification_permission)
            else -> null
        }
    }

    private fun requiresUserInitiatedStart(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !isAppInForeground()
    }

    private fun isAppInForeground(): Boolean {
        val processInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(processInfo)
        return processInfo.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun Throwable.toAutoStartBlockedReason(): String {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                this is ForegroundServiceStartNotAllowedException ->
                context.getString(R.string.autostart_block_foreground_service)
            this is SecurityException ->
                context.getString(R.string.autostart_block_sensitive_permissions)
            else -> context.getString(
                R.string.autostart_block_failed,
                message ?: javaClass.simpleName,
            )
        }
    }
}
