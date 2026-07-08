package com.voyagecam.app.core.model

data class EmergencyLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val capturedAtMillis: Long,
)
