package com.voyagecam.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DualCameraDiagnosticTest {
    @Test
    fun summary_formatsStageAndDetail() {
        val diagnostic = DualCameraDiagnostic(
            stage = DualCameraDiagnosticStage.ConcurrentRecording,
            detail = "Front recorder init failed",
        )

        assertEquals("Concurrent recording: Front recorder init failed", diagnostic.summary())
    }
}
