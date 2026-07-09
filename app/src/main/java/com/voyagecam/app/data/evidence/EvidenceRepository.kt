package com.voyagecam.app.data.evidence

import android.content.Context
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.data.storage.RecordingStorageManager
import com.voyagecam.app.feature.evidence.EmergencyEvidenceExporter
import com.voyagecam.app.feature.evidence.EvidencePackageFile
import com.voyagecam.app.feature.evidence.ExportProgress

class EvidenceRepository(context: Context) {
    private val storageManager = RecordingStorageManager(context)
    private val exporter = EmergencyEvidenceExporter(context, storageManager)

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
}
