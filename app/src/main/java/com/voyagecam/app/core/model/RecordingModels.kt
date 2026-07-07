package com.voyagecam.app.core.model

data class RecordingSegment(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val day: String,
    val cameraDirection: CameraDirection,
    val locked: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
)

enum class CameraDirection(val label: String) {
    Rear("后摄"),
    Front("前摄"),
}
