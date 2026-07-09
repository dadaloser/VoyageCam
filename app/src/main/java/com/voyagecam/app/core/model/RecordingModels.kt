package com.voyagecam.app.core.model

data class RecordingSegment(
    val name: String,
    val relativePath: String,
    val groupKey: String,
    val absolutePath: String,
    val day: String,
    val cameraDirection: CameraDirection,
    val locked: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
)

data class RecordingStorageOverview(
    val normalBytes: Long,
    val lockedBytes: Long,
    val normalClipCount: Int,
    val lockedClipCount: Int,
    val maxStorageBytes: Long,
    val estimatedBytesPerMinute: Long,
) {
    val totalBytes: Long
        get() = normalBytes + lockedBytes

    val remainingManagedBytes: Long
        get() = (maxStorageBytes - normalBytes).coerceAtLeast(0L)

    val estimatedRemainingMinutes: Long
        get() = if (estimatedBytesPerMinute <= 0L) 0L else remainingManagedBytes / estimatedBytesPerMinute

    val normalUsagePercent: Int
        get() = if (maxStorageBytes <= 0L) 0 else ((normalBytes * 100) / maxStorageBytes).toInt().coerceIn(0, 100)

    fun requiresCleanupConfirmation(nextCapacityGb: Int): Boolean {
        return nextCapacityGb.toStorageBytes() < normalBytes
    }
}

data class PendingStorageCapacityChange(
    val nextCapacityGb: Int,
    val currentNormalBytes: Long,
    val overflowBytes: Long,
)

fun Int.toStorageBytes(): Long {
    return coerceAtLeast(0).toLong() * BYTES_PER_GB
}

enum class CameraDirection {
    Rear,
    Front,
}

private const val BYTES_PER_GB = 1024L * 1024L * 1024L
