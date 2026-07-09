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

data class PlaybackSyncCorrection(
    val status: PlaybackSyncStatus,
    val shouldCorrect: Boolean,
    val targetSecondaryPositionMs: Long,
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

fun playbackSyncCorrection(
    primaryPositionMs: Long,
    secondaryPositionMs: Long,
    isPlaying: Boolean,
    driftThresholdMs: Long = DEFAULT_DRIFT_THRESHOLD_MS,
): PlaybackSyncCorrection {
    val status = playbackSyncStatus(
        primaryPositionMs = primaryPositionMs,
        secondaryPositionMs = secondaryPositionMs,
        driftThresholdMs = driftThresholdMs,
    )
    return PlaybackSyncCorrection(
        status = status,
        shouldCorrect = isPlaying && status.requiresCorrection,
        targetSecondaryPositionMs = primaryPositionMs,
    )
}

private const val DEFAULT_DRIFT_THRESHOLD_MS = 500L
