package com.voyagecam.app.data.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeTelemetryStoreTest {
    @Test
    fun pendingCrashCodec_roundTripsPayload() {
        val payload = PendingCrashReportPayload(
            threadName = "main",
            exceptionType = "java.lang.IllegalStateException",
            message = "Camera bind failed",
            stacktrace = "line1\nline2",
            appVersion = "0.1.0 (1)",
            recordedAtMillis = 123456789L,
        )

        val decoded = PendingCrashReportCodec.decode(PendingCrashReportCodec.encode(payload))

        assertEquals(payload, decoded)
    }

    @Test
    fun flattenAttributes_sortsKeysForStableOutput() {
        val formatted = flattenAttributes(
            mapOf(
                "segmentIndex" to "3",
                "dualCamera" to "true",
            ),
        )

        assertEquals("dualCamera=true | segmentIndex=3", formatted)
        assertTrue(flattenAttributes(emptyMap()).isEmpty())
    }
}
