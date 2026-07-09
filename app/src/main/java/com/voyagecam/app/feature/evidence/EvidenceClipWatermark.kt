package com.voyagecam.app.feature.evidence

import android.content.Context
import com.voyagecam.app.R
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.GpsTrackPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class EvidenceClipWatermark(
    val clipStartMillis: Long,
    val clipEndMillis: Long,
    val samples: List<EvidenceWatermarkSample>,
    val timeTemplate: String = "Time %1\$s",
    val speedTemplate: String = "Speed %.0fkm/h",
    val bearingTemplate: String = "Heading %.0f°",
    val locationTemplate: String = "Location %.5f, %.5f",
) {
    fun linesAt(presentationTimeUs: Long): List<String> {
        val absoluteMillis = (clipStartMillis + presentationTimeUs / 1_000L)
            .coerceIn(clipStartMillis, clipEndMillis)
        val sample = samples.lastOrNull { it.capturedAtMillis <= absoluteMillis }

        return buildList {
            add(String.format(Locale.getDefault(), timeTemplate, absoluteMillis.asWatermarkTime()))
            sample?.speedMetersPerSecond?.let {
                add(String.format(Locale.getDefault(), speedTemplate, it * METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR))
            }
            sample?.bearingDegrees?.let {
                add(String.format(Locale.getDefault(), bearingTemplate, it))
            }
            if (sample?.latitude != null && sample.longitude != null) {
                add(
                    String.format(
                        Locale.getDefault(),
                        locationTemplate,
                        sample.latitude,
                        sample.longitude,
                    ),
                )
            }
        }
    }
}

internal data class EvidenceWatermarkSample(
    val capturedAtMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
)

internal fun EmergencyEvent.buildClipWatermark(
    context: Context,
    fileNameWithoutExtension: String,
    segmentDurationMinutes: Int,
): EvidenceClipWatermark? {
    return buildClipWatermark(fileNameWithoutExtension, segmentDurationMinutes)?.copy(
        timeTemplate = context.getString(R.string.evidence_clip_time),
        speedTemplate = context.getString(R.string.evidence_clip_speed),
        bearingTemplate = context.getString(R.string.evidence_clip_bearing),
        locationTemplate = context.getString(R.string.evidence_clip_location),
    )
}

internal fun EmergencyEvent.buildClipWatermark(
    fileNameWithoutExtension: String,
    segmentDurationMinutes: Int,
): EvidenceClipWatermark? {
    val clipStartMillis = fileNameWithoutExtension.clipStartedAtMillis() ?: return null
    val clipEndMillis = clipStartMillis + segmentDurationMinutes
        .coerceAtLeast(1)
        .toLong() * 60_000L + CLIP_WATERMARK_TOLERANCE_MS
    val orderedTrack = gpsTrackPoints.sortedBy { it.capturedAtMillis }
    val samples = buildList {
        orderedTrack
            .lastOrNull { it.capturedAtMillis < clipStartMillis }
            ?.toWatermarkSample()
            ?.let { add(it.copy(capturedAtMillis = clipStartMillis)) }
        addAll(
            orderedTrack
                .filter { point -> point.capturedAtMillis in clipStartMillis..clipEndMillis }
                .map(GpsTrackPoint::toWatermarkSample),
        )
        if (isEmpty()) {
            eventLocationSample()
                ?.takeIf { it.capturedAtMillis in clipStartMillis..clipEndMillis }
                ?.let(::add)
        }
    }

    return EvidenceClipWatermark(
        clipStartMillis = clipStartMillis,
        clipEndMillis = clipEndMillis,
        samples = samples.sortedBy { it.capturedAtMillis },
    )
}

private fun GpsTrackPoint.toWatermarkSample(): EvidenceWatermarkSample {
    return EvidenceWatermarkSample(
        capturedAtMillis = capturedAtMillis,
        latitude = latitude,
        longitude = longitude,
        speedMetersPerSecond = speedMetersPerSecond,
        bearingDegrees = bearingDegrees,
    )
}

private fun EmergencyEvent.eventLocationSample(): EvidenceWatermarkSample? {
    return EvidenceWatermarkSample(
        capturedAtMillis = locationCapturedAtMillis ?: triggeredAtMillis,
        latitude = latitude,
        longitude = longitude,
        speedMetersPerSecond = speedMetersPerSecond,
        bearingDegrees = bearingDegrees,
    ).takeIf {
        it.latitude != null ||
            it.longitude != null ||
            it.speedMetersPerSecond != null ||
            it.bearingDegrees != null
    }
}

private fun String.clipStartedAtMillis(): Long? {
    val timestamp = CLIP_TIMESTAMP_PATTERN.find(this)?.value ?: return null
    return runCatching {
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(timestamp)?.time
    }.getOrNull()
}

private fun Long.asWatermarkTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))
}

private const val METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR = 3.6f
private const val CLIP_WATERMARK_TOLERANCE_MS = 30_000L
private val CLIP_TIMESTAMP_PATTERN = Regex("""\d{8}_\d{6}""")
