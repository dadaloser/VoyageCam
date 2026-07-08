package com.voyagecam.app.feature.recording

import com.voyagecam.app.core.camera.RecordingSegmentFileSet
import com.voyagecam.app.core.model.CollisionSensitivity
import com.voyagecam.app.data.settings.VoyageCamSettingsStore

class RecordingServiceState {
    var startedAtMillis: Long = 0L
    var dualCamera: Boolean = false
    var ambientAudio: Boolean = false
    var gpsMetadataEnabled: Boolean = true
    var storageCapacityGb: Int = VoyageCamSettingsStore.MIN_STORAGE_GB
    var segmentDurationMinutes: Int = 3
    var collisionSensitivity: CollisionSensitivity = CollisionSensitivity.Medium
    var currentSegmentIndex: Int = 0
    var status: String = "正在准备后置摄像头"
    var currentFileName: String? = null
    var previousSegmentFiles: RecordingSegmentFileSet = RecordingSegmentFileSet(rear = null)
    var currentSegmentFiles: RecordingSegmentFileSet = RecordingSegmentFileSet(rear = null)
    var pendingLockNextSegment: Boolean = false
    var pendingLockNextEventId: String? = null
    var lockedSegmentCount: Int = 0
    var segmentTransitionSummary: String? = null

    fun resetForStart(
        startedAtMillis: Long,
        dualCamera: Boolean,
        ambientAudio: Boolean,
        gpsMetadataEnabled: Boolean,
        storageCapacityGb: Int,
        segmentDurationMinutes: Int,
        collisionSensitivity: CollisionSensitivity,
    ) {
        this.startedAtMillis = startedAtMillis
        this.dualCamera = dualCamera
        this.ambientAudio = ambientAudio
        this.gpsMetadataEnabled = gpsMetadataEnabled
        this.storageCapacityGb = storageCapacityGb
        this.segmentDurationMinutes = segmentDurationMinutes
        this.collisionSensitivity = collisionSensitivity
        currentSegmentIndex = 0
        status = "正在准备后置摄像头，每 ${segmentDurationMinutes} 分钟自动分段"
        currentFileName = null
        previousSegmentFiles = RecordingSegmentFileSet(rear = null)
        currentSegmentFiles = RecordingSegmentFileSet(rear = null)
        pendingLockNextSegment = false
        pendingLockNextEventId = null
        lockedSegmentCount = 0
        segmentTransitionSummary = null
    }

    fun clearAfterStop() {
        startedAtMillis = 0L
    }

    fun notificationState(): RecordingNotificationState {
        return RecordingNotificationState(
            startedAtMillis = startedAtMillis,
            dualCamera = dualCamera,
            ambientAudio = ambientAudio,
            segmentDurationMinutes = segmentDurationMinutes,
            storageCapacityGb = storageCapacityGb,
            lockedSegmentCount = lockedSegmentCount,
            status = status,
            currentFileName = currentFileName,
            segmentTransitionSummary = segmentTransitionSummary,
        )
    }
}
