package com.voyagecam.app.data.recording

import android.content.Context
import com.voyagecam.app.core.model.EmergencyEventRepairResult
import com.voyagecam.app.core.model.StructuredLogLevel
import com.voyagecam.app.data.emergency.EmergencyEventStore
import com.voyagecam.app.data.storage.RecordingStorageManager
import com.voyagecam.app.data.telemetry.VoyageCamRuntimeTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object RecordingStartupRecovery {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile
    private var recoveredInProcess = false

    fun warmUp(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                ensureRecovered(appContext)
            }.onFailure { error ->
                VoyageCamRuntimeTelemetry.log(
                    level = StructuredLogLevel.Warn,
                    category = VoyageCamRuntimeTelemetry.CATEGORY_RECORDING,
                    event = "recording_startup_recovery_failed",
                    message = error.message ?: "Recording startup recovery failed",
                    throwable = error,
                )
            }
        }
    }

    suspend fun ensureRecovered(context: Context): RecoveryResult {
        return recover(context = context.applicationContext, force = false)
    }

    suspend fun repairNow(context: Context): RecoveryResult {
        return recover(context = context.applicationContext, force = true)
    }

    private suspend fun recover(context: Context, force: Boolean): RecoveryResult {
        if (!force && recoveredInProcess) {
            return RecoveryResult(performed = false)
        }

        return mutex.withLock {
            if (!force && recoveredInProcess) {
                return@withLock RecoveryResult(performed = false)
            }

            val storageManager = RecordingStorageManager(context)
            val emergencyEventStore = EmergencyEventStore(context)
            val rebuild = storageManager.rebuildSegmentIndex()
            val repair = emergencyEventStore.repairMissingSegments { path ->
                storageManager.dashcamFile(path)?.let { it.exists() && it.isFile } == true
            }

            recoveredInProcess = true
            logRecovery(rebuild = rebuild, repair = repair)

            RecoveryResult(
                performed = true,
                rebuildResult = rebuild,
                eventRepairResult = repair,
            )
        }
    }

    private fun logRecovery(
        rebuild: RecordingStorageManager.IndexRebuildResult,
        repair: EmergencyEventRepairResult,
    ) {
        val hasFixes = rebuild.quarantinedFiles > 0 ||
            rebuild.deletedFiles > 0 ||
            repair.removedSegmentPaths > 0
        VoyageCamRuntimeTelemetry.log(
            level = if (hasFixes) StructuredLogLevel.Warn else StructuredLogLevel.Info,
            category = VoyageCamRuntimeTelemetry.CATEGORY_RECORDING,
            event = "recording_startup_recovery_completed",
            message = if (hasFixes) {
                "Recovered recording index and isolated invalid clips"
            } else {
                "Recording index recovery completed without changes"
            },
            attributes = mapOf(
                "indexedSegments" to rebuild.indexedSegments.toString(),
                "quarantinedFiles" to rebuild.quarantinedFiles.toString(),
                "deletedFiles" to rebuild.deletedFiles.toString(),
                "updatedEvents" to repair.updatedEvents.toString(),
                "removedSegmentPaths" to repair.removedSegmentPaths.toString(),
                "emptyEvents" to repair.emptyEvents.toString(),
            ),
        )
    }

    data class RecoveryResult(
        val performed: Boolean,
        val rebuildResult: RecordingStorageManager.IndexRebuildResult = RecordingStorageManager.IndexRebuildResult(
            indexedSegments = 0,
            quarantinedFiles = 0,
            deletedFiles = 0,
        ),
        val eventRepairResult: EmergencyEventRepairResult = EmergencyEventRepairResult(
            updatedEvents = 0,
            removedSegmentPaths = 0,
            emptyEvents = 0,
        ),
    )
}
