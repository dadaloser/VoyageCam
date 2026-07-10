package com.voyagecam.app.data.recording

import android.content.Context
import com.voyagecam.app.R
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.EmergencyEventRepairResult
import com.voyagecam.app.core.model.RecordingSegment
import com.voyagecam.app.core.model.RecordingStorageOverview
import com.voyagecam.app.data.emergency.EmergencyEventStore
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.data.settings.resolveRecordingConfig
import com.voyagecam.app.data.storage.RecordingStorageManager
import java.io.File

class RecordingRepository(context: Context) {
    private val appContext = context.applicationContext
    private val storageManager = RecordingStorageManager(context)
    private val emergencyEventStore = EmergencyEventStore(context)

    suspend fun listSegments(): List<RecordingSegment> = storageManager.listRecentSegments()

    suspend fun listEmergencyEvents(): List<EmergencyEvent> = emergencyEventStore.listRecentEvents()

    suspend fun storageOverview(settings: VoyageCamSettings, capability: DualCameraCapability): RecordingStorageOverview {
        return storageManager.storageOverview(
            settings = settings,
            dualCameraActive = settings.resolveRecordingConfig(capability).dualCameraActive,
        )
    }

    suspend fun cleanupNormalSegments(maxStorageGb: Int): RecordingStorageManager.CleanupResult {
        return storageManager.cleanupNormalSegments(maxStorageGb)
    }

    suspend fun unlockSegment(segment: RecordingSegment): File {
        val lockedDashcamPath = storageManager.dashcamRelativePath(File(segment.absolutePath))
        val unlockedFile = storageManager.unlockSegment(segment)
            ?: error(appContext.getString(R.string.recording_repository_locked_missing))
        emergencyEventStore.removeSegment(lockedDashcamPath)
        return unlockedFile
    }

    suspend fun deleteSegment(segment: RecordingSegment): RecordingStorageManager.DeleteResult {
        val dashcamPath = storageManager.dashcamRelativePath(File(segment.absolutePath))
        val result = storageManager.deleteSegment(segment)
        if (result.deleted) {
            emergencyEventStore.removeSegment(dashcamPath)
        }
        return result
    }

    suspend fun deleteEmergencyEvent(eventId: String?) {
        emergencyEventStore.deleteEvent(eventId)
    }

    suspend fun repairEmergencyEvents(): EmergencyEventRepairResult {
        storageManager.rebuildSegmentIndex()
        return emergencyEventStore.repairMissingSegments { path ->
            storageManager.dashcamFile(path)?.let { it.exists() && it.isFile } == true
        }
    }

    fun existingSegmentFiles(event: EmergencyEvent): List<File> {
        return event.segmentPaths
            .mapNotNull { storageManager.dashcamFile(it) }
            .filter { it.exists() && it.isFile }
    }
}
