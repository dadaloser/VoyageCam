package com.voyagecam.app.data.settings

import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.DualCameraSwitchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoyageCamSettingsResolveRecordingConfigTest {
    @Test
    fun frontOnlyModeDisablesAmbientAudioButKeepsMirrorWhenFrontCameraExists() {
        val capability = DualCameraCapability(
            state = DualCameraSwitchState.AvailableOn,
            rearCameraId = "rear",
            frontCameraId = "front",
            reason = "supported",
        )

        val resolved = VoyageCamSettings(
            recordingMode = RecordingMode.FrontOnly,
            ambientAudioEnabled = true,
            frontCameraMirrorEnabled = true,
        ).resolveRecordingConfig(capability)

        assertTrue(resolved.frontCameraActive)
        assertFalse(resolved.dualCameraActive)
        assertFalse(resolved.ambientAudioActive)
        assertTrue(resolved.frontCameraMirrorActive)
        assertEquals(null, resolved.downgradeReason)
    }

    @Test
    fun frontOnlyModeFallsBackToRearWhenFrontCameraIsUnavailable() {
        val capability = DualCameraCapability(
            state = DualCameraSwitchState.Unavailable,
            rearCameraId = "rear",
            frontCameraId = null,
            reason = "rear only",
        )

        val resolved = VoyageCamSettings(
            recordingMode = RecordingMode.FrontOnly,
            ambientAudioEnabled = true,
            frontCameraMirrorEnabled = true,
        ).resolveRecordingConfig(capability)

        assertFalse(resolved.frontCameraActive)
        assertFalse(resolved.dualCameraActive)
        assertTrue(resolved.ambientAudioActive)
        assertFalse(resolved.frontCameraMirrorActive)
        assertEquals(RecordingConfigDowngradeReason.FrontCameraUnavailable, resolved.downgradeReason)
    }

    @Test
    fun autoModeFallsBackToRearWhenProfileExceedsSafeDualCameraTier() {
        val capability = DualCameraCapability(
            state = DualCameraSwitchState.AvailableOn,
            rearCameraId = "rear",
            frontCameraId = "front",
            reason = "supported",
        )

        val resolved = VoyageCamSettings(
            recordingMode = RecordingMode.Auto,
            recordingResolution = RecordingResolutionPreset.UHD_2160P,
            recordingFrameRate = RecordingFrameRatePreset.FPS_60,
            recordingBitrate = RecordingBitratePreset.MBPS_24,
            ambientAudioEnabled = true,
        ).resolveRecordingConfig(capability)

        assertFalse(resolved.dualCameraActive)
        assertFalse(resolved.frontCameraActive)
        assertTrue(resolved.ambientAudioActive)
        assertEquals(RecordingConfigDowngradeReason.DualCameraProfileUnsupported, resolved.downgradeReason)
    }

    @Test
    fun autoModeFallsBackToRearWhenDeviceDoesNotSupportDualCameraConcurrency() {
        val capability = DualCameraCapability(
            state = DualCameraSwitchState.Unavailable,
            rearCameraId = "rear",
            frontCameraId = "front",
            reason = "unsupported",
        )

        val resolved = VoyageCamSettings(
            recordingMode = RecordingMode.Auto,
            ambientAudioEnabled = true,
        ).resolveRecordingConfig(capability)

        assertFalse(resolved.dualCameraActive)
        assertFalse(resolved.frontCameraActive)
        assertTrue(resolved.ambientAudioActive)
        assertEquals(RecordingConfigDowngradeReason.DualCameraUnavailable, resolved.downgradeReason)
    }
}
