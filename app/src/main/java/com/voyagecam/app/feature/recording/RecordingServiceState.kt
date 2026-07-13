package com.voyagecam.app.feature.recording

import android.content.Context
import com.voyagecam.app.R
import com.voyagecam.app.core.camera.RecordingSegmentFileSet
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.core.model.CollisionSensitivity
import com.voyagecam.app.data.settings.RecordingMode
import com.voyagecam.app.data.settings.VoyageCamSettingsStore

class RecordingServiceState {
    var startedAtMillis: Long = 0L
    var startupInProgress: Boolean = false
    var requestedMode: RecordingMode = RecordingMode.RearOnly
    var primaryCameraDirection: CameraDirection = CameraDirection.Rear
    var dualCamera: Boolean = false
    var ambientAudio: Boolean = false
    var recordingResolutionLabel: String = "1080p"
    var recordingFrameRateLabel: String = "30fps"
    var recordingBitrateLabel: String = "12Mbps"
    var gpsMetadataEnabled: Boolean = false
    var storageCapacityGb: Int = VoyageCamSettingsStore.MIN_STORAGE_GB
    var segmentDurationMinutes: Int = 3
    var collisionSensitivity: CollisionSensitivity = CollisionSensitivity.Medium
    var currentSegmentIndex: Int = 0
    var status: String = ""
    var currentFileName: String? = null
    var previousSegmentFiles: RecordingSegmentFileSet = RecordingSegmentFileSet(rear = null)
    var currentSegmentFiles: RecordingSegmentFileSet = RecordingSegmentFileSet(rear = null)
    var pendingLockNextSegment: Boolean = false
    var pendingLockNextEventId: String? = null
    var lockedSegmentCount: Int = 0
    var segmentTransitionSummary: String? = null
    var fallbackSummary: String? = null
    var dualCameraDiagnostic: String? = null
    var performanceGuardSummary: String? = null
    var stopRequested: Boolean = false

    fun resetForStart(
        context: Context,
        startedAtMillis: Long,
        requestedMode: RecordingMode,
        primaryCameraDirection: CameraDirection,
        dualCamera: Boolean,
        ambientAudio: Boolean,
        recordingResolutionLabel: String,
        recordingFrameRateLabel: String,
        recordingBitrateLabel: String,
        gpsMetadataEnabled: Boolean,
        storageCapacityGb: Int,
        segmentDurationMinutes: Int,
        collisionSensitivity: CollisionSensitivity,
    ) {
        this.startedAtMillis = startedAtMillis
        startupInProgress = true
        this.requestedMode = requestedMode
        this.primaryCameraDirection = primaryCameraDirection
        this.dualCamera = dualCamera
        this.ambientAudio = ambientAudio
        this.recordingResolutionLabel = recordingResolutionLabel
        this.recordingFrameRateLabel = recordingFrameRateLabel
        this.recordingBitrateLabel = recordingBitrateLabel
        this.gpsMetadataEnabled = gpsMetadataEnabled
        this.storageCapacityGb = storageCapacityGb
        this.segmentDurationMinutes = segmentDurationMinutes
        this.collisionSensitivity = collisionSensitivity
        currentSegmentIndex = 0
        status = context.getString(R.string.recording_service_startup_cleanup)
        currentFileName = null
        previousSegmentFiles = RecordingSegmentFileSet(rear = null)
        currentSegmentFiles = RecordingSegmentFileSet(rear = null)
        pendingLockNextSegment = false
        pendingLockNextEventId = null
        lockedSegmentCount = 0
        segmentTransitionSummary = null
        fallbackSummary = null
        dualCameraDiagnostic = null
        performanceGuardSummary = null
        stopRequested = false
    }

    fun clearAfterStop() {
        startedAtMillis = 0L
        startupInProgress = false
        stopRequested = false
    }

    fun notificationState(): RecordingNotificationState {
        return RecordingNotificationState(
            startedAtMillis = startedAtMillis,
            startupInProgress = startupInProgress,
            requestedMode = requestedMode,
            primaryCameraDirection = primaryCameraDirection,
            dualCamera = dualCamera,
            ambientAudio = ambientAudio,
            recordingResolutionLabel = recordingResolutionLabel,
            recordingFrameRateLabel = recordingFrameRateLabel,
            recordingBitrateLabel = recordingBitrateLabel,
            segmentDurationMinutes = segmentDurationMinutes,
            storageCapacityGb = storageCapacityGb,
            lockedSegmentCount = lockedSegmentCount,
            status = status,
            currentFileName = currentFileName,
            segmentTransitionSummary = segmentTransitionSummary,
            fallbackSummary = fallbackSummary,
            dualCameraDiagnostic = dualCameraDiagnostic,
            performanceGuardSummary = performanceGuardSummary,
        )
    }
}
