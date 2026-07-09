package com.voyagecam.app.feature.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.voyagecam.app.MainActivity
import com.voyagecam.app.R
import com.voyagecam.app.data.settings.recordingModeLabel
import java.util.Locale

data class RecordingNotificationState(
    val startedAtMillis: Long,
    val recordingModeAuto: Boolean,
    val dualCamera: Boolean,
    val ambientAudio: Boolean,
    val recordingResolutionLabel: String,
    val recordingFrameRateLabel: String,
    val recordingBitrateLabel: String,
    val segmentDurationMinutes: Int,
    val storageCapacityGb: Int,
    val lockedSegmentCount: Int,
    val status: String,
    val currentFileName: String?,
    val segmentTransitionSummary: String?,
    val dualCameraDiagnostic: String?,
    val performanceGuardSummary: String?,
)

class RecordingNotificationController(private val context: Context) {
    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "VoyageCam 录制状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示行车记录录制状态，并提供停止入口"
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(channel)
    }

    fun notify(state: RecordingNotificationState) {
        notificationManager.notify(NOTIFICATION_ID, build(state))
    }

    fun build(state: RecordingNotificationState): Notification {
        val mode = state.modeLabel()
        val profile = "${state.recordingResolutionLabel}/${state.recordingFrameRateLabel}/${state.recordingBitrateLabel}"
        val audio = if (state.ambientAudio) "环境声开启" else "静音"
        val segment = "${state.segmentDurationMinutes}分钟分段"
        val locked = if (state.lockedSegmentCount > 0) " · 已锁定${state.lockedSegmentCount}段" else ""
        val elapsed = ((System.currentTimeMillis() - state.startedAtMillis).coerceAtLeast(0L)) / 1000L
        val elapsedText = String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            elapsed / 3600,
            elapsed % 3600 / 60,
            elapsed % 60,
        )
        val fileText = state.currentFileName?.let { " · $it" }.orEmpty()
        val transitionText = state.segmentTransitionSummary?.let { " · $it" }.orEmpty()
        val diagnosticText = state.dualCameraDiagnostic?.let { " · $it" }.orEmpty()
        val performanceText = state.performanceGuardSummary?.let { " · 性能保护：$it" }.orEmpty()

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, RecordingForegroundService::class.java).setAction(RecordingForegroundService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val lockIntent = PendingIntent.getService(
            context,
            2,
            Intent(context, RecordingForegroundService::class.java).setAction(RecordingForegroundService.ACTION_LOCK_CURRENT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("VoyageCam 正在录制")
            .setContentText("$mode · $profile · $segment$locked · $elapsedText$fileText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${state.status} · $mode · $profile · $audio · $segment$locked$transitionText$diagnosticText$performanceText · ${state.storageCapacityGb}GB循环空间 · 已运行 $elapsedText$fileText"),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, "紧急锁定", lockIntent)
            .addAction(0, "停止录制", stopIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "voyage_cam_recording"
        const val NOTIFICATION_ID = 1001
    }
}

internal fun RecordingNotificationState.modeLabel(): String {
    return recordingModeLabel(
        recordingModeAuto = recordingModeAuto,
        dualCameraActive = dualCamera,
    )
}
