package com.voyagecam.app.core.model

data class PersistedDualCameraSessionTelemetry(
    val summary: String,
    val detail: String,
    val diagnostic: String? = null,
    val recordedAtMillis: Long,
)
