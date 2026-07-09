package com.voyagecam.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PersistedDualCameraDiagnosticTest {
    @Test
    fun summary_delegatesToDiagnostic() {
        val record = PersistedDualCameraDiagnostic(
            diagnostic = DualCameraDiagnostic(
                stage = DualCameraDiagnosticStage.FrontRecording,
                detail = "Front encoder init failed",
            ),
            recordedAtMillis = 1234L,
        )

        assertEquals("Front recording: Front encoder init failed", record.summary())
        assertEquals(DualCameraDiagnosticStage.FrontRecording, record.stage)
        assertEquals("Front encoder init failed", record.detail)
    }
}
