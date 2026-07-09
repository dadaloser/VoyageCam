package com.voyagecam.app.core.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import com.voyagecam.app.core.model.DualCameraDiagnostic
import com.voyagecam.app.data.settings.RecordingVideoProfile
import java.io.File

object DualCameraRecordingPipeline {
    fun startRecording(
        context: Context,
        rearFile: File,
        frontFile: File,
        audioEnabled: Boolean,
        videoProfile: RecordingVideoProfile,
        onReady: (DualCameraRecordingSession) -> Unit,
        onEvent: (DualCameraRecordEvent) -> Unit,
        onError: (DualCameraDiagnostic) -> Unit,
    ) {
        DualCameraSessionCoordinator.startRecording(
            context = context,
            rearFile = rearFile,
            frontFile = frontFile,
            audioEnabled = audioEnabled,
            videoProfile = videoProfile,
            onReady = onReady,
            onEvent = onEvent,
            onError = onError,
        )
    }

    fun stop() {
        DualCameraSessionCoordinator.stop()
    }
}

data class DualCameraRecordingSession(
    val rearRecording: Recording,
    val frontRecording: Recording,
    val onStopped: () -> Unit = {},
) {
    fun stop() {
        runCatching { rearRecording.stop() }
        runCatching { frontRecording.stop() }
        onStopped()
    }
}

data class DualCameraRecordEvent(
    val camera: Int,
    val event: VideoRecordEvent,
)
