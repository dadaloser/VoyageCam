package com.voyagecam.app.data.camera

import android.content.Context
import com.voyagecam.app.core.model.PersistedDualCameraSessionTelemetry
import com.voyagecam.app.ui.preview.DualCameraTelemetryPresentation

class DualCameraSessionTelemetryStore(context: Context) {
    private val prefs = context.getSharedPreferences("voyage_cam_dual_camera_session_telemetry", Context.MODE_PRIVATE)

    fun load(): PersistedDualCameraSessionTelemetry? {
        val summary = prefs.getString(KEY_SUMMARY, null)?.takeIf { it.isNotBlank() } ?: return null
        val detail = prefs.getString(KEY_DETAIL, null)?.takeIf { it.isNotBlank() } ?: return null
        val recordedAtMillis = prefs.getLong(KEY_RECORDED_AT, 0L).takeIf { it > 0L } ?: return null
        return PersistedDualCameraSessionTelemetry(
            summary = summary,
            detail = detail,
            diagnostic = prefs.getString(KEY_DIAGNOSTIC, null),
            recordedAtMillis = recordedAtMillis,
        )
    }

    fun record(telemetry: DualCameraTelemetryPresentation) {
        prefs.edit()
            .putString(KEY_SUMMARY, telemetry.summary)
            .putString(KEY_DETAIL, telemetry.detail)
            .putString(KEY_DIAGNOSTIC, telemetry.diagnostic)
            .putLong(KEY_RECORDED_AT, System.currentTimeMillis())
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_SUMMARY)
            .remove(KEY_DETAIL)
            .remove(KEY_DIAGNOSTIC)
            .remove(KEY_RECORDED_AT)
            .apply()
    }

    companion object {
        private const val KEY_SUMMARY = "summary"
        private const val KEY_DETAIL = "detail"
        private const val KEY_DIAGNOSTIC = "diagnostic"
        private const val KEY_RECORDED_AT = "recorded_at"
    }
}
