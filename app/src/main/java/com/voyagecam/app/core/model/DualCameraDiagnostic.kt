package com.voyagecam.app.core.model

import java.util.Locale

data class DualCameraDiagnostic(
    val stage: DualCameraDiagnosticStage,
    val detail: String,
) {
    fun summary(): String = "${stage.summaryLabel()}: $detail"
}

enum class DualCameraDiagnosticStage {
    Preview,
    Session,
    RearRecording,
    FrontRecording,
    ConcurrentRecording,
}

private fun DualCameraDiagnosticStage.summaryLabel(): String {
    return name
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .lowercase(Locale.US)
        .replaceFirstChar { character -> character.titlecase(Locale.US) }
}
