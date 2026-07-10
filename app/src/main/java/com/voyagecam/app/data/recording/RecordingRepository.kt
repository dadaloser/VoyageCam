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

    suspend fun listSegments(): List<RecordingSegment> {
        RecordingStartupRecovery.ensureRecovered(appContext)
        return storageManager.listRecentSegments()
    }

    suspend fun listEmergencyEvents(): List<EmergencyEvent> {
        RecordingStartupRecovery.ensureRecovered(appContext)
        return emergencyEventStore.listRecentEvents()
    }

    suspend fun storageOverview(settings: VoyageCamSettings, capability: DualCameraCapability): RecordingStorageOverview {
        RecordingStartupRecovery.ensureRecovered(appContext)
        return storageManager.storageOverview(
            settings = settings,
            dualCameraActive = settings.resolveRecordingConfig(capability).dualCameraActive,
        )
    }

    suspend fun cleanupNormalSegments(maxStorageGb: Int): RecordingStorageManager.CleanupResult {
        RecordingStartupRecovery.ensureRecovered(appContext)
        return storageManager.cleanupNormalSegments(maxStorageGb)
    }

    suspend fun unlockSegment(segment: RecordingSegment): File {
        RecordingStartupRecovery.ensureRecovered(appContext)
        val lockedDashcamPath = storageManager.dashcamRelativePath(File(segment.absolutePath))
        val unlockedFile = storageManager.unlockSegment(segment)
            ?: error(appContext.getString(R.string.recording_repository_locked_missing))
        emergencyEventStore.removeSegment(lockedDashcamPath)
        return unlockedFile
    }

    suspend fun deleteSegment(segment: RecordingSegment): RecordingStorageManager.DeleteResult {
        RecordingStartupRecovery.ensureRecovered(appContext)
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
        return RecordingStartupRecovery.repairNow(appContext).eventRepairResult
    }

    fun existingSegmentFiles(event: EmergencyEvent): List<File> {
        return event.segmentPaths
            .mapNotNull { storageManager.dashcamFile(it) }
            .filter { it.exists() && it.isFile }
    }
}
