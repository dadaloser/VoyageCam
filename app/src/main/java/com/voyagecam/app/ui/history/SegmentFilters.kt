package com.voyagecam.app.ui.history

import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.core.model.RecordingSegment

enum class SegmentCameraFilter(val label: String) {
    All("全部"),
    Rear("后摄"),
    Front("前摄"),
}

enum class SegmentLockFilter(val label: String) {
    All("全部"),
    Normal("普通"),
    Locked("锁定"),
}

fun List<RecordingSegment>.filterSegments(
    selectedDay: String?,
    cameraFilter: SegmentCameraFilter,
    lockFilter: SegmentLockFilter,
): List<RecordingSegment> {
    return filter { segment ->
        val dayMatches = selectedDay == null || segment.day == selectedDay
        val cameraMatches = when (cameraFilter) {
            SegmentCameraFilter.All -> true
            SegmentCameraFilter.Rear -> segment.cameraDirection == CameraDirection.Rear
            SegmentCameraFilter.Front -> segment.cameraDirection == CameraDirection.Front
        }
        val lockMatches = when (lockFilter) {
            SegmentLockFilter.All -> true
            SegmentLockFilter.Normal -> !segment.locked
            SegmentLockFilter.Locked -> segment.locked
        }

        dayMatches && cameraMatches && lockMatches
    }
}
