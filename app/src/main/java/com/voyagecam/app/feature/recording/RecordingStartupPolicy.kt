package com.voyagecam.app.feature.recording

internal object RecordingStartupPolicy {
    fun shouldIgnoreStart(
        startupInProgress: Boolean,
        startedAtMillis: Long,
        hasRecorder: Boolean,
    ): Boolean {
        return startupInProgress || (startedAtMillis > 0L && hasRecorder)
    }
}
