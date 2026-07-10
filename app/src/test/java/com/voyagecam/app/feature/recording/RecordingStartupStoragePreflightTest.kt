package com.voyagecam.app.feature.recording

import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.data.settings.RecordingBitratePreset
import com.voyagecam.app.data.settings.RecordingFrameRatePreset
import com.voyagecam.app.data.settings.RecordingMode
import com.voyagecam.app.data.settings.RecordingOrientationStrategy
import com.voyagecam.app.data.settings.RecordingResolutionPreset
import com.voyagecam.app.data.settings.ResolvedRecordingConfig
import com.voyagecam.app.data.settings.VoyageCamSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStartupStoragePreflightTest {
    @Test
    fun calculatesSingleCameraRequirementWithMinimumSafetyMargin() {
        val settings = VoyageCamSettings(
            recordingMode = RecordingMode.RearOnly,
            segmentDurationMinutes = 1,
            recordingResolution = RecordingResolutionPreset.FHD_1080P,
            recordingFrameRate = RecordingFrameRatePreset.FPS_30,
            recordingBitrate = RecordingBitratePreset.MBPS_8,
        )
        val config = ResolvedRecordingConfig(
            requestedMode = RecordingMode.RearOnly,
            primaryCameraDirection = CameraDirection.Rear,
            dualCameraActive = false,
            frontCameraActive = false,
            ambientAudioActive = false,
            frontCameraMirrorActive = false,
            orientationStrategy = RecordingOrientationStrategy.FollowSystem,
        )

        val result = RecordingStartupStoragePreflight.check(
            availableBytes = 200L * 1024L * 1024L,
            settings = settings,
            resolvedConfig = config,
        )

        assertEquals(60_000_000L, result.segmentBytes)
        assertEquals(64L * 1024L * 1024L, result.safetyMarginBytes)
        assertTrue(result.hasEnoughSpace)
    }

    @Test
    fun doublesSegmentEstimateWhenDualCameraIsActive() {
        val settings = VoyageCamSettings(
            recordingMode = RecordingMode.Auto,
            segmentDurationMinutes = 3,
            recordingResolution = RecordingResolutionPreset.FHD_1080P,
            recordingFrameRate = RecordingFrameRatePreset.FPS_30,
            recordingBitrate = RecordingBitratePreset.MBPS_12,
        )
        val config = ResolvedRecordingConfig(
            requestedMode = RecordingMode.Auto,
            primaryCameraDirection = CameraDirection.Rear,
            dualCameraActive = true,
            frontCameraActive = true,
            ambientAudioActive = true,
            frontCameraMirrorActive = false,
            orientationStrategy = RecordingOrientationStrategy.FollowSystem,
        )

        val result = RecordingStartupStoragePreflight.check(
            availableBytes = Long.MAX_VALUE,
            settings = settings,
            resolvedConfig = config,
        )

        assertEquals((12_000_000L * 60L / 8L) * 2L * 3L + 3L * 1024L * 1024L, result.segmentBytes)
        assertEquals(result.segmentBytes / 5L, result.safetyMarginBytes)
    }

    @Test
    fun blocksStartupWhenAvailableSpaceIsBelowRequiredBytes() {
        val settings = VoyageCamSettings(
            segmentDurationMinutes = 5,
            recordingBitrate = RecordingBitratePreset.MBPS_24,
        )
        val config = ResolvedRecordingConfig(
            requestedMode = RecordingMode.RearOnly,
            primaryCameraDirection = CameraDirection.Rear,
            dualCameraActive = false,
            frontCameraActive = false,
            ambientAudioActive = false,
            frontCameraMirrorActive = false,
            orientationStrategy = RecordingOrientationStrategy.FollowSystem,
        )

        val result = RecordingStartupStoragePreflight.check(
            availableBytes = 400L * 1024L * 1024L,
            settings = settings,
            resolvedConfig = config,
        )

        assertFalse(result.hasEnoughSpace)
        assertTrue(result.requiredBytes > result.availableBytes)
    }
}
