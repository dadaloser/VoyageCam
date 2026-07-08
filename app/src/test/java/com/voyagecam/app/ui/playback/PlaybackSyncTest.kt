package com.voyagecam.app.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSyncTest {
    @Test
    fun reportsOffsetWithoutCorrectionBelowThreshold() {
        val status = playbackSyncStatus(
            primaryPositionMs = 10_000,
            secondaryPositionMs = 10_260,
            driftThresholdMs = 500,
        )

        assertEquals(260, status.offsetMs)
        assertFalse(status.requiresCorrection)
    }

    @Test
    fun requestsCorrectionAboveThreshold() {
        val status = playbackSyncStatus(
            primaryPositionMs = 20_000,
            secondaryPositionMs = 19_200,
            driftThresholdMs = 500,
        )

        assertEquals(-800, status.offsetMs)
        assertTrue(status.requiresCorrection)
    }
}
