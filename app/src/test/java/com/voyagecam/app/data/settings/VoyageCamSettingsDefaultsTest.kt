package com.voyagecam.app.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class VoyageCamSettingsDefaultsTest {
    @Test
    fun defaults_keep_sensitive_metadata_disabled() {
        val settings = VoyageCamSettings()

        assertFalse(settings.gpsMetadataEnabled)
        assertFalse(settings.exportWatermarkSubtitlesEnabled)
        assertFalse(settings.exportBurnedWatermarkVideoEnabled)
        assertEquals(RecordingResolutionPreset.FHD_1080P, settings.recordingResolution)
        assertEquals(RecordingFrameRatePreset.FPS_30, settings.recordingFrameRate)
        assertEquals(RecordingBitratePreset.MBPS_12, settings.recordingBitrate)
        assertEquals(RecordingMode.RearOnly, settings.recordingMode)
        assertEquals(false, settings.frontCameraMirrorEnabled)
        assertEquals(RecordingOrientationStrategy.FollowSystem, settings.recordingOrientationStrategy)
    }
}
