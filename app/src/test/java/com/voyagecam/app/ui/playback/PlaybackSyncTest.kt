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

    @Test
    fun autoCorrectionTargetsPrimaryPositionWhilePlaying() {
        val correction = playbackSyncCorrection(
            primaryPositionMs = 42_000,
            secondaryPositionMs = 42_900,
            isPlaying = true,
            driftThresholdMs = 500,
        )

        assertEquals(900, correction.status.offsetMs)
        assertTrue(correction.status.requiresCorrection)
        assertTrue(correction.shouldCorrect)
        assertEquals(42_000, correction.targetSecondaryPositionMs)
    }

    @Test
    fun autoCorrectionDoesNotSeekWhilePaused() {
        val correction = playbackSyncCorrection(
            primaryPositionMs = 42_000,
            secondaryPositionMs = 42_900,
            isPlaying = false,
            driftThresholdMs = 500,
        )

        assertEquals(900, correction.status.offsetMs)
        assertTrue(correction.status.requiresCorrection)
        assertFalse(correction.shouldCorrect)
        assertEquals(42_000, correction.targetSecondaryPositionMs)
    }
}
