package com.voyagecam.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersistedDualCameraSessionTelemetryTest {
    @Test
    fun keepsSummaryDetailAndDiagnostic() {
        val telemetry = PersistedDualCameraSessionTelemetry(
            summary = "Dual Session 2 · Concurrent preview attached",
            detail = "Rear preview connected · Front preview connected",
            diagnostic = null,
            recordedAtMillis = 1234L,
        )

        assertEquals("Dual Session 2 · Concurrent preview attached", telemetry.summary)
        assertEquals("Rear preview connected · Front preview connected", telemetry.detail)
        assertNull(telemetry.diagnostic)
        assertEquals(1234L, telemetry.recordedAtMillis)
    }
}
