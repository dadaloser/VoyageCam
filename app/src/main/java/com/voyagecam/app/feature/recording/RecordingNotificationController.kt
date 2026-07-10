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
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.data.settings.RecordingMode
import com.voyagecam.app.data.settings.recordingModeLabel
import java.util.Locale

data class RecordingNotificationState(
    val startedAtMillis: Long,
    val requestedMode: RecordingMode,
    val primaryCameraDirection: CameraDirection,
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
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(channel)
    }

    fun notify(state: RecordingNotificationState) {
        notificationManager.notify(NOTIFICATION_ID, build(state))
    }

    fun build(state: RecordingNotificationState): Notification {
        val mode = state.modeLabel(context)
        val profile = "${state.recordingResolutionLabel}/${state.recordingFrameRateLabel}/${state.recordingBitrateLabel}"
        val audio = if (state.ambientAudio) {
            context.getString(R.string.notification_audio_on)
        } else {
            context.getString(R.string.notification_audio_off)
        }
        val segment = context.getString(R.string.notification_segment_minutes, state.segmentDurationMinutes)
        val locked = if (state.lockedSegmentCount > 0) {
            context.getString(R.string.notification_locked_count, state.lockedSegmentCount)
        } else {
            ""
        }
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
        val performanceText = state.performanceGuardSummary
            ?.let { context.getString(R.string.notification_performance_prefix, it) }
            .orEmpty()

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
            .setContentTitle(context.getString(R.string.notification_content_title))
            .setContentText(
                context.getString(
                    R.string.notification_content_text,
                    mode,
                    profile,
                    segment,
                    locked,
                    elapsedText,
                    fileText,
                ),
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        context.getString(
                            R.string.notification_big_text,
                            state.status,
                            mode,
                            profile,
                            audio,
                            segment,
                            locked,
                            transitionText,
                            diagnosticText,
                            performanceText,
                            state.storageCapacityGb,
                            elapsedText,
                            fileText,
                        ),
                    ),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, context.getString(R.string.notification_action_lock), lockIntent)
            .addAction(0, context.getString(R.string.notification_action_stop), stopIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "voyage_cam_recording"
        const val NOTIFICATION_ID = 1001
    }
}

internal fun RecordingNotificationState.modeLabel(context: Context): String {
    return context.recordingModeLabel(
        requestedMode = requestedMode,
        dualCameraActive = dualCamera,
        primaryCameraDirection = primaryCameraDirection,
    )
}
