package com.voyagecam.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersistedDualCameraSessionTelemetryTest {
    @Test
    fun keepsSummaryDetailAndDiagnostic() {
        val telemetry = PersistedDualCameraSessionTelemetry(
            summary = "双摄 Session 2 · 并发预览已绑定",
            detail = "后摄预览已连接 · 前摄预览已连接",
            diagnostic = null,
            recordedAtMillis = 1234L,
        )

        assertEquals("双摄 Session 2 · 并发预览已绑定", telemetry.summary)
        assertEquals("后摄预览已连接 · 前摄预览已连接", telemetry.detail)
        assertNull(telemetry.diagnostic)
        assertEquals(1234L, telemetry.recordedAtMillis)
    }
}
