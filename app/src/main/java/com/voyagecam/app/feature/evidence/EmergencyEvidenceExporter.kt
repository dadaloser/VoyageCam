package com.voyagecam.app.feature.evidence

import android.content.Context
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.EmergencyTrigger
import com.voyagecam.app.data.storage.RecordingStorageManager
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EmergencyEvidenceExporter(
    private val context: Context,
    private val storageManager: RecordingStorageManager,
) {
    fun export(
        event: EmergencyEvent,
        onProgress: (ExportProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): EvidencePackageFile {
        val files = event.existingSegmentFiles(storageManager)
        if (files.isEmpty()) error("关联片段文件不存在")

        val exportDir = File(context.filesDir, EVIDENCE_EXPORT_DIR_NAME).apply { mkdirs() }
        val packageFile = event.availableEvidencePackageFile(exportDir)
        val temporaryPackageFile = File.createTempFile(packageFile.nameWithoutExtension, TEMP_PACKAGE_SUFFIX, exportDir)
        val hasGpsTrack = event.gpsTrackPoints.isNotEmpty()
        val totalSteps = files.size + 1 + if (hasGpsTrack) 1 else 0

        try {
            checkNotCancelled(isCancelled)
            onProgress(
                ExportProgress(
                    completedSteps = 0,
                    totalSteps = totalSteps,
                    currentItem = "metadata.txt",
                ),
            )

            ZipOutputStream(FileOutputStream(temporaryPackageFile)).use { zip ->
                checkNotCancelled(isCancelled)
                zip.putNextEntry(ZipEntry("metadata.txt"))
                zip.write(event.evidenceMetadata(files).toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
                var completedSteps = 1
                onProgress(
                    ExportProgress(
                        completedSteps = completedSteps,
                        totalSteps = totalSteps,
                        currentItem = "metadata.txt",
                    ),
                )

                if (hasGpsTrack) {
                    checkNotCancelled(isCancelled)
                    zip.putNextEntry(ZipEntry("gps_track.csv"))
                    zip.write(event.gpsTrackCsv().toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                    completedSteps++
                    onProgress(
                        ExportProgress(
                            completedSteps = completedSteps,
                            totalSteps = totalSteps,
                            currentItem = "gps_track.csv",
                        ),
                    )
                }

                files.forEachIndexed { index, file ->
                    checkNotCancelled(isCancelled)
                    zip.putNextEntry(ZipEntry("clips/${file.name}"))
                    file.inputStream().use { input ->
                        input.copyToInterruptibly(zip, isCancelled)
                    }
                    zip.closeEntry()
                    onProgress(
                        ExportProgress(
                            completedSteps = completedSteps + index + 1,
                            totalSteps = totalSteps,
                            currentItem = file.name,
                        ),
                    )
                }
            }
            if (!temporaryPackageFile.renameTo(packageFile)) {
                error("无法保存证据包")
            }
        } catch (cancelled: EvidenceExportCancelledException) {
            temporaryPackageFile.delete()
            throw cancelled
        } catch (error: Throwable) {
            temporaryPackageFile.delete()
            throw error
        }

        return EvidencePackageFile(file = packageFile, clipCount = files.size)
    }

    private fun checkNotCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw EvidenceExportCancelledException()
    }

    private fun java.io.InputStream.copyToInterruptibly(
        output: java.io.OutputStream,
        isCancelled: () -> Boolean,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            checkNotCancelled(isCancelled)
            val bytes = read(buffer)
            if (bytes < 0) break
            output.write(buffer, 0, bytes)
        }
    }

    private fun EmergencyEvent.evidencePackageFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(triggeredAtMillis))
        return "voyagecam_evidence_${timestamp}_${id.take(8)}.zip"
    }

    private fun EmergencyEvent.availableEvidencePackageFile(exportDir: File): File {
        val baseName = evidencePackageFileName().removeSuffix(".zip")
        var candidate = File(exportDir, "$baseName.zip")
        var index = 2
        while (candidate.exists()) {
            candidate = File(exportDir, "${baseName}_$index.zip")
            index++
        }
        return candidate
    }

    private fun EmergencyEvent.evidenceMetadata(files: List<File>): String {
        return buildString {
            appendLine("VoyageCam Emergency Evidence Package")
            appendLine()
            appendLine("Event")
            appendLine("ID: $id")
            appendLine("Trigger: ${trigger.label}")
            appendLine("Triggered At: ${triggeredAtMillis.asTime()}")
            collisionSummary()?.let { appendLine("Collision: $it") }
            locationSummary()?.let { appendLine("Location: $it") }
            if (gpsTrackPoints.isNotEmpty()) {
                appendLine("GPS Track Points: ${gpsTrackPoints.size}")
                appendLine("GPS Track File: gps_track.csv")
            }
            appendLine()
            appendLine("Linked Clips")
            files.forEachIndexed { index, file ->
                appendLine("${index + 1}. ${file.name}")
                appendLine("   Size: ${file.length().asFileSize()}")
                appendLine("   Last Modified: ${file.lastModified().asTime()}")
            }
            appendLine()
            appendLine("Original Relative Paths")
            segmentPaths.forEach { path -> appendLine(path) }
        }
    }

    private fun EmergencyEvent.gpsTrackCsv(): String {
        return buildString {
            appendLine("captured_at,latitude,longitude,speed_kmh")
            gpsTrackPoints
                .sortedBy { it.capturedAtMillis }
                .forEach { point ->
                    val speedKmh = point.speedMetersPerSecond
                        ?.let { String.format(Locale.US, "%.1f", it * METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR) }
                        .orEmpty()
                    appendLine(
                        listOf(
                            point.capturedAtMillis.asTime(),
                            String.format(Locale.US, "%.6f", point.latitude),
                            String.format(Locale.US, "%.6f", point.longitude),
                            speedKmh,
                        ).joinToString(separator = ","),
                    )
                }
        }
    }

    private fun EmergencyEvent.collisionSummary(): String? {
        if (trigger != EmergencyTrigger.Collision) return null
        val acceleration = accelerationG ?: return null
        val threshold = thresholdG
        return if (threshold == null) {
            String.format(Locale.getDefault(), "峰值 %.1fg", acceleration)
        } else {
            String.format(Locale.getDefault(), "峰值 %.1fg · 阈值 %.1fg", acceleration, threshold)
        }
    }

    private fun EmergencyEvent.locationSummary(): String? {
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        val coordinate = String.format(Locale.getDefault(), "位置 %.5f, %.5f", lat, lon)
        val speedText = speedMetersPerSecond?.let {
            String.format(Locale.getDefault(), " · %.0fkm/h", it * METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR)
        }.orEmpty()
        val timeText = locationCapturedAtMillis?.let { " · ${it.asTime()}" }.orEmpty()
        return "$coordinate$speedText$timeText"
    }

    private fun EmergencyEvent.existingSegmentFiles(storageManager: RecordingStorageManager): List<File> {
        return segmentPaths
            .mapNotNull { storageManager.dashcamFile(it) }
            .filter { it.exists() && it.isFile }
    }

    private fun Long.asTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))
    }

    private fun Long.asFileSize(): String {
        val kb = this / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.getDefault(), "%.1fGB", gb)
            mb >= 1.0 -> String.format(Locale.getDefault(), "%.1fMB", mb)
            kb >= 1.0 -> String.format(Locale.getDefault(), "%.0fKB", kb)
            else -> "${this}B"
        }
    }

    private companion object {
        const val EVIDENCE_EXPORT_DIR_NAME = "evidence_exports"
        const val TEMP_PACKAGE_SUFFIX = ".tmp"
        const val METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR = 3.6f
    }
}

data class EvidencePackageFile(
    val file: File,
    val clipCount: Int,
)

data class ExportProgress(
    val completedSteps: Int,
    val totalSteps: Int,
    val currentItem: String,
) {
    val percent: Int
        get() = if (totalSteps <= 0) 0 else (completedSteps * 100 / totalSteps).coerceIn(0, 100)
}

class EvidenceExportCancelledException : RuntimeException("导出已取消")
