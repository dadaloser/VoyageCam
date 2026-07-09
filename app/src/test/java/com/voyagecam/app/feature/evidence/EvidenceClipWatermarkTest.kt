package com.voyagecam.app.feature.evidence

import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.EmergencyTrigger
import com.voyagecam.app.core.model.GpsTrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceClipWatermarkTest {
    @Test
    fun buildsClipWatermarkFromTrackPointsWithinClipWindow() {
        val event = emergencyEvent(
            gpsTrackPoints = listOf(
                GpsTrackPoint(
                    latitude = 31.2304,
                    longitude = 121.4737,
                    speedMetersPerSecond = 18f,
                    bearingDegrees = 90f,
                    capturedAtMillis = CLIP_START_MILLIS + 5_000L,
                ),
            ),
        )

        val watermark = event.buildClipWatermark(
            fileNameWithoutExtension = "rear_${CLIP_TIMESTAMP}",
            segmentDurationMinutes = 3,
        )

        assertNotNull(watermark)
        val lines = watermark!!.linesAt(5_000_000L)
        assertTrue(lines.first().startsWith("时间 "))
        assertTrue(lines.any { it.contains("65km/h") })
        assertTrue(lines.any { it.contains("航向 90°") })
        assertTrue(lines.any { it.contains("31.23040, 121.47370") })
    }

    @Test
    fun keepsTimestampOverlayEvenWhenLocationSamplesAreMissing() {
        val watermark = emergencyEvent().buildClipWatermark(
            fileNameWithoutExtension = "rear_${CLIP_TIMESTAMP}",
            segmentDurationMinutes = 3,
        )

        val lines = watermark!!.linesAt(0L)

        assertEquals(1, lines.size)
        assertTrue(lines.single().startsWith("时间 "))
    }

    private fun emergencyEvent(
        gpsTrackPoints: List<GpsTrackPoint> = emptyList(),
    ): EmergencyEvent {
        return EmergencyEvent(
            id = "evt",
            trigger = EmergencyTrigger.Manual,
            triggeredAtMillis = CLIP_START_MILLIS + 30_000L,
            accelerationG = null,
            thresholdG = null,
            latitude = null,
            longitude = null,
            speedMetersPerSecond = null,
            bearingDegrees = null,
            locationCapturedAtMillis = null,
            segmentPaths = emptyList(),
            gpsTrackPoints = gpsTrackPoints,
        )
    }
}

private const val CLIP_TIMESTAMP = "20240711_103000"
private const val CLIP_START_MILLIS = 1_720_665_000_000L
