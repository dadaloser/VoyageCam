package com.voyagecam.app.core.model

data class PersistedDualCameraDiagnostic(
    val diagnostic: DualCameraDiagnostic,
    val recordedAtMillis: Long,
) {
    val stage: DualCameraDiagnosticStage
        get() = diagnostic.stage

    val detail: String
        get() = diagnostic.detail

    fun summary(): String = diagnostic.summary()
}
