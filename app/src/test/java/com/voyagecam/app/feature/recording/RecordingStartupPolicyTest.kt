package com.voyagecam.app.feature.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStartupPolicyTest {
    @Test
    fun ignoresStartWhileStartupCleanupIsStillRunning() {
        assertTrue(
            RecordingStartupPolicy.shouldIgnoreStart(
                startupInProgress = true,
                startedAtMillis = System.currentTimeMillis(),
                hasRecorder = false,
            ),
        )
    }

    @Test
    fun ignoresStartWhenRecorderIsAlreadyActive() {
        assertTrue(
            RecordingStartupPolicy.shouldIgnoreStart(
                startupInProgress = false,
                startedAtMillis = System.currentTimeMillis(),
                hasRecorder = true,
            ),
        )
    }

    @Test
    fun allowsRestartRecoveryWhenRecorderIsMissingAndStartupAlreadyFinished() {
        assertFalse(
            RecordingStartupPolicy.shouldIgnoreStart(
                startupInProgress = false,
                startedAtMillis = System.currentTimeMillis(),
                hasRecorder = false,
            ),
        )
    }
}
