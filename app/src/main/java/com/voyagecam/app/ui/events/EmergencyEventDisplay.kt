package com.voyagecam.app.ui.events

import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.EmergencyTrigger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun EmergencyEvent.collisionSummary(): String? {
    if (trigger != EmergencyTrigger.Collision) return null
    val acceleration = accelerationG ?: return null
    val threshold = thresholdG
    return if (threshold == null) {
        String.format(Locale.getDefault(), "峰值 %.1fg", acceleration)
    } else {
        String.format(Locale.getDefault(), "峰值 %.1fg · 阈值 %.1fg", acceleration, threshold)
    }
}

fun EmergencyEvent.locationSummary(): String? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    val coordinate = String.format(Locale.getDefault(), "位置 %.5f, %.5f", lat, lon)
    val speedText = speedMetersPerSecond?.let {
        String.format(Locale.getDefault(), " · %.0fkm/h", it * METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR)
    }.orEmpty()
    val timeText = locationCapturedAtMillis?.let { " · ${it.asTime()}" }.orEmpty()
    return "$coordinate$speedText$timeText"
}

fun EmergencyEvent.hasLocation(): Boolean {
    return latitude != null && longitude != null
}

private fun Long.asTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))
}

private const val METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR = 3.6f
