package com.voyagecam.app.core.model

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

enum class EmergencyTrigger(val label: String) {
    Manual("手动锁定"),
    Collision("碰撞触发"),
}
