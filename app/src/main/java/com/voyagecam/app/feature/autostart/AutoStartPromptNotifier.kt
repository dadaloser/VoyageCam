package com.voyagecam.app.feature.autostart

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.voyagecam.app.MainActivity
import com.voyagecam.app.R
import com.voyagecam.app.core.model.AutoStartSource
import com.voyagecam.app.feature.recording.RecordingForegroundService
import com.voyagecam.app.ui.labelRes

class AutoStartPromptNotifier(private val context: Context) {
    @SuppressLint("MissingPermission")
    fun showStartPrompt(
        source: AutoStartSource,
        dualCamera: Boolean,
        ambientAudio: Boolean,
        detail: String = "",
    ) {
        ensureNotificationChannel()

        val openAppIntent = PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val startRecordingIntent = RecordingForegroundService.startPendingIntent(
            context = context,
            requestCode = START_RECORDING_REQUEST_CODE + source.ordinal,
            dualCamera = dualCamera,
            ambientAudio = ambientAudio,
        )
        val triggerText = detail.takeIf { it.isNotBlank() } ?: context.getString(source.labelRes())

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.autostart_prompt_title))
            .setContentText(context.getString(R.string.autostart_prompt_text, triggerText))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.autostart_prompt_big_text, triggerText)),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .addAction(0, context.getString(R.string.autostart_prompt_action), startRecordingIntent)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID + source.ordinal, notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.autostart_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.autostart_channel_description)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "voyage_cam_auto_start"
        const val NOTIFICATION_ID = 1101
        const val OPEN_APP_REQUEST_CODE = 2101
        const val START_RECORDING_REQUEST_CODE = 2201
    }
}
