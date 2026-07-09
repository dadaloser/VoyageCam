package com.voyagecam.app.core.model

data class AutoStartDiagnostic(
    val source: AutoStartSource,
    val result: AutoStartResult,
    val reason: String,
    val detail: String,
    val recordedAtMillis: Long,
)

enum class AutoStartSource {
    Power,
    Bluetooth,
}

enum class AutoStartResult {
    Started,
    Ignored,
}
