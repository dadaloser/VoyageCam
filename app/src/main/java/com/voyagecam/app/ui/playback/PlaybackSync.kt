package com.voyagecam.app.ui.playback

import kotlin.math.abs

data class PlaybackSyncSnapshot(
    val primaryPositionMs: Long,
    val secondaryPositionMs: Long,
) {
    val offsetMs: Long
        get() = secondaryPositionMs - primaryPositionMs
}

data class PlaybackSyncStatus(
    val offsetMs: Long,
    val requiresCorrection: Boolean,
)

fun playbackSyncStatus(
    primaryPositionMs: Long,
    secondaryPositionMs: Long,
    driftThresholdMs: Long = DEFAULT_DRIFT_THRESHOLD_MS,
): PlaybackSyncStatus {
    val offsetMs = secondaryPositionMs - primaryPositionMs
    return PlaybackSyncStatus(
        offsetMs = offsetMs,
        requiresCorrection = abs(offsetMs) >= driftThresholdMs,
    )
}

private const val DEFAULT_DRIFT_THRESHOLD_MS = 500L
