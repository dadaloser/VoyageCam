package com.voyagecam.app.feature.recording

import com.voyagecam.app.R
import com.voyagecam.app.data.settings.recordingModeLabelRes
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingNotificationStateTest {
    @Test
    fun modeLabelUsesAutoDualStringWhenDualCameraActive() {
        assertEquals(
            R.string.recording_mode_auto_dual_active,
            recordingModeLabelRes(recordingModeAuto = true, dualCameraActive = true),
        )
    }

    @Test
    fun modeLabelUsesAutoRearStringWhenAutoModeFallsBackToRear() {
        assertEquals(
            R.string.recording_mode_auto_rear_active,
            recordingModeLabelRes(recordingModeAuto = true, dualCameraActive = false),
        )
    }

    @Test
    fun modeLabelUsesRearOnlyStringWhenManualRearModeSelected() {
        assertEquals(
            R.string.recording_mode_rear_only,
            recordingModeLabelRes(recordingModeAuto = false, dualCameraActive = false),
        )
    }
}
