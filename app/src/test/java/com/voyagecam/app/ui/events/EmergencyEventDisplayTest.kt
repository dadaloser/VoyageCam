package com.voyagecam.app.ui.events

import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.EmergencyTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyEventDisplayTest {
    @Test
    fun collisionSummary_includesThresholdWhenPresent() {
        val event = emergencyEvent(
            trigger = EmergencyTrigger.Collision,
            accelerationG = 2.6f,
            thresholdG = 2.0f,
        )

        assertEquals("峰值 2.6g · 阈值 2.0g", event.collisionSummary())
    }

    @Test
    fun locationSummary_includesSpeedAndBearing() {
        val event = emergencyEvent(
            latitude = 31.2304,
            longitude = 121.4737,
            speedMetersPerSecond = 18f,
            bearingDegrees = 90f,
            locationCapturedAtMillis = 1_700_000_000_000L,
        )

        val summary = event.locationSummary()

        assertTrue(summary!!.contains("位置 31.23040, 121.47370"))
        assertTrue(summary.contains("65km/h"))
        assertTrue(summary.contains("航向 90°"))
    }

    @Test
    fun locationSummary_returnsNullWhenCoordinatesMissing() {
        val event = emergencyEvent(latitude = null, longitude = null)

        assertNull(event.locationSummary())
    }

    private fun emergencyEvent(
        trigger: EmergencyTrigger = EmergencyTrigger.Manual,
        accelerationG: Float? = null,
        thresholdG: Float? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        speedMetersPerSecond: Float? = null,
        bearingDegrees: Float? = null,
        locationCapturedAtMillis: Long? = null,
    ): EmergencyEvent {
        return EmergencyEvent(
            id = "evt",
            trigger = trigger,
            triggeredAtMillis = 1_700_000_000_000L,
            accelerationG = accelerationG,
            thresholdG = thresholdG,
            latitude = latitude,
            longitude = longitude,
            speedMetersPerSecond = speedMetersPerSecond,
            bearingDegrees = bearingDegrees,
            locationCapturedAtMillis = locationCapturedAtMillis,
            segmentPaths = emptyList(),
            gpsTrackPoints = emptyList(),
        )
    }
}
