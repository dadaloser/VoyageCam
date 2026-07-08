package com.voyagecam.app.core.model

data class DualCameraDiagnostic(
    val stage: DualCameraDiagnosticStage,
    val detail: String,
) {
    fun summary(): String = "${stage.label}：$detail"
}

enum class DualCameraDiagnosticStage(val label: String) {
    Preview("双摄预览"),
    Session("双摄会话"),
    RearRecording("后摄录制"),
    FrontRecording("前摄录制"),
    ConcurrentRecording("双摄并发录制"),
}
