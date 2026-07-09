package com.voyagecam.app.ui.events

import android.content.Context
import com.voyagecam.app.R
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.EmergencyTrigger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun EmergencyEvent.collisionSummary(context: Context): String? {
    if (trigger != EmergencyTrigger.Collision) return null
    val acceleration = accelerationG ?: return null
    val threshold = thresholdG
    return if (threshold == null) {
        context.getString(R.string.events_collision_peak, acceleration)
    } else {
        context.getString(R.string.events_collision_peak_threshold, acceleration, threshold)
    }
}

fun EmergencyEvent.locationSummary(context: Context): String? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    val coordinate = context.getString(R.string.events_location, lat, lon)
    val speedText = speedMetersPerSecond?.let {
        " · ${String.format(Locale.getDefault(), context.getString(R.string.events_speed_kmh), it * METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR)}"
    }.orEmpty()
    val bearingText = bearingDegrees?.let {
        context.getString(R.string.events_bearing, it)
    }.orEmpty()
    val timeText = locationCapturedAtMillis?.let { " · ${it.asTime()}" }.orEmpty()
    return "$coordinate$speedText$bearingText$timeText"
}

fun EmergencyEvent.hasLocation(): Boolean {
    return latitude != null && longitude != null
}

private fun Long.asTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))
}

private const val METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR = 3.6f
