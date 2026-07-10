package com.voyagecam.app.data.evidence

import android.content.Context
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.data.storage.RecordingStorageManager
import com.voyagecam.app.feature.evidence.EmergencyEvidenceExporter
import com.voyagecam.app.feature.evidence.EvidencePackageFile
import com.voyagecam.app.feature.evidence.ExportProgress
import com.voyagecam.app.feature.evidence.RecordingClipExportFile
import com.voyagecam.app.feature.evidence.RecordingClipExportMode
import com.voyagecam.app.feature.evidence.RecordingClipExporter
import java.io.File

class EvidenceRepository(context: Context) {
    private val storageManager = RecordingStorageManager(context)
    private val exporter = EmergencyEvidenceExporter(context, storageManager)
    private val clipExporter = RecordingClipExporter(context)

    fun export(
        event: EmergencyEvent,
        settings: VoyageCamSettings,
        onProgress: (ExportProgress) -> Unit,
        isCancelled: () -> Boolean,
    ): EvidencePackageFile {
        return exporter.export(
            event = event,
            includeWatermarkSubtitles = settings.exportWatermarkSubtitlesEnabled,
            includeBurnedWatermarkVideos = settings.exportBurnedWatermarkVideoEnabled,
            segmentDurationMinutes = settings.segmentDurationMinutes,
            onProgress = onProgress,
            isCancelled = isCancelled,
        )
    }

    fun exportClipGroup(
        groupKey: String,
        rearFile: File?,
        frontFile: File?,
        mode: RecordingClipExportMode,
        onProgress: (ExportProgress) -> Unit,
        isCancelled: () -> Boolean,
    ): RecordingClipExportFile {
        return clipExporter.export(
            groupKey = groupKey,
            rearFile = rearFile,
            frontFile = frontFile,
            mode = mode,
            onProgress = onProgress,
            isCancelled = isCancelled,
        )
    }
}
