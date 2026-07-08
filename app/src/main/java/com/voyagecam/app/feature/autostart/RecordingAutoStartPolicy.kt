package com.voyagecam.app.feature.autostart

import android.Manifest
import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.voyagecam.app.core.model.AutoStartSource
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.data.settings.VoyageCamSettingsStore
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
        val dualCamera = settings.dualCameraEnabled && capability?.isAvailable == true
        val ambientAudio = settings.ambientAudioEnabled

        if (requiresUserInitiatedStart()) {
            AutoStartPromptNotifier(context).showStartPrompt(
                source = source,
                dualCamera = dualCamera,
                ambientAudio = ambientAudio,
                detail = detail,
            )
            return "Android 14+ 限制后台直接启动相机录制，已显示启动通知"
        }

        return runCatching {
            RecordingForegroundService.start(
                context = context,
                dualCamera = dualCamera,
                ambientAudio = ambientAudio,
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
            !hasCamera -> "相机权限未授权"
            !hasNotifications -> "通知权限未授权"
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
                "系统限制后台启动前台录制服务"
            this is SecurityException ->
                "系统限制后台使用相机/麦克风/定位权限启动录制"
            else -> "启动录制失败：${message ?: javaClass.simpleName}"
        }
    }
}
