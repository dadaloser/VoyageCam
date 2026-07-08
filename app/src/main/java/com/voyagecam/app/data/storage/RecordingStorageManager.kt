package com.voyagecam.app.data.storage

import android.content.Context
import android.os.Environment
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.core.model.RecordingSegment
import com.voyagecam.app.core.model.RecordingStorageOverview
import com.voyagecam.app.core.model.SegmentFileNames
import com.voyagecam.app.core.model.toStorageBytes
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.data.settings.VoyageCamSettingsStore
import java.io.File

class RecordingStorageManager(private val context: Context) {
    val dashcamRoot: File
        get() = File(recordingRoot, DASHCAM_DIRECTORY)

    val normalRoot: File
        get() = File(dashcamRoot, NORMAL_DIRECTORY)

    val lockedRoot: File
        get() = File(dashcamRoot, LOCKED_DIRECTORY)

    fun createNormalSegmentFile(startedAtMillis: Long, cameraDirection: String): File {
        val dateParts = SegmentFileNames.from(startedAtMillis)
        val directory = File(normalRoot, "${dateParts.day}/${dateParts.group}").apply {
            mkdirs()
        }

        return File(directory, "${dateParts.filePrefix}_$cameraDirection.mp4")
    }

    fun cleanupNormalSegments(maxStorageGb: Int): CleanupResult {
        val maxBytes = maxStorageGb.coerceAtLeast(VoyageCamSettingsStore.MIN_STORAGE_GB).toStorageBytes()
        val segments = normalSegments()
        val totalBytes = segments.sumOf { it.length() }
        if (totalBytes <= maxBytes) {
            return CleanupResult(totalBytes = totalBytes, deletedBytes = 0L, deletedFiles = 0)
        }

        var currentBytes = totalBytes
        var deletedBytes = 0L
        var deletedFiles = 0

        segments
            .sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.absolutePath })
            .forEach { file ->
                if (currentBytes <= maxBytes) return@forEach

                val size = file.length()
                if (file.delete()) {
                    currentBytes -= size
                    deletedBytes += size
                    deletedFiles++
                    pruneEmptyParents(file.parentFile)
                }
            }

        return CleanupResult(
            totalBytes = currentBytes,
            deletedBytes = deletedBytes,
            deletedFiles = deletedFiles,
        )
    }

    fun normalUsageBytes(): Long {
        return normalSegments().sumOf { it.length() }
    }

    fun storageOverview(settings: VoyageCamSettings, dualCameraActive: Boolean): RecordingStorageOverview {
        val normal = normalSegments()
        val locked = lockedSegments()
        return RecordingStorageOverview(
            normalBytes = normal.sumOf { it.length() },
            lockedBytes = locked.sumOf { it.length() },
            normalClipCount = normal.size,
            lockedClipCount = locked.size,
            maxStorageBytes = settings.storageCapacityGb.coerceAtLeast(VoyageCamSettingsStore.MIN_STORAGE_GB).toStorageBytes(),
            estimatedBytesPerMinute = estimateBytesPerMinute(
                dualCameraActive = dualCameraActive,
                ambientAudioEnabled = settings.ambientAudioEnabled,
            ),
        )
    }

    fun listRecentSegments(limit: Int = DEFAULT_SEGMENT_LIST_LIMIT): List<RecordingSegment> {
        val normal = normalSegments().map { file ->
            file.toRecordingSegment(locked = false)
        }
        val locked = lockedSegments().map { file ->
            file.toRecordingSegment(locked = true)
        }

        return (normal + locked)
            .sortedWith(compareByDescending<RecordingSegment> { it.lastModifiedMillis }.thenBy { it.relativePath })
            .take(limit)
    }

    fun lockNormalSegment(file: File?): File? {
        if (file == null || !file.exists() || !file.isFile) return null
        val normalPath = normalRoot.canonicalFile.toPath()
        val filePath = file.canonicalFile.toPath()
        if (!filePath.startsWith(normalPath)) return file

        val relativePath = normalPath.relativize(filePath).toString()
        val lockedFile = File(lockedRoot, relativePath).withLockedName()
        lockedFile.parentFile?.mkdirs()

        val moved = file.renameTo(lockedFile)
        if (!moved) {
            file.copyTo(lockedFile, overwrite = true)
            file.delete()
        }
        pruneEmptyParents(file.parentFile)

        return lockedFile
    }

    fun unlockSegment(segment: RecordingSegment): File? {
        if (!segment.locked) return File(segment.absolutePath).takeIf { it.exists() && it.isFile }
        val lockedFile = File(segment.absolutePath)
        if (!lockedFile.exists() || !lockedFile.isFile) return null

        val lockedPath = lockedRoot.canonicalFile.toPath()
        val filePath = runCatching { lockedFile.canonicalFile.toPath() }.getOrNull() ?: return null
        if (!filePath.startsWith(lockedPath)) return null

        val relativePath = lockedPath.relativize(filePath).toString()
        val normalFile = File(normalRoot, relativePath).withoutLockedName()
        normalFile.parentFile?.mkdirs()

        val moved = lockedFile.renameTo(normalFile)
        if (!moved) {
            lockedFile.copyTo(normalFile, overwrite = true)
            lockedFile.delete()
        }
        pruneEmptyParents(lockedFile.parentFile)

        return normalFile
    }

    fun deleteSegment(segment: RecordingSegment): DeleteResult {
        val file = File(segment.absolutePath)
        if (!file.exists() || !file.isFile) return DeleteResult(deleted = false, deletedBytes = 0L)

        val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return DeleteResult(deleted = false, deletedBytes = 0L)
        if (!canonicalFile.isUnderManagedRoot()) return DeleteResult(deleted = false, deletedBytes = 0L)

        val size = canonicalFile.length()
        val deleted = canonicalFile.delete()
        if (deleted) {
            pruneEmptyParents(canonicalFile.parentFile)
        }

        return DeleteResult(deleted = deleted, deletedBytes = if (deleted) size else 0L)
    }


    fun dashcamRelativePath(file: File?): String? {
        if (file == null) return null
        val root = dashcamRoot.canonicalFile.toPath()
        val filePath = runCatching { file.canonicalFile.toPath() }.getOrNull() ?: return file.name
        return if (filePath.startsWith(root)) {
            root.relativize(filePath).toString()
        } else {
            file.name
        }
    }

    fun dashcamFile(relativePath: String): File? {
        if (relativePath.isBlank()) return null
        val root = runCatching { dashcamRoot.canonicalFile }.getOrNull() ?: return null
        val file = runCatching { File(root, relativePath).canonicalFile }.getOrNull() ?: return null
        return if (file.toPath().startsWith(root.toPath())) {
            file
        } else {
            null
        }
    }

    private fun normalSegments(): List<File> {
        if (!normalRoot.exists()) return emptyList()
        return normalRoot.walkTopDown()
            .filter { it.isFile && it.extension.equals("mp4", ignoreCase = true) }
            .toList()
    }

    private fun lockedSegments(): List<File> {
        if (!lockedRoot.exists()) return emptyList()
        return lockedRoot.walkTopDown()
            .filter { it.isFile && it.extension.equals("mp4", ignoreCase = true) }
            .toList()
    }

    private fun File.toRecordingSegment(locked: Boolean): RecordingSegment {
        val root = if (locked) lockedRoot else normalRoot
        val relativePath = runCatching {
            root.canonicalFile.toPath().relativize(canonicalFile.toPath()).toString()
        }.getOrDefault(name)

        return RecordingSegment(
            name = name,
            relativePath = relativePath,
            groupKey = relativePath.toSegmentGroupKey(),
            absolutePath = absolutePath,
            day = relativePath.toSegmentDay(),
            cameraDirection = when {
                name.contains("_front", ignoreCase = true) -> CameraDirection.Front
                else -> CameraDirection.Rear
            },
            locked = locked,
            sizeBytes = length(),
            lastModifiedMillis = lastModified(),
        )
    }

    private fun pruneEmptyParents(startDirectory: File?) {
        var directory = startDirectory
        while (directory != null && directory.isUnderManagedRoot()) {
            val children = directory.listFiles()
            if (!children.isNullOrEmpty()) return
            val parent = directory.parentFile
            directory.delete()
            if (directory.absolutePath == normalRoot.absolutePath || directory.absolutePath == lockedRoot.absolutePath) return
            directory = parent
        }
    }

    private fun File.isUnderManagedRoot(): Boolean {
        return absolutePath.startsWith(normalRoot.absolutePath) || absolutePath.startsWith(lockedRoot.absolutePath)
    }

    private val recordingRoot: File
        get() = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir

    data class CleanupResult(
        val totalBytes: Long,
        val deletedBytes: Long,
        val deletedFiles: Int,
    )

    data class DeleteResult(
        val deleted: Boolean,
        val deletedBytes: Long,
    )

    companion object {
        private const val DASHCAM_DIRECTORY = "Dashcam"
        private const val NORMAL_DIRECTORY = "normal"
        private const val LOCKED_DIRECTORY = "locked"
        private const val REAR_VIDEO_BYTES_PER_MINUTE = 90L * 1024L * 1024L
        private const val FRONT_VIDEO_BYTES_PER_MINUTE = 55L * 1024L * 1024L
        private const val AUDIO_BYTES_PER_MINUTE = 1L * 1024L * 1024L
        private const val DEFAULT_SEGMENT_LIST_LIMIT = 30

        private fun estimateBytesPerMinute(
            dualCameraActive: Boolean,
            ambientAudioEnabled: Boolean,
        ): Long {
            return REAR_VIDEO_BYTES_PER_MINUTE +
                if (dualCameraActive) FRONT_VIDEO_BYTES_PER_MINUTE else 0L +
                if (ambientAudioEnabled) AUDIO_BYTES_PER_MINUTE else 0L
        }

        private fun File.withLockedName(): File {
            if (nameWithoutExtension.endsWith("_locked")) return this
            val lockedName = "${nameWithoutExtension}_locked.$extension"
            return File(parentFile, lockedName)
        }

        private fun File.withoutLockedName(): File {
            if (!nameWithoutExtension.endsWith("_locked")) return this
            val normalName = "${nameWithoutExtension.removeSuffix("_locked")}.$extension"
            return File(parentFile, normalName)
        }
    }
}

private fun String.toSegmentDay(): String {
    return split('/', File.separatorChar)
        .firstOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "未知日期"
}

private fun String.toSegmentGroupKey(): String {
    val normalized = replace(File.separatorChar, '/')
    return normalized.substringBeforeLast('/', missingDelimiterValue = normalized)
}
