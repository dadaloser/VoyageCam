package com.voyagecam.app.core.model

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
