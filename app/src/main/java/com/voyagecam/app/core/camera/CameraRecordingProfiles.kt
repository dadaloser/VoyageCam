package com.voyagecam.app.core.camera

import android.util.Range
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import com.voyagecam.app.data.settings.RecordingResolutionPreset
import com.voyagecam.app.data.settings.RecordingVideoProfile

internal fun RecordingVideoProfile.qualitySelector(): QualitySelector {
    return QualitySelector.fromOrderedList(
        resolution.orderedQualities(),
        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
    )
}

internal fun RecordingVideoProfile.targetFrameRateRange(): Range<Int> {
    return Range(frameRate.fps, frameRate.fps)
}

private fun RecordingResolutionPreset.orderedQualities(): List<Quality> {
    return when (this) {
        RecordingResolutionPreset.UHD_2160P -> listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
        RecordingResolutionPreset.FHD_1080P -> listOf(Quality.FHD, Quality.HD, Quality.SD)
        RecordingResolutionPreset.HD_720P -> listOf(Quality.HD, Quality.SD)
    }
}
