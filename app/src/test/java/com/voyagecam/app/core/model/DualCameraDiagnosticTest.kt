package com.voyagecam.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DualCameraDiagnosticTest {
    @Test
    fun summary_formatsStageAndDetail() {
        val diagnostic = DualCameraDiagnostic(
            stage = DualCameraDiagnosticStage.ConcurrentRecording,
            detail = "前摄录制初始化失败",
        )

        assertEquals("双摄并发录制：前摄录制初始化失败", diagnostic.summary())
    }
}
