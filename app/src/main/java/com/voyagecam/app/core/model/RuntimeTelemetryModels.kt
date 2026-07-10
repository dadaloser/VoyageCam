package com.voyagecam.app.core.model

enum class StructuredLogLevel {
    Debug,
    Info,
    Warn,
    Error,
    Fatal,
}

enum class DualCameraFailureSource {
    SessionCoordinator,
    RecordingService,
    PerformanceGuard,
}

data class PersistedStructuredLogEntry(
    val id: Long,
    val level: StructuredLogLevel,
    val category: String,
    val event: String,
    val message: String,
    val attributes: String,
    val throwable: String?,
    val recordedAtMillis: Long,
)

data class PersistedCrashReport(
    val id: Long,
    val threadName: String,
    val exceptionType: String,
    val message: String?,
    val stacktrace: String,
    val appVersion: String,
    val recordedAtMillis: Long,
)

data class PersistedDualCameraFailureArchive(
    val id: Long,
    val source: DualCameraFailureSource,
    val stage: DualCameraDiagnosticStage?,
    val summary: String,
    val detail: String,
    val attributes: String,
    val recordedAtMillis: Long,
)
