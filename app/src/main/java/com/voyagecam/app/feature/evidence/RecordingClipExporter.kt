package com.voyagecam.app.feature.evidence

import android.content.Context
import com.voyagecam.app.R
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RecordingClipExporter(
    private val context: Context,
) {
    fun export(
        groupKey: String,
        rearFile: File?,
        frontFile: File?,
        mode: RecordingClipExportMode,
        onProgress: (ExportProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): RecordingClipExportFile {
        val exportDir = File(context.filesDir, CLIP_EXPORT_DIR_NAME).apply { mkdirs() }
        val selectedFiles = when (mode) {
            RecordingClipExportMode.RearOnly ->
                listOfNotNull(rearFile ?: error(context.getString(R.string.history_export_rear_missing)))

            RecordingClipExportMode.FrontOnly ->
                listOfNotNull(frontFile ?: error(context.getString(R.string.history_export_front_missing)))

            RecordingClipExportMode.DualPackage -> {
                val rear = rearFile ?: error(context.getString(R.string.history_export_rear_missing))
                val front = frontFile ?: error(context.getString(R.string.history_export_front_missing))
                listOf(rear, front)
            }
        }

        return when (mode) {
            RecordingClipExportMode.RearOnly,
            RecordingClipExportMode.FrontOnly -> {
                val source = selectedFiles.first()
                val outputFile = availableExportFile(
                    exportDir = exportDir,
                    baseName = groupKey.exportBaseName(source, mode),
                    extension = "mp4",
                )
                copySingleClip(
                    sourceFile = source,
                    outputFile = outputFile,
                    onProgress = onProgress,
                    isCancelled = isCancelled,
                )
            }

            RecordingClipExportMode.DualPackage -> {
                val outputFile = availableExportFile(
                    exportDir = exportDir,
                    baseName = groupKey.exportBaseName(selectedFiles.first(), mode),
                    extension = "zip",
                )
                zipClipGroup(
                    files = selectedFiles,
                    outputFile = outputFile,
                    onProgress = onProgress,
                    isCancelled = isCancelled,
                )
            }
        }
    }

    private fun copySingleClip(
        sourceFile: File,
        outputFile: File,
        onProgress: (ExportProgress) -> Unit,
        isCancelled: () -> Boolean,
    ): RecordingClipExportFile {
        val tempFile = File.createTempFile(outputFile.nameWithoutExtension, TEMP_EXPORT_SUFFIX, outputFile.parentFile)
        val totalBytes = sourceFile.length().coerceAtLeast(1L)
        try {
            tempFile.outputStream().use { output ->
                sourceFile.inputStream().use { input ->
                    copyStreamInterruptibly(
                        input = input,
                        output = output,
                        currentItem = sourceFile.name,
                        totalBytes = totalBytes,
                        bytesCopiedBefore = 0L,
                        completedSteps = 0,
                        totalSteps = 1,
                        onProgress = onProgress,
                        isCancelled = isCancelled,
                    )
                }
            }
            checkNotCancelled(isCancelled)
            if (!tempFile.renameTo(outputFile)) {
                error(context.getString(R.string.history_export_save_failed))
            }
            onProgress(
                ExportProgress(
                    completedSteps = 1,
                    totalSteps = 1,
                    currentItem = sourceFile.name,
                ),
            )
            return RecordingClipExportFile(file = outputFile, itemCount = 1)
        } catch (cancelled: EvidenceExportCancelledException) {
            tempFile.delete()
            throw cancelled
        } catch (error: Throwable) {
            tempFile.delete()
            throw error
        }
    }

    private fun zipClipGroup(
        files: List<File>,
        outputFile: File,
        onProgress: (ExportProgress) -> Unit,
        isCancelled: () -> Boolean,
    ): RecordingClipExportFile {
        val tempFile = File.createTempFile(outputFile.nameWithoutExtension, TEMP_EXPORT_SUFFIX, outputFile.parentFile)
        val totalBytes = files.sumOf { it.length().coerceAtLeast(1L) }.coerceAtLeast(1L)
        var copiedBytes = 0L
        try {
            ZipOutputStream(FileOutputStream(tempFile)).use { zip ->
                files.forEachIndexed { index, file ->
                    checkNotCancelled(isCancelled)
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { input ->
                        copiedBytes += copyStreamInterruptibly(
                            input = input,
                            output = zip,
                            currentItem = file.name,
                            totalBytes = totalBytes,
                            bytesCopiedBefore = copiedBytes,
                            completedSteps = index,
                            totalSteps = files.size,
                            onProgress = onProgress,
                            isCancelled = isCancelled,
                        )
                    }
                    zip.closeEntry()
                    onProgress(
                        ExportProgress(
                            completedSteps = index + 1,
                            totalSteps = files.size,
                            currentItem = file.name,
                        ),
                    )
                }
            }
            checkNotCancelled(isCancelled)
            if (!tempFile.renameTo(outputFile)) {
                error(context.getString(R.string.history_export_save_failed))
            }
            return RecordingClipExportFile(file = outputFile, itemCount = files.size)
        } catch (cancelled: EvidenceExportCancelledException) {
            tempFile.delete()
            throw cancelled
        } catch (error: Throwable) {
            tempFile.delete()
            throw error
        }
    }

    private fun copyStreamInterruptibly(
        input: InputStream,
        output: OutputStream,
        currentItem: String,
        totalBytes: Long,
        bytesCopiedBefore: Long,
        completedSteps: Int,
        totalSteps: Int,
        onProgress: (ExportProgress) -> Unit,
        isCancelled: () -> Boolean,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copiedBytes = 0L
        while (true) {
            checkNotCancelled(isCancelled)
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copiedBytes += read
            val progressPercent = (((bytesCopiedBefore + copiedBytes).toDouble() / totalBytes) * 100)
                .toInt()
                .coerceIn(0, 99)
            onProgress(
                ExportProgress(
                    completedSteps = completedSteps,
                    totalSteps = totalSteps,
                    currentItem = currentItem,
                    progressPercentOverride = progressPercent,
                ),
            )
        }
        return copiedBytes
    }

    private fun availableExportFile(
        exportDir: File,
        baseName: String,
        extension: String,
    ): File {
        var candidate = File(exportDir, "$baseName.$extension")
        var index = 2
        while (candidate.exists()) {
            candidate = File(exportDir, "${baseName}_$index.$extension")
            index++
        }
        return candidate
    }

    private fun checkNotCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw EvidenceExportCancelledException()
    }

    private fun String.exportBaseName(sourceFile: File, mode: RecordingClipExportMode): String {
        val groupLabel = substringAfterLast('/')
            .takeIf { it.isNotBlank() }
            ?: sourceFile.nameWithoutExtension.removeCameraSuffix()
        val suffix = when (mode) {
            RecordingClipExportMode.RearOnly -> "rear"
            RecordingClipExportMode.FrontOnly -> "front"
            RecordingClipExportMode.DualPackage -> "dual"
        }
        return "voyagecam_export_${groupLabel}_${suffix}"
    }

    private fun String.removeCameraSuffix(): String {
        return replace(CAMERA_SUFFIX_REGEX, "")
    }

    private companion object {
        const val CLIP_EXPORT_DIR_NAME = "clip_exports"
        const val TEMP_EXPORT_SUFFIX = ".tmp"
        val CAMERA_SUFFIX_REGEX = Regex("_(rear|front)(?:_locked)?$", RegexOption.IGNORE_CASE)
    }
}

enum class RecordingClipExportMode {
    RearOnly,
    FrontOnly,
    DualPackage,
}

data class RecordingClipExportFile(
    val file: File,
    val itemCount: Int,
)
