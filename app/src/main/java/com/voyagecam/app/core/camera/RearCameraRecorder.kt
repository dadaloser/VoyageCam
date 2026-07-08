package com.voyagecam.app.core.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import com.voyagecam.app.data.storage.RecordingStorageManager
import java.io.File

class RearCameraRecorder(
    private val context: Context,
    private val cameraHandler: Handler,
    private val storageManager: RecordingStorageManager,
    private val callbacks: Callbacks,
) {
    private var currentRecording: Recording? = null
    private var outputFile: File? = null
    private var recordingStarted = false
    private var shouldContinueRecording = false
    private var audioEnabled = false
    private var segmentDurationMillis = DEFAULT_SEGMENT_DURATION_MINUTES * 60_000L
    private var segmentIndex = 0
    private var pendingStopReason: StopReason? = null

    private val rotateSegmentTask = Runnable {
        rotateSegment()
    }

    fun start(ambientAudioRequested: Boolean, segmentDurationMinutes: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            callbacks.onRecordingError("相机权限未授权，无法启动录制")
            return
        }

        audioEnabled = ambientAudioRequested &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        segmentDurationMillis = segmentDurationMinutes
            .coerceIn(MIN_SEGMENT_DURATION_MINUTES, MAX_SEGMENT_DURATION_MINUTES) * 60_000L
        shouldContinueRecording = true
        segmentIndex = 0
        pendingStopReason = null

        cameraHandler.post {
            startNextSegment()
        }
    }

    fun stop() {
        cameraHandler.post {
            shouldContinueRecording = false
            cameraHandler.removeCallbacks(rotateSegmentTask)
            val recording = currentRecording
            if (recording == null) {
                callbacks.onRecordingStopped(outputFile)
                return@post
            }
            pendingStopReason = StopReason.Stop
            runCatching { recording.stop() }
                .onFailure { error ->
                    callbacks.onRecordingError(error.message ?: "停止录制失败")
                    callbacks.onRecordingStopped(outputFile)
                }
        }
    }

    fun lockCurrentSegment() {
        cameraHandler.post {
            val recording = currentRecording
            if (!recordingStarted || recording == null) {
                callbacks.onRecordingError("当前没有可锁定的录制片段")
                return@post
            }

            cameraHandler.removeCallbacks(rotateSegmentTask)
            pendingStopReason = StopReason.Lock
            runCatching { recording.stop() }
                .onFailure { error ->
                    callbacks.onRecordingError(error.message ?: "锁定当前片段失败")
                }
        }
    }

    private fun startNextSegment() {
        if (!shouldContinueRecording) return

        val file = storageManager.createNormalSegmentFile(System.currentTimeMillis(), CAMERA_DIRECTION_REAR)
        outputFile = file
        recordingStarted = false
        pendingStopReason = null

        RearCameraCameraXPipeline.startRecording(
            context = context,
            file = file,
            audioEnabled = audioEnabled,
            onReady = { recording ->
                cameraHandler.post {
                    currentRecording = recording
                }
            },
            onEvent = { event ->
                cameraHandler.post {
                    handleVideoRecordEvent(event)
                }
            },
            onError = { message ->
                cameraHandler.post {
                    file.delete()
                    outputFile = null
                    callbacks.onRecordingError(message)
                }
            },
        )
    }

    private fun handleVideoRecordEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> {
                recordingStarted = true
                segmentIndex++
                callbacks.onRecordingStarted(outputFile, segmentIndex)
                cameraHandler.removeCallbacks(rotateSegmentTask)
                cameraHandler.postDelayed(rotateSegmentTask, segmentDurationMillis)
            }
            is VideoRecordEvent.Finalize -> {
                handleRecordingFinalized(event)
            }
        }
    }

    private fun handleRecordingFinalized(event: VideoRecordEvent.Finalize) {
        cameraHandler.removeCallbacks(rotateSegmentTask)
        val finalizedFile = outputFile
        val stopReason = pendingStopReason ?: StopReason.Stop
        currentRecording = null
        recordingStarted = false
        pendingStopReason = null

        if (event.error != VideoRecordEvent.Finalize.ERROR_NONE) {
            callbacks.onRecordingError(event.cause?.message ?: "录制片段完成异常：${event.error}")
        }

        when (stopReason) {
            StopReason.Rotate -> {
                callbacks.onSegmentFinalized(finalizedFile)
                if (shouldContinueRecording) {
                    startNextSegment()
                }
            }
            StopReason.Lock -> {
                callbacks.onSegmentLockRequested(finalizedFile)
                if (shouldContinueRecording) {
                    startNextSegment()
                }
            }
            StopReason.Stop -> {
                callbacks.onRecordingStopped(finalizedFile)
            }
        }
    }

    private fun rotateSegment() {
        if (!shouldContinueRecording) return
        val recording = currentRecording ?: return
        pendingStopReason = StopReason.Rotate
        runCatching { recording.stop() }
            .onFailure { error ->
                callbacks.onRecordingError(error.message ?: "切换到下一录制片段失败")
                pendingStopReason = null
            }
    }

    interface Callbacks {
        fun onRecordingStarted(file: File?, segmentIndex: Int)
        fun onSegmentFinalized(file: File?)
        fun onSegmentLockRequested(file: File?)
        fun onRecordingStopped(file: File?)
        fun onRecordingError(message: String)
    }

    private enum class StopReason {
        Rotate,
        Lock,
        Stop,
    }

    companion object {
        private const val DEFAULT_SEGMENT_DURATION_MINUTES = 3
        private const val MIN_SEGMENT_DURATION_MINUTES = 1
        private const val MAX_SEGMENT_DURATION_MINUTES = 5
        private const val CAMERA_DIRECTION_REAR = "rear"
    }
}
