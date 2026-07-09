package com.voyagecam.app.data.camera

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.voyagecam.app.core.model.DualCameraDiagnosticStage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DualCameraPersistenceMigrationTest {
    @Test
    fun importsLegacyDualCameraDiagnosticPrefsIntoRoom() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DualCameraDiagnosticsStore(context)
        val prefs = context.getSharedPreferences(DIAGNOSTIC_PREFS_NAME, Context.MODE_PRIVATE)

        runBlocking {
            store.clear()
        }
        prefs.edit()
            .putString("stage", DualCameraDiagnosticStage.Session.name)
            .putString("detail", "legacy bind failed")
            .putLong("recorded_at", 1_720_000_000_000L)
            .apply()

        val persisted = runBlocking {
            DualCameraDiagnosticsStore(context).load()
        }

        assertEquals(DualCameraDiagnosticStage.Session, persisted?.diagnostic?.stage)
        assertEquals("legacy bind failed", persisted?.diagnostic?.detail)
        assertEquals(1_720_000_000_000L, persisted?.recordedAtMillis)
        assertFalse(prefs.contains("stage"))
        assertFalse(prefs.contains("detail"))
        assertFalse(prefs.contains("recorded_at"))
    }

    @Test
    fun importsLegacyDualCameraTelemetryPrefsIntoRoom() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DualCameraSessionTelemetryStore(context)
        val prefs = context.getSharedPreferences(TELEMETRY_PREFS_NAME, Context.MODE_PRIVATE)

        runBlocking {
            store.clear()
        }
        prefs.edit()
            .putString("summary", "legacy summary")
            .putString("detail", "legacy detail")
            .putString("diagnostic", "legacy diagnostic")
            .putLong("recorded_at", 1_730_000_000_000L)
            .apply()

        val persisted = runBlocking {
            DualCameraSessionTelemetryStore(context).load()
        }

        assertEquals("legacy summary", persisted?.summary)
        assertEquals("legacy detail", persisted?.detail)
        assertEquals("legacy diagnostic", persisted?.diagnostic)
        assertEquals(1_730_000_000_000L, persisted?.recordedAtMillis)
        assertFalse(prefs.contains("summary"))
        assertFalse(prefs.contains("detail"))
        assertFalse(prefs.contains("diagnostic"))
        assertFalse(prefs.contains("recorded_at"))
    }

    @Test
    fun ignoresIncompleteLegacyDiagnosticPrefs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DualCameraDiagnosticsStore(context)
        val prefs = context.getSharedPreferences(DIAGNOSTIC_PREFS_NAME, Context.MODE_PRIVATE)

        runBlocking {
            store.clear()
        }
        prefs.edit()
            .putString("stage", DualCameraDiagnosticStage.Session.name)
            .putLong("recorded_at", 1_720_000_000_000L)
            .apply()

        val persisted = runBlocking {
            DualCameraDiagnosticsStore(context).load()
        }

        assertNull(persisted)
        assertFalse(prefs.contains("stage"))
        assertFalse(prefs.contains("recorded_at"))
    }

    private companion object {
        private const val DIAGNOSTIC_PREFS_NAME = "voyage_cam_dual_camera_diagnostics"
        private const val TELEMETRY_PREFS_NAME = "voyage_cam_dual_camera_session_telemetry"
    }
}
