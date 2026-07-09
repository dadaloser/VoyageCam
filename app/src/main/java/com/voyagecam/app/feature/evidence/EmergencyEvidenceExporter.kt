package com.voyagecam.app.feature.evidence

import android.content.Context
import com.voyagecam.app.R
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.GpsTrackPoint
import com.voyagecam.app.data.storage.RecordingStorageManager
import com.voyagecam.app.ui.events.collisionSummary
import com.voyagecam.app.ui.events.locationSummary
import com.voyagecam.app.ui.labelRes
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
        if (files.isEmpty()) error(context.getString(R.string.evidence_export_files_missing))

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
                            context = context,
                            fileNameWithoutExtension = file.nameWithoutExtension,
                            segmentDurationMinutes = segmentDurationMinutes,
                        ) ?: error(context.getString(R.string.evidence_export_timestamp_parse_failed, file.name))
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
                error(context.getString(R.string.evidence_export_save_failed))
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
                            currentItem = context.getString(
                                R.string.evidence_export_transcoding,
                                sourceFile.name,
                                percent,
                            ),
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
            appendLine(context.getString(R.string.evidence_metadata_title, context.getString(R.string.app_name)))
            appendLine()
            appendLine(context.getString(R.string.evidence_metadata_section_event))
            appendLine(context.getString(R.string.evidence_metadata_id, id))
            appendLine(context.getString(R.string.evidence_trigger_prefix, context.getString(trigger.labelRes())))
            appendLine(context.getString(R.string.evidence_metadata_triggered_at, triggeredAtMillis.asTime()))
            collisionSummary(context)?.let { appendLine(context.getString(R.string.evidence_metadata_collision, it)) }
            locationSummary(context)?.let { appendLine(context.getString(R.string.evidence_metadata_location, it)) }
            if (gpsTrackPoints.isNotEmpty()) {
                appendLine(context.getString(R.string.evidence_metadata_gps_track_points, gpsTrackPoints.size))
                appendLine(context.getString(R.string.evidence_metadata_gps_track_file, GPS_TRACK_ENTRY_NAME))
            }
            if (watermarkSubtitleCount > 0) {
                appendLine(context.getString(R.string.evidence_metadata_watermark_subtitle_files, watermarkSubtitleCount))
                appendLine(context.getString(R.string.evidence_metadata_watermark_directory, WATERMARK_DIRECTORY_NAME))
            }
            if (burnedWatermarkClipCount > 0) {
                appendLine(context.getString(R.string.evidence_metadata_burned_watermark_clips, burnedWatermarkClipCount))
                appendLine(
                    context.getString(
                        R.string.evidence_metadata_clips_directory,
                        CLIPS_DIRECTORY_NAME,
                    ),
                )
            }
            appendLine()
            appendLine(context.getString(R.string.evidence_metadata_section_linked_clips))
            files.forEachIndexed { index, file ->
                appendLine(context.getString(R.string.evidence_metadata_clip_item, index + 1, file.name))
                appendLine(context.getString(R.string.evidence_metadata_clip_size, file.length().asFileSize()))
                appendLine(
                    context.getString(
                        R.string.evidence_metadata_clip_last_modified,
                        file.lastModified().asTime(),
                    ),
                )
            }
            appendLine()
            appendLine(context.getString(R.string.evidence_metadata_section_original_relative_paths))
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
                entryName = "$WATERMARK_DIRECTORY_NAME${file.nameWithoutExtension}.srt",
                content = content,
            )
        }
    }

    private fun EmergencyEvent.gpsTrackCsv(): String {
        return buildString {
            appendLine(GPS_TRACK_CSV_HEADER)
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
            add(context.getString(R.string.app_name))
            add(capturedAtMillis.asTime())
            speedMetersPerSecond?.let {
                add(
                    String.format(
                        Locale.getDefault(),
                        context.getString(R.string.events_speed_kmh),
                        it * METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR,
                    ),
                )
            }
            bearingDegrees?.let {
                add(context.getString(R.string.evidence_clip_bearing, it))
            }
            if (latitude != null && longitude != null) {
                add(String.format(Locale.getDefault(), "%.5f, %.5f", latitude, longitude))
            }
        }.joinToString(separator = " · ")
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
        const val GPS_TRACK_ENTRY_NAME = "gps_track.csv"
        const val GPS_TRACK_CSV_HEADER = "captured_at,latitude,longitude,speed_kmh,bearing_degrees"
        const val WATERMARK_DIRECTORY_NAME = "watermark/"
        const val CLIPS_DIRECTORY_NAME = "clips/"
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

class EvidenceExportCancelledException : RuntimeException()
