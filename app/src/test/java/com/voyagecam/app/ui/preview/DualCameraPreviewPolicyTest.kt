package com.voyagecam.app.ui.preview

import com.voyagecam.app.core.model.DeviceCapabilityGrade
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.DualCameraSwitchState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualCameraPreviewPolicyTest {
    @Test
    fun keepsFrontInsetVisibleWhileRecordingWhenDualCameraIsAvailable() {
        val capability = DualCameraCapability(
            state = DualCameraSwitchState.AvailableOn,
            grade = DeviceCapabilityGrade.A,
            reason = "supported",
        )

        assertTrue(
            shouldShowFrontInsetPreview(
                dualCameraEnabled = true,
                capability = capability,
                isRecording = true,
            ),
        )
    }

    @Test
    fun hidesFrontInsetWhenDualCameraSwitchIsOff() {
        val capability = DualCameraCapability(
            state = DualCameraSwitchState.AvailableOff,
            grade = DeviceCapabilityGrade.B,
            reason = "supported",
        )

        assertFalse(
            shouldShowFrontInsetPreview(
                dualCameraEnabled = false,
                capability = capability,
                isRecording = false,
            ),
        )
    }

    @Test
    fun hidesFrontInsetWhenDualCameraIsUnavailable() {
        val capability = DualCameraCapability(
            state = DualCameraSwitchState.Unavailable,
            grade = DeviceCapabilityGrade.D,
            reason = "unsupported",
        )

        assertFalse(
            shouldShowFrontInsetPreview(
                dualCameraEnabled = true,
                capability = capability,
                isRecording = true,
            ),
        )
    }
}
