package com.voyagecam.app.core.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class EmergencyEvent(
    val id: String,
    val trigger: EmergencyTrigger,
    val triggeredAtMillis: Long,
    val accelerationG: Float?,
    val thresholdG: Float?,
    val latitude: Double?,
    val longitude: Double?,
    val speedMetersPerSecond: Float?,
    val locationCapturedAtMillis: Long?,
    val segmentPaths: List<String>,
    val gpsTrackPoints: List<GpsTrackPoint> = emptyList(),
)

data class EmergencyEventRepairResult(
    val updatedEvents: Int,
    val removedSegmentPaths: Int,
    val emptyEvents: Int,
)

data class GpsTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val speedMetersPerSecond: Float?,
    val capturedAtMillis: Long,
)

data class GpsTrackSummary(
    val pointCount: Int,
    val distanceMeters: Double,
    val durationMillis: Long,
    val averageSpeedMetersPerSecond: Double,
    val maxSpeedMetersPerSecond: Float?,
    val startPoint: GpsTrackPoint,
    val endPoint: GpsTrackPoint,
)

enum class EmergencyTrigger(val label: String) {
    Manual("手动锁定"),
    Collision("碰撞触发"),
}

fun List<GpsTrackPoint>.toGpsTrackSummary(): GpsTrackSummary? {
    val orderedPoints = sortedBy { it.capturedAtMillis }
    val startPoint = orderedPoints.firstOrNull() ?: return null
    val endPoint = orderedPoints.last()
    val distanceMeters = orderedPoints
        .zipWithNext()
        .sumOf { (from, to) -> from.distanceMetersTo(to) }
    val durationMillis = (endPoint.capturedAtMillis - startPoint.capturedAtMillis).coerceAtLeast(0L)
    val averageSpeed = if (durationMillis > 0L) {
        distanceMeters / (durationMillis / 1000.0)
    } else {
        0.0
    }

    return GpsTrackSummary(
        pointCount = orderedPoints.size,
        distanceMeters = distanceMeters,
        durationMillis = durationMillis,
        averageSpeedMetersPerSecond = averageSpeed,
        maxSpeedMetersPerSecond = orderedPoints.mapNotNull { it.speedMetersPerSecond }.maxOrNull(),
        startPoint = startPoint,
        endPoint = endPoint,
    )
}

private fun GpsTrackPoint.distanceMetersTo(other: GpsTrackPoint): Double {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val deltaLat = Math.toRadians(other.latitude - latitude)
    val deltaLon = Math.toRadians(other.longitude - longitude)
    val a = sin(deltaLat / 2).pow(2.0) +
        cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_METERS * c
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
