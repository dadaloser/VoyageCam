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
)

enum class EmergencyTrigger(val label: String) {
    Manual("手动锁定"),
    Collision("碰撞触发"),
}
