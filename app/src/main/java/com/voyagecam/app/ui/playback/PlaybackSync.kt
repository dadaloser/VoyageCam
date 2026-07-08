package com.voyagecam.app.ui.playback

import kotlin.math.abs

data class PlaybackSyncSnapshot(
    val primaryPositionMs: Int,
    val secondaryPositionMs: Int,
) {
    val offsetMs: Int
        get() = secondaryPositionMs - primaryPositionMs
}

data class PlaybackSyncStatus(
    val offsetMs: Int,
    val requiresCorrection: Boolean,
)

fun playbackSyncStatus(
    primaryPositionMs: Int,
    secondaryPositionMs: Int,
    driftThresholdMs: Int = DEFAULT_DRIFT_THRESHOLD_MS,
): PlaybackSyncStatus {
    val offsetMs = secondaryPositionMs - primaryPositionMs
    return PlaybackSyncStatus(
        offsetMs = offsetMs,
        requiresCorrection = abs(offsetMs) >= driftThresholdMs,
    )
}

private const val DEFAULT_DRIFT_THRESHOLD_MS = 500
