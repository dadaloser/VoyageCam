package com.voyagecam.app.feature.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingNotificationStateTest {
    @Test
    fun modeLabelShowsAutoDualWhenDualCameraActive() {
        assertEquals(
            "自动模式（当前双摄）",
            notificationState(recordingModeAuto = true, dualCamera = true).modeLabel(),
        )
    }

    @Test
    fun modeLabelShowsAutoRearWhenAutoModeFallsBackToRear() {
        assertEquals(
            "自动模式（当前后摄）",
            notificationState(recordingModeAuto = true, dualCamera = false).modeLabel(),
        )
    }

    @Test
    fun modeLabelShowsRearOnlyWhenManualRearModeSelected() {
        assertEquals(
            "仅后摄",
            notificationState(recordingModeAuto = false, dualCamera = false).modeLabel(),
        )
    }

    private fun notificationState(
        recordingModeAuto: Boolean,
        dualCamera: Boolean,
    ): RecordingNotificationState {
        return RecordingNotificationState(
            startedAtMillis = 0L,
            recordingModeAuto = recordingModeAuto,
            dualCamera = dualCamera,
            ambientAudio = false,
            recordingResolutionLabel = "1080p",
            recordingFrameRateLabel = "30fps",
            recordingBitrateLabel = "12Mbps",
            segmentDurationMinutes = 3,
            storageCapacityGb = 10,
            lockedSegmentCount = 0,
            status = "",
            currentFileName = null,
            segmentTransitionSummary = null,
            dualCameraDiagnostic = null,
            performanceGuardSummary = null,
        )
    }
}
