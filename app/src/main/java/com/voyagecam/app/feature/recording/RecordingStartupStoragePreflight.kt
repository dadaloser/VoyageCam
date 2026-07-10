package com.voyagecam.app.feature.recording

import com.voyagecam.app.data.settings.ResolvedRecordingConfig
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.data.settings.estimatedManagedBytesPerMinute

data class RecordingStartupSpaceCheck(
    val availableBytes: Long,
    val segmentBytes: Long,
    val safetyMarginBytes: Long,
) {
    val requiredBytes: Long
        get() = segmentBytes + safetyMarginBytes

    val hasEnoughSpace: Boolean
        get() = availableBytes >= requiredBytes
}

internal object RecordingStartupStoragePreflight {
    fun check(
        availableBytes: Long,
        settings: VoyageCamSettings,
        resolvedConfig: ResolvedRecordingConfig,
    ): RecordingStartupSpaceCheck {
        val segmentBytes = settings.estimatedManagedBytesPerMinute(resolvedConfig) *
            settings.segmentDurationMinutes.coerceAtLeast(1).toLong()
        // Reserve extra headroom so the first segment can start cleanly and finalize safely.
        val safetyMarginBytes = maxOf(MIN_SAFETY_MARGIN_BYTES, segmentBytes / SAFETY_MARGIN_DIVISOR)
        return RecordingStartupSpaceCheck(
            availableBytes = availableBytes.coerceAtLeast(0L),
            segmentBytes = segmentBytes,
            safetyMarginBytes = safetyMarginBytes,
        )
    }

    private const val MIN_SAFETY_MARGIN_BYTES = 64L * 1024L * 1024L
    private const val SAFETY_MARGIN_DIVISOR = 5L
}
