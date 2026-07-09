package com.voyagecam.app.feature.evidence

import android.content.Context
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.EmergencyTrigger
import com.voyagecam.app.core.model.GpsTrackPoint
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
    @androidx.media3.common.util.UnstableApi
    private val watermarkVideoTranscoder = EvidenceWatermarkVideoTranscoder(context)

    fun export(
        event: EmergencyEvent,
        includeWatermarkSubtitles: Boolean = false,
        includeBurnedWatermarkVideos: Boolean = false,
        segmentDurationMinutes: Int = DEFAULT_SEGMENT_DURATION_MINUTES,
        onProgress: (ExportProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): EvidencePackageFile {
        val files = event.existingSegmentFiles(storageManager)
        if (files.isEmpty()) error("关联片段文件不存在")

        val exportDir = File(context.filesDir, EVIDENCE_EXPORT_DIR_NAME).apply { mkdirs() }
        val packageFile = event.availableEvidencePackageFile(exportDir)
        val temporaryPackageFile = File.createTempFile(packageFile.nameWithoutExtension, TEMP_PACKAGE_SUFFIX, exportDir)
        val hasGpsTrack = event.gpsTrackPoints.isNotEmpty()
        val watermarkSubtitles = if (includeWatermarkSubtitles) {
            event.watermarkSubtitles(files, segmentDurationMinutes)
        } else {
            emptyList()
        }
        val shouldBurnWatermarkVideos = includeBurnedWatermarkVideos && files.isNotEmpty()
        val totalSteps = files.size + 1 + (if (hasGpsTrack) 1 else 0) + watermarkSubtitles.size
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
                zip.write(
                    event.evidenceMetadata(
                        files = files,
                        watermarkSubtitleCount = watermarkSubtitles.size,
                        burnedWatermarkClipCount = if (shouldBurnWatermarkVideos) files.size else 0,
                    ).toByteArray(StandardCharsets.UTF_8),
                )
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

                watermarkSubtitles.forEach { subtitle ->
                    checkNotCancelled(isCancelled)
                    zip.putNextEntry(ZipEntry(subtitle.entryName))
                    zip.write(subtitle.content.toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                    completedSteps++
                    onProgress(
                        ExportProgress(
                            completedSteps = completedSteps,
                            totalSteps = totalSteps,
                            currentItem = subtitle.entryName,
                        ),
                    )
                }

                files.forEachIndexed { index, file ->
                    checkNotCancelled(isCancelled)
                    val clipSource = if (shouldBurnWatermarkVideos) {
                        val watermark = event.buildClipWatermark(
                            fileNameWithoutExtension = file.nameWithoutExtension,
                            segmentDurationMinutes = segmentDurationMinutes,
                        ) ?: error("无法解析片段时间戳：${file.name}")
                        transcodeWatermarkedClip(
                            sourceFile = file,
                            watermark = watermark,
                            exportDir = exportDir,
                            completedSteps = completedSteps + index,
                            totalSteps = totalSteps,
                            onProgress = onProgress,
                            isCancelled = isCancelled,
                        )
                    } else {
                        file
                    }
                    try {
                        zip.putNextEntry(ZipEntry("clips/${file.name}"))
                        clipSource.inputStream().use { input ->
                            input.copyToInterruptibly(zip, isCancelled)
                        }
                        zip.closeEntry()
                    } finally {
                        if (clipSource != file) {
                            clipSource.delete()
                        }
                    }
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

    @androidx.media3.common.util.UnstableApi
    private fun transcodeWatermarkedClip(
        sourceFile: File,
        watermark: EvidenceClipWatermark,
        exportDir: File,
        completedSteps: Int,
        totalSteps: Int,
        onProgress: (ExportProgress) -> Unit,
        isCancelled: () -> Boolean,
    ): File {
        val outputFile = File.createTempFile(sourceFile.nameWithoutExtension, ".mp4", exportDir)
        try {
            watermarkVideoTranscoder.transcode(
                inputFile = sourceFile,
                outputFile = outputFile,
                watermark = watermark,
                onProgress = { percent ->
                    val overallPercent = (((completedSteps + percent / 100.0) / totalSteps) * 100)
                        .toInt()
                        .coerceIn(0, 99)
                    onProgress(
                        ExportProgress(
                            completedSteps = completedSteps,
                            totalSteps = totalSteps,
                            currentItem = "转码 ${sourceFile.name} ($percent%)",
                            progressPercentOverride = overallPercent,
                        ),
                    )
                },
                isCancelled = isCancelled,
            )
            return outputFile
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        }
    }

    private fun EmergencyEvent.evidenceMetadata(
        files: List<File>,
        watermarkSubtitleCount: Int,
        burnedWatermarkClipCount: Int,
    ): String {
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
            if (watermarkSubtitleCount > 0) {
                appendLine("Watermark Subtitle Files: $watermarkSubtitleCount")
                appendLine("Watermark Directory: watermark/")
            }
            if (burnedWatermarkClipCount > 0) {
                appendLine("Burned Watermark Clips: $burnedWatermarkClipCount")
                appendLine("Clips Directory: clips/ (transcoded export copies)")
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

    private fun EmergencyEvent.watermarkSubtitles(files: List<File>, segmentDurationMinutes: Int): List<WatermarkSubtitle> {
        val clipWindowMillis = segmentDurationMinutes
            .coerceAtLeast(1)
            .toLong() * 60_000L + CLIP_WATERMARK_TOLERANCE_MS
        val orderedTrack = gpsTrackPoints.sortedBy { it.capturedAtMillis }
        return files.mapNotNull { file ->
            val clipStartMillis = file.clipStartedAtMillis() ?: return@mapNotNull null
            val clipEndMillis = clipStartMillis + clipWindowMillis
            val cues = orderedTrack
                .filter { point -> point.capturedAtMillis in clipStartMillis..clipEndMillis }
                .map { point -> point.toWatermarkCue() }
                .ifEmpty {
                    eventLocationCue()
                        ?.takeIf { cue -> cue.capturedAtMillis in clipStartMillis..clipEndMillis }
                        ?.let(::listOf)
                        .orEmpty()
                }
            val content = cues.toWatermarkSrt(clipStartMillis).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WatermarkSubtitle(
                entryName = "watermark/${file.nameWithoutExtension}.srt",
                content = content,
            )
        }
    }

    private fun EmergencyEvent.gpsTrackCsv(): String {
        return buildString {
            appendLine("captured_at,latitude,longitude,speed_kmh,bearing_degrees")
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
                            point.bearingDegrees
                                ?.let { String.format(Locale.US, "%.0f", it) }
                                .orEmpty(),
                        ).joinToString(separator = ","),
                    )
                }
        }
    }

    private fun File.clipStartedAtMillis(): Long? {
        val timestamp = CLIP_TIMESTAMP_PATTERN.find(nameWithoutExtension)?.value ?: return null
        return runCatching {
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(timestamp)?.time
        }.getOrNull()
    }

    private fun GpsTrackPoint.toWatermarkCue(): WatermarkCue {
        return WatermarkCue(
            capturedAtMillis = capturedAtMillis,
            latitude = latitude,
            longitude = longitude,
            speedMetersPerSecond = speedMetersPerSecond,
            bearingDegrees = bearingDegrees,
        )
    }

    private fun EmergencyEvent.eventLocationCue(): WatermarkCue? {
        return WatermarkCue(
            capturedAtMillis = locationCapturedAtMillis ?: triggeredAtMillis,
            latitude = latitude,
            longitude = longitude,
            speedMetersPerSecond = speedMetersPerSecond,
            bearingDegrees = bearingDegrees,
        ).takeIf {
            it.latitude != null ||
                it.longitude != null ||
                it.speedMetersPerSecond != null ||
                it.bearingDegrees != null
        }
    }

    private fun List<WatermarkCue>.toWatermarkSrt(clipStartMillis: Long): String {
        if (isEmpty()) return ""
        val orderedCues = sortedBy { it.capturedAtMillis }
        return buildString {
            orderedCues.forEachIndexed { index, cue ->
                val startOffset = (cue.capturedAtMillis - clipStartMillis).coerceAtLeast(0L)
                val nextStartOffset = orderedCues.getOrNull(index + 1)
                    ?.let { (it.capturedAtMillis - clipStartMillis).coerceAtLeast(startOffset + MIN_SUBTITLE_DURATION_MS) }
                val endOffset = (nextStartOffset ?: startOffset + DEFAULT_SUBTITLE_DURATION_MS)
                    .coerceAtLeast(startOffset + MIN_SUBTITLE_DURATION_MS)
                appendLine(index + 1)
                appendLine("${startOffset.asSrtOffset()} --> ${endOffset.asSrtOffset()}")
                appendLine(cue.toWatermarkText())
                appendLine()
            }
        }
    }

    private fun WatermarkCue.toWatermarkText(): String {
        return buildList {
            add("VoyageCam")
            add(capturedAtMillis.asTime())
            speedMetersPerSecond?.let {
                add(String.format(Locale.getDefault(), "%.0fkm/h", it * METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR))
            }
            bearingDegrees?.let {
                add(String.format(Locale.getDefault(), "航向 %.0f°", it))
            }
            if (latitude != null && longitude != null) {
                add(String.format(Locale.getDefault(), "%.5f, %.5f", latitude, longitude))
            }
        }.joinToString(separator = " · ")
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
        val bearingText = bearingDegrees?.let {
            String.format(Locale.getDefault(), " · 航向 %.0f°", it)
        }.orEmpty()
        val timeText = locationCapturedAtMillis?.let { " · ${it.asTime()}" }.orEmpty()
        return "$coordinate$speedText$bearingText$timeText"
    }

    private fun EmergencyEvent.existingSegmentFiles(storageManager: RecordingStorageManager): List<File> {
        return segmentPaths
            .mapNotNull { storageManager.dashcamFile(it) }
            .filter { it.exists() && it.isFile }
    }

    private fun Long.asTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))
    }

    private fun Long.asSrtOffset(): String {
        val safeMillis = coerceAtLeast(0L)
        val hours = safeMillis / 3_600_000L
        val minutes = safeMillis % 3_600_000L / 60_000L
        val seconds = safeMillis % 60_000L / 1_000L
        val millis = safeMillis % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
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
        const val DEFAULT_SEGMENT_DURATION_MINUTES = 3
        const val DEFAULT_SUBTITLE_DURATION_MS = 5_000L
        const val MIN_SUBTITLE_DURATION_MS = 1_000L
        const val CLIP_WATERMARK_TOLERANCE_MS = 30_000L
        val CLIP_TIMESTAMP_PATTERN = Regex("""\d{8}_\d{6}""")
    }
}

private data class WatermarkSubtitle(
    val entryName: String,
    val content: String,
)

private data class WatermarkCue(
    val capturedAtMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
)

data class EvidencePackageFile(
    val file: File,
    val clipCount: Int,
)

data class ExportProgress(
    val completedSteps: Int,
    val totalSteps: Int,
    val currentItem: String,
    val progressPercentOverride: Int? = null,
) {
    val percent: Int
        get() = progressPercentOverride ?: if (totalSteps <= 0) 0 else (completedSteps * 100 / totalSteps).coerceIn(0, 100)
}

class EvidenceExportCancelledException : RuntimeException("导出已取消")
