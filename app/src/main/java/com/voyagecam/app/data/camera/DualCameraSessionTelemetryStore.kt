package com.voyagecam.app.data.camera

import android.content.Context
import com.voyagecam.app.core.model.PersistedDualCameraSessionTelemetry
import com.voyagecam.app.ui.preview.DualCameraTelemetryPresentation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DualCameraSessionTelemetryStore(context: Context) {
    private val appContext = context.applicationContext
    private val database = DualCameraPersistenceDatabase.from(appContext)
    private val dao = database.dualCameraSessionTelemetryDao()
    private val legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    private val legacyImportMutex = Mutex()
    @Volatile
    private var legacyImportChecked = false

    suspend fun load(): PersistedDualCameraSessionTelemetry? {
        ensureLegacyImported()
        return dao.load()?.toPersistedModel()
    }

    suspend fun record(telemetry: DualCameraTelemetryPresentation) {
        ensureLegacyImported()
        dao.upsert(
            DualCameraSessionTelemetryEntity(
                id = SINGLETON_RECORD_ID,
                summary = telemetry.summary,
                detail = telemetry.detail,
                diagnostic = telemetry.diagnostic,
                recordedAtMillis = System.currentTimeMillis(),
            ),
        )
        clearLegacyPrefs()
    }

    suspend fun clear() {
        ensureLegacyImported()
        dao.clear()
        clearLegacyPrefs()
    }

    private suspend fun ensureLegacyImported() {
        if (legacyImportChecked) return

        legacyImportMutex.withLock {
            if (legacyImportChecked) return

            if (dao.count() == 0) {
                legacyPrefs.toPersistedDualCameraSessionTelemetryOrNull()?.let { telemetry ->
                    dao.upsert(
                        DualCameraSessionTelemetryEntity(
                            id = SINGLETON_RECORD_ID,
                            summary = telemetry.summary,
                            detail = telemetry.detail,
                            diagnostic = telemetry.diagnostic,
                            recordedAtMillis = telemetry.recordedAtMillis,
                        ),
                    )
                }
            }
            clearLegacyPrefs()
            legacyImportChecked = true
        }
    }

    private fun clearLegacyPrefs() {
        legacyPrefs.edit().clear().apply()
    }

    private companion object {
        private const val LEGACY_PREFS_NAME = "voyage_cam_dual_camera_session_telemetry"
        private const val LEGACY_KEY_SUMMARY = "summary"
        private const val LEGACY_KEY_DETAIL = "detail"
        private const val LEGACY_KEY_DIAGNOSTIC = "diagnostic"
        private const val LEGACY_KEY_RECORDED_AT = "recorded_at"
        private const val SINGLETON_RECORD_ID = 1
    }

    private fun DualCameraSessionTelemetryEntity.toPersistedModel(): PersistedDualCameraSessionTelemetry? {
        val nonBlankSummary = summary.takeIf { it.isNotBlank() } ?: return null
        val nonBlankDetail = detail.takeIf { it.isNotBlank() } ?: return null
        val persistedAt = recordedAtMillis.takeIf { it > 0L } ?: return null
        return PersistedDualCameraSessionTelemetry(
            summary = nonBlankSummary,
            detail = nonBlankDetail,
            diagnostic = diagnostic,
            recordedAtMillis = persistedAt,
        )
    }

    private fun android.content.SharedPreferences.toPersistedDualCameraSessionTelemetryOrNull(): PersistedDualCameraSessionTelemetry? {
        val nonBlankSummary = getString(LEGACY_KEY_SUMMARY, null)?.takeIf { it.isNotBlank() } ?: return null
        val nonBlankDetail = getString(LEGACY_KEY_DETAIL, null)?.takeIf { it.isNotBlank() } ?: return null
        val persistedAt = getLong(LEGACY_KEY_RECORDED_AT, 0L).takeIf { it > 0L } ?: return null
        return PersistedDualCameraSessionTelemetry(
            summary = nonBlankSummary,
            detail = nonBlankDetail,
            diagnostic = getString(LEGACY_KEY_DIAGNOSTIC, null),
            recordedAtMillis = persistedAt,
        )
    }
}
