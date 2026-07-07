package com.voyagecam.app

import android.content.Context

class AutoStartDiagnosticsStore(context: Context) {
    private val prefs = context.getSharedPreferences("voyage_cam_auto_start_diagnostics", Context.MODE_PRIVATE)

    fun load(): AutoStartDiagnostic? {
        val source = prefs.getString(KEY_SOURCE, null)?.toAutoStartSource() ?: return null
        val result = prefs.getString(KEY_RESULT, null)?.toAutoStartResult() ?: return null
        return AutoStartDiagnostic(
            source = source,
            result = result,
            reason = prefs.getString(KEY_REASON, null).orEmpty(),
            detail = prefs.getString(KEY_DETAIL, null).orEmpty(),
            recordedAtMillis = prefs.getLong(KEY_RECORDED_AT, 0L).takeIf { it > 0L } ?: return null,
        )
    }

    fun record(
        source: AutoStartSource,
        result: AutoStartResult,
        reason: String,
        detail: String = "",
    ) {
        prefs.edit()
            .putString(KEY_SOURCE, source.name)
            .putString(KEY_RESULT, result.name)
            .putString(KEY_REASON, reason)
            .putString(KEY_DETAIL, detail)
            .putLong(KEY_RECORDED_AT, System.currentTimeMillis())
            .apply()
    }

    companion object {
        private const val KEY_SOURCE = "source"
        private const val KEY_RESULT = "result"
        private const val KEY_REASON = "reason"
        private const val KEY_DETAIL = "detail"
        private const val KEY_RECORDED_AT = "recorded_at"
    }
}

data class AutoStartDiagnostic(
    val source: AutoStartSource,
    val result: AutoStartResult,
    val reason: String,
    val detail: String,
    val recordedAtMillis: Long,
)

enum class AutoStartSource(val label: String) {
    Power("充电器"),
    Bluetooth("蓝牙"),
}

enum class AutoStartResult(val label: String) {
    Started("已启动"),
    Ignored("已忽略"),
}

private fun String.toAutoStartSource(): AutoStartSource? {
    return runCatching { AutoStartSource.valueOf(this) }.getOrNull()
}

private fun String.toAutoStartResult(): AutoStartResult? {
    return runCatching { AutoStartResult.valueOf(this) }.getOrNull()
}
