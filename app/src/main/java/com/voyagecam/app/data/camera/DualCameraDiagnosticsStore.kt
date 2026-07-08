package com.voyagecam.app.data.camera

import android.content.Context
import com.voyagecam.app.core.model.DualCameraDiagnostic
import com.voyagecam.app.core.model.DualCameraDiagnosticStage
import com.voyagecam.app.core.model.PersistedDualCameraDiagnostic

class DualCameraDiagnosticsStore(context: Context) {
    private val prefs = context.getSharedPreferences("voyage_cam_dual_camera_diagnostics", Context.MODE_PRIVATE)

    fun load(): PersistedDualCameraDiagnostic? {
        val stage = prefs.getString(KEY_STAGE, null)?.toDualCameraDiagnosticStage() ?: return null
        val detail = prefs.getString(KEY_DETAIL, null)?.takeIf { it.isNotBlank() } ?: return null
        val recordedAtMillis = prefs.getLong(KEY_RECORDED_AT, 0L).takeIf { it > 0L } ?: return null
        return PersistedDualCameraDiagnostic(
            diagnostic = DualCameraDiagnostic(
                stage = stage,
                detail = detail,
            ),
            recordedAtMillis = recordedAtMillis,
        )
    }

    fun record(diagnostic: DualCameraDiagnostic) {
        prefs.edit()
            .putString(KEY_STAGE, diagnostic.stage.name)
            .putString(KEY_DETAIL, diagnostic.detail)
            .putLong(KEY_RECORDED_AT, System.currentTimeMillis())
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_STAGE)
            .remove(KEY_DETAIL)
            .remove(KEY_RECORDED_AT)
            .apply()
    }

    companion object {
        private const val KEY_STAGE = "stage"
        private const val KEY_DETAIL = "detail"
        private const val KEY_RECORDED_AT = "recorded_at"
    }
}

internal fun String.toDualCameraDiagnosticStage(): DualCameraDiagnosticStage? {
    return runCatching { DualCameraDiagnosticStage.valueOf(this) }.getOrNull()
}
