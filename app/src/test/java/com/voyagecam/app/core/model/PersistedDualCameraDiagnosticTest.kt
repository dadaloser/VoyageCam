package com.voyagecam.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PersistedDualCameraDiagnosticTest {
    @Test
    fun summary_delegatesToDiagnostic() {
        val record = PersistedDualCameraDiagnostic(
            diagnostic = DualCameraDiagnostic(
                stage = DualCameraDiagnosticStage.FrontRecording,
                detail = "前摄编码器初始化失败",
            ),
            recordedAtMillis = 1234L,
        )

        assertEquals("前摄录制：前摄编码器初始化失败", record.summary())
        assertEquals(DualCameraDiagnosticStage.FrontRecording, record.stage)
        assertEquals("前摄编码器初始化失败", record.detail)
    }
}
