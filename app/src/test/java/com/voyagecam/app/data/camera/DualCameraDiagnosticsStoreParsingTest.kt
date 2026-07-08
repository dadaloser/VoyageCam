package com.voyagecam.app.data.camera

import com.voyagecam.app.core.model.DualCameraDiagnosticStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DualCameraDiagnosticsStoreParsingTest {
    @Test
    fun toDualCameraDiagnosticStage_returnsStageForKnownValue() {
        assertEquals(
            DualCameraDiagnosticStage.ConcurrentRecording,
            "ConcurrentRecording".toDualCameraDiagnosticStage(),
        )
    }

    @Test
    fun toDualCameraDiagnosticStage_returnsNullForUnknownValue() {
        assertNull("not-a-stage".toDualCameraDiagnosticStage())
    }
}
