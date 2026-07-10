package com.voyagecam.app.feature.recording

import com.voyagecam.app.R
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.data.settings.RecordingMode
import com.voyagecam.app.data.settings.recordingModeLabelRes
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingNotificationStateTest {
    @Test
    fun modeLabelUsesAutoDualStringWhenDualCameraActive() {
        assertEquals(
            R.string.recording_mode_auto_dual_active,
            recordingModeLabelRes(requestedMode = RecordingMode.Auto, dualCameraActive = true),
        )
    }

    @Test
    fun modeLabelUsesAutoRearStringWhenAutoModeFallsBackToRear() {
        assertEquals(
            R.string.recording_mode_auto_rear_active,
            recordingModeLabelRes(requestedMode = RecordingMode.Auto, dualCameraActive = false),
        )
    }

    @Test
    fun modeLabelUsesRearOnlyStringWhenManualRearModeSelected() {
        assertEquals(
            R.string.recording_mode_rear_only,
            recordingModeLabelRes(requestedMode = RecordingMode.RearOnly, dualCameraActive = false),
        )
    }

    @Test
    fun modeLabelUsesFrontOnlyStringWhenFrontModeSelected() {
        assertEquals(
            R.string.recording_mode_front_only,
            recordingModeLabelRes(
                requestedMode = RecordingMode.FrontOnly,
                dualCameraActive = false,
                primaryCameraDirection = CameraDirection.Front,
            ),
        )
    }
}
