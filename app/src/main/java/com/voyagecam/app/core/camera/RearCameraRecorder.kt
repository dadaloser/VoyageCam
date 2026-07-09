package com.voyagecam.app.core.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import com.voyagecam.app.R
import com.voyagecam.app.core.model.DualCameraDiagnostic
import com.voyagecam.app.data.settings.RecordingBitratePreset
import com.voyagecam.app.data.settings.RecordingFrameRatePreset
import com.voyagecam.app.data.settings.RecordingResolutionPreset
import com.voyagecam.app.data.settings.RecordingVideoProfile
import com.voyagecam.app.data.storage.RecordingStorageManager
import com.voyagecam.app.ui.dualCameraDiagnosticSummary
import java.io.File

data class RecordingSegmentFileSet(
    val rear: File?,
    val front: File? = null,
) {
    val primary: File?
        get() = rear ?: front

    val files: List<File>
        get() = listOfNotNull(rear, front)
}

data class RecordingSegmentTransitionStats(
    val completedSegmentIndex: Int,
    val stopToFinalizeMillis: Long?,
    val finalizeToNextStartMillis: Long,
)

class RearCameraRecorder(
    private val context: Context,
    private val cameraHandler: Handler,
    private val storageManager: RecordingStorageManager,
    private val callbacks: Callbacks,
) {
    private var currentRecording: Recording? = null
    private var currentDualRecording: DualCameraRecordingSession? = null
    private var outputFile: File? = null
    private var outputFrontFile: File? = null
    private var recordingStarted = false
    private var dualRecordingMode = false
    private var shouldContinueRecording = false
    private var audioEnabled = false
    private var segmentDurationMillis = DEFAULT_SEGMENT_DURATION_MINUTES * 60_000L
    private var segmentIndex = 0
    private var recordingVideoProfile = RecordingVideoProfile(
        resolution = RecordingResolutionPreset.FHD_1080P,
        frameRate = RecordingFrameRatePreset.FPS_30,
        bitrate = RecordingBitratePreset.MBPS_12,
    )
    private var pendingStopReason: StopReason? = null
    private var rotationStopRequestedAtElapsed: Long? = null
    private var rotationSegmentIndex: Int? = null
    private var pendingRotationStats: PendingRotationStats? = null

    private val rotateSegmentTask = Runnable {
        rotateSegment()
    }

    fun start(
        ambientAudioRequested: Boolean,
        segmentDurationMinutes: Int,
        dualCameraRequested: Boolean = false,
        videoProfile: RecordingVideoProfile,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            callbacks.onRecordingError(context.getString(R.string.camera_error_permission_recording))
            return
        }

        audioEnabled = ambientAudioRequested &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        segmentDurationMillis = segmentDurationMinutes
            .coerceIn(MIN_SEGMENT_DURATION_MINUTES, MAX_SEGMENT_DURATION_MINUTES) * 60_000L
        shouldContinueRecording = true
        dualRecordingMode = dualCameraRequested
        recordingVideoProfile = videoProfile
        segmentIndex = 0
        pendingStopReason = null
        rotationStopRequestedAtElapsed = null
        rotationSegmentIndex = null
        pendingRotationStats = null

        cameraHandler.post {
            startNextSegment()
        }
    }

    fun stop() {
        cameraHandler.post {
            shouldContinueRecording = false
            cameraHandler.removeCallbacks(rotateSegmentTask)
            val recording = currentRecording
            val dualRecording = currentDualRecording
            if (recording == null && dualRecording == null) {
                callbacks.onRecordingStopped(currentFiles())
                return@post
            }
            pendingStopReason = StopReason.Stop
            rotationStopRequestedAtElapsed = null
            rotationSegmentIndex = null
            pendingRotationStats = null
            runCatching {
                dualRecording?.stop() ?: recording?.stop()
            }
                .onFailure { error ->
                    callbacks.onRecordingError(error.message ?: context.getString(R.string.camera_error_stop_failed))
                    callbacks.onRecordingStopped(currentFiles())
                }
        }
    }

    fun lockCurrentSegment() {
        cameraHandler.post {
            val recording = currentRecording
            val dualRecording = currentDualRecording
            if (!recordingStarted || (recording == null && dualRecording == null)) {
                callbacks.onRecordingError(context.getString(R.string.camera_error_no_lockable_segment))
                return@post
            }

            cameraHandler.removeCallbacks(rotateSegmentTask)
            pendingStopReason = StopReason.Lock
            rotationStopRequestedAtElapsed = null
            rotationSegmentIndex = null
            pendingRotationStats = null
            runCatching {
                dualRecording?.stop() ?: recording?.stop()
            }
                .onFailure { error ->
                    callbacks.onRecordingError(error.message ?: context.getString(R.string.camera_error_lock_failed))
                }
        }
    }

    fun downgradeToRearOnly(reason: String) {
        cameraHandler.post {
            if (!dualRecordingMode) return@post
            dualRecordingMode = false
            val dualRecording = currentDualRecording
            if (dualRecording == null) {
                outputFrontFile?.delete()
                outputFrontFile = null
                callbacks.onRecordingError(reason)
                return@post
            }

            cameraHandler.removeCallbacks(rotateSegmentTask)
            pendingStopReason = StopReason.Rotate
            rotationStopRequestedAtElapsed = SystemClock.elapsedRealtime()
            rotationSegmentIndex = segmentIndex
            runCatching {
                dualRecording.stop()
            }.onFailure { error ->
                pendingStopReason = null
                callbacks.onRecordingError(error.message ?: context.getString(R.string.camera_error_downgrade_failed))
            }
        }
    }

    private fun startNextSegment() {
        if (!shouldContinueRecording) return

        val startedAtMillis = System.currentTimeMillis()
        val file = storageManager.createNormalSegmentFile(startedAtMillis, CAMERA_DIRECTION_REAR)
        val frontFile = if (dualRecordingMode) {
            storageManager.createNormalSegmentFile(startedAtMillis, CAMERA_DIRECTION_FRONT)
        } else {
            null
        }
        outputFile = file
        outputFrontFile = frontFile
        recordingStarted = false
        pendingStopReason = null

        if (frontFile != null) {
            startNextDualSegment(file, frontFile)
        } else {
            startNextRearOnlySegment(file)
        }
    }

    private fun startNextRearOnlySegment(file: File) {
        RearCameraCameraXPipeline.startRecording(
            context = context,
            file = file,
            audioEnabled = audioEnabled,
            videoProfile = recordingVideoProfile,
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
                    outputFrontFile = null
                    callbacks.onRecordingError(message)
                }
            },
        )
    }

    private fun startNextDualSegment(rearFile: File, frontFile: File) {
        DualCameraRecordingPipeline.startRecording(
            context = context,
            rearFile = rearFile,
            frontFile = frontFile,
            audioEnabled = audioEnabled,
            videoProfile = recordingVideoProfile,
            onReady = { recording ->
                cameraHandler.post {
                    currentDualRecording = recording
                }
            },
            onEvent = { event ->
                cameraHandler.post {
                    handleDualVideoRecordEvent(event)
                }
            },
            onError = { diagnostic ->
                cameraHandler.post {
                    dualRecordingMode = false
                    frontFile.delete()
                    outputFrontFile = null
                    callbacks.onDualCameraFallback(diagnostic)
                    callbacks.onRecordingError(
                        context.getString(
                            R.string.camera_error_dual_fallback_rear,
                            context.dualCameraDiagnosticSummary(diagnostic),
                        ),
                    )
                    startNextRearOnlySegment(rearFile)
                }
            },
        )
    }

    private fun handleVideoRecordEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> {
                notifyPendingRotationStatsIfNeeded()
                recordingStarted = true
                segmentIndex++
                callbacks.onRecordingStarted(currentFiles(), segmentIndex)
                cameraHandler.removeCallbacks(rotateSegmentTask)
                cameraHandler.postDelayed(rotateSegmentTask, segmentDurationMillis)
            }
            is VideoRecordEvent.Finalize -> {
                handleRecordingFinalized(event)
            }
        }
    }

    private fun handleRecordingFinalized(event: VideoRecordEvent.Finalize) {
        val finalizedAtElapsed = SystemClock.elapsedRealtime()
        cameraHandler.removeCallbacks(rotateSegmentTask)
        val finalizedFiles = currentFiles()
        val stopReason = pendingStopReason ?: StopReason.Stop
        if (stopReason == StopReason.Rotate) {
            pendingRotationStats = PendingRotationStats(
                completedSegmentIndex = rotationSegmentIndex ?: segmentIndex,
                stopToFinalizeMillis = rotationStopRequestedAtElapsed?.let { finalizedAtElapsed - it },
                finalizedAtElapsed = finalizedAtElapsed,
            )
        } else {
            pendingRotationStats = null
        }
        rotationStopRequestedAtElapsed = null
        rotationSegmentIndex = null
        currentRecording = null
        currentDualRecording = null
        recordingStarted = false
        pendingStopReason = null

        if (event.error != VideoRecordEvent.Finalize.ERROR_NONE) {
            callbacks.onRecordingError(
                event.cause?.message
                    ?: context.getString(R.string.camera_error_finalize_failed, event.error),
            )
        }

        when (stopReason) {
            StopReason.Rotate -> {
                callbacks.onSegmentFinalized(finalizedFiles)
                if (shouldContinueRecording) {
                    startNextSegment()
                }
            }
            StopReason.Lock -> {
                callbacks.onSegmentLockRequested(finalizedFiles)
                if (shouldContinueRecording) {
                    startNextSegment()
                }
            }
            StopReason.Stop -> {
                callbacks.onRecordingStopped(finalizedFiles)
            }
        }
    }

    private fun rotateSegment() {
        if (!shouldContinueRecording) return
        val recording = currentRecording
        val dualRecording = currentDualRecording
        if (recording == null && dualRecording == null) return
        pendingStopReason = StopReason.Rotate
        rotationStopRequestedAtElapsed = SystemClock.elapsedRealtime()
        rotationSegmentIndex = segmentIndex
        runCatching {
            dualRecording?.stop() ?: recording?.stop()
        }
            .onFailure { error ->
                callbacks.onRecordingError(error.message ?: context.getString(R.string.camera_error_rotate_failed))
                pendingStopReason = null
            }
    }

    private fun handleDualVideoRecordEvent(event: DualCameraRecordEvent) {
        when (val recordEvent = event.event) {
            is VideoRecordEvent.Start -> {
                if (event.camera == CameraSelector.LENS_FACING_BACK) {
                    notifyPendingRotationStatsIfNeeded()
                    recordingStarted = true
                    segmentIndex++
                    callbacks.onRecordingStarted(currentFiles(), segmentIndex)
                    cameraHandler.removeCallbacks(rotateSegmentTask)
                    cameraHandler.postDelayed(rotateSegmentTask, segmentDurationMillis)
                }
            }
            is VideoRecordEvent.Finalize -> {
                if (event.camera == CameraSelector.LENS_FACING_BACK) {
                    handleRecordingFinalized(recordEvent)
                }
            }
        }
    }

    private fun currentFiles(): RecordingSegmentFileSet {
        return RecordingSegmentFileSet(rear = outputFile, front = outputFrontFile)
    }

    private fun notifyPendingRotationStatsIfNeeded() {
        val stats = pendingRotationStats ?: return
        pendingRotationStats = null
        callbacks.onSegmentTransitionMeasured(
            RecordingSegmentTransitionStats(
                completedSegmentIndex = stats.completedSegmentIndex,
                stopToFinalizeMillis = stats.stopToFinalizeMillis,
                finalizeToNextStartMillis = SystemClock.elapsedRealtime() - stats.finalizedAtElapsed,
            ),
        )
    }

    interface Callbacks {
        fun onRecordingStarted(files: RecordingSegmentFileSet, segmentIndex: Int)
        fun onSegmentFinalized(files: RecordingSegmentFileSet)
        fun onSegmentLockRequested(files: RecordingSegmentFileSet)
        fun onRecordingStopped(files: RecordingSegmentFileSet)
        fun onRecordingError(message: String)
        fun onDualCameraFallback(diagnostic: DualCameraDiagnostic) = Unit
        fun onSegmentTransitionMeasured(stats: RecordingSegmentTransitionStats) = Unit
    }

    private enum class StopReason {
        Rotate,
        Lock,
        Stop,
    }

    private data class PendingRotationStats(
        val completedSegmentIndex: Int,
        val stopToFinalizeMillis: Long?,
        val finalizedAtElapsed: Long,
    )

    companion object {
        private const val DEFAULT_SEGMENT_DURATION_MINUTES = 3
        private const val MIN_SEGMENT_DURATION_MINUTES = 1
        private const val MAX_SEGMENT_DURATION_MINUTES = 5
        private const val CAMERA_DIRECTION_REAR = "rear"
        private const val CAMERA_DIRECTION_FRONT = "front"
    }
}
