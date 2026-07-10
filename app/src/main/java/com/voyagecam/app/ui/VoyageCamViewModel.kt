package com.voyagecam.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voyagecam.app.R
import com.voyagecam.app.core.camera.CameraCapabilityDetector
import com.voyagecam.app.core.model.AutoStartDiagnostic
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.PersistedCrashReport
import com.voyagecam.app.core.model.PersistedDualCameraFailureArchive
import com.voyagecam.app.core.model.DualCameraSwitchState
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.PendingStorageCapacityChange
import com.voyagecam.app.core.model.PersistedDualCameraDiagnostic
import com.voyagecam.app.core.model.PersistedDualCameraSessionTelemetry
import com.voyagecam.app.core.model.PersistedStructuredLogEntry
import com.voyagecam.app.core.model.RecordingSegment
import com.voyagecam.app.core.model.RecordingStorageOverview
import com.voyagecam.app.core.model.toStorageBytes
import com.voyagecam.app.data.autostart.AutoStartDiagnosticsStore
import com.voyagecam.app.data.camera.DualCameraDiagnosticsStore
import com.voyagecam.app.data.camera.DualCameraSessionTelemetryStore
import com.voyagecam.app.data.evidence.EvidenceRepository
import com.voyagecam.app.data.recording.RecordingRepository
import com.voyagecam.app.data.settings.StorageCapacityLimit
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.data.settings.VoyageCamSettingsStore
import com.voyagecam.app.data.settings.coerceTo
import com.voyagecam.app.data.settings.estimatedManagedBytesPerMinute
import com.voyagecam.app.data.telemetry.RuntimeTelemetryStore
import com.voyagecam.app.feature.evidence.EvidenceExportCancelledException
import com.voyagecam.app.feature.evidence.RecordingClipExportMode
import com.voyagecam.app.ui.events.EvidenceExportState
import com.voyagecam.app.ui.history.SegmentCameraFilter
import com.voyagecam.app.ui.history.SegmentLockFilter
import com.voyagecam.app.ui.history.filterSegments
import com.voyagecam.app.ui.playback.PlaybackItem
import com.voyagecam.app.ui.preview.DualCameraTelemetryPresentation
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoyageCamViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val settingsStore = VoyageCamSettingsStore(appContext)
    private val detector = CameraCapabilityDetector(appContext)
    private val recordingRepository = RecordingRepository(appContext)
    private val evidenceRepository = EvidenceRepository(appContext)
    private val autoStartDiagnosticsStore = AutoStartDiagnosticsStore(appContext)
    private val dualCameraDiagnosticsStore = DualCameraDiagnosticsStore(appContext)
    private val dualCameraSessionTelemetryStore = DualCameraSessionTelemetryStore(appContext)
    private val runtimeTelemetryStore = RuntimeTelemetryStore(appContext)
    private val storageLimit = StorageCapacityLimit.from(appContext)
    private var evidenceExportCancelFlag: AtomicBoolean? = null
    private var clipExportCancelFlag: AtomicBoolean? = null

    private val initialSettings = settingsStore.load().coerceTo(storageLimit)
    private val initialCapability = settingsStore.loadCapability() ?: DualCameraCapability(
        state = DualCameraSwitchState.Checking,
        reason = appContext.getString(R.string.vm_initial_capability_reason),
    )

    private val _uiState = MutableStateFlow(
        VoyageCamUiState(
            settings = initialSettings,
            capability = initialCapability,
            storageLimit = storageLimit,
            statusMessage = appContext.getString(R.string.vm_initial_status),
            storageOverview = emptyStorageOverview(initialSettings, initialCapability),
        ),
    )
    val uiState: StateFlow<VoyageCamUiState> = _uiState

    init {
        refreshRecordingData()
        refreshAutoStartDiagnostic()
        refreshDualCameraDiagnostic()
        refreshDualCameraSessionTelemetry()
        refreshRuntimeTelemetry()
        redetect()
    }

    fun setRecordingStarted() {
        _uiState.update {
            it.copy(
                isRecording = true,
                statusMessage = appContext.getString(R.string.vm_recording_started, it.settings.segmentDurationMinutes),
            )
        }
    }

    fun setRecordingStopped() {
        _uiState.update {
            it.copy(
                isRecording = false,
                statusMessage = appContext.getString(R.string.vm_recording_stopped),
            )
        }
        refreshRecordingData()
    }

    fun setStatus(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    fun persistSettings(next: VoyageCamSettings) {
        val coerced = next.coerceTo(storageLimit)
        settingsStore.save(coerced)
        _uiState.update { it.copy(settings = coerced) }
        refreshStorageOverview()
    }

    fun persistCapability(next: DualCameraCapability) {
        settingsStore.saveCapability(next)
        _uiState.update { it.copy(capability = next) }
        refreshStorageOverview()
    }

    fun redetect() {
        persistCapability(
            DualCameraCapability(
                state = DualCameraSwitchState.Checking,
                reason = appContext.getString(R.string.vm_initial_capability_reason),
            ),
        )
        viewModelScope.launch(Dispatchers.IO) {
            val currentSettings = _uiState.value.settings
            val result = detector.detect(currentSettings.recordingMode == com.voyagecam.app.data.settings.RecordingMode.Auto)
            withContext(Dispatchers.Main) {
                persistCapability(result)
            }
        }
    }

    fun refreshRecordingData() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val segments = recordingRepository.listSegments()
            val events = recordingRepository.listEmergencyEvents()
            val overview = recordingRepository.storageOverview(state.settings, state.capability)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        allSegments = segments,
                        emergencyEvents = events,
                        storageOverview = overview,
                    )
                }
            }
        }
    }

    private fun refreshStorageOverview() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val overview = recordingRepository.storageOverview(state.settings, state.capability)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(storageOverview = overview) }
            }
        }
    }

    fun refreshAutoStartDiagnostic() {
        _uiState.update { it.copy(autoStartDiagnostic = autoStartDiagnosticsStore.load()) }
    }

    fun refreshDualCameraDiagnostic() {
        viewModelScope.launch(Dispatchers.IO) {
            val diagnostic = dualCameraDiagnosticsStore.load()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(dualCameraDiagnostic = diagnostic) }
            }
        }
    }

    fun clearDualCameraDiagnostic() {
        viewModelScope.launch(Dispatchers.IO) {
            dualCameraDiagnosticsStore.clear()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(dualCameraDiagnostic = null) }
            }
        }
    }

    fun refreshDualCameraSessionTelemetry() {
        viewModelScope.launch(Dispatchers.IO) {
            val telemetry = dualCameraSessionTelemetryStore.load()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(dualCameraSessionTelemetry = telemetry) }
            }
        }
    }

    fun clearDualCameraSessionTelemetry() {
        viewModelScope.launch(Dispatchers.IO) {
            dualCameraSessionTelemetryStore.clear()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(dualCameraSessionTelemetry = null) }
            }
        }
    }

    fun refreshRuntimeTelemetry() {
        viewModelScope.launch(Dispatchers.IO) {
            val latestCrashReport = runtimeTelemetryStore.latestCrashReport()
            val recentRuntimeLogs = runtimeTelemetryStore.recentLogs()
            val dualCameraFailureArchive = runtimeTelemetryStore.recentDualCameraFailures()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        latestCrashReport = latestCrashReport,
                        recentRuntimeLogs = recentRuntimeLogs,
                        dualCameraFailureArchive = dualCameraFailureArchive,
                    )
                }
            }
        }
    }

    fun clearRuntimeTelemetry() {
        viewModelScope.launch(Dispatchers.IO) {
            runtimeTelemetryStore.clearCrashReports()
            runtimeTelemetryStore.clearLogs()
            runtimeTelemetryStore.clearDualCameraFailures()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        latestCrashReport = null,
                        recentRuntimeLogs = emptyList(),
                        dualCameraFailureArchive = emptyList(),
                    )
                }
            }
        }
    }

    fun recordDualCameraSessionTelemetry(telemetry: DualCameraTelemetryPresentation) {
        val current = _uiState.value.dualCameraSessionTelemetry
        if (
            current?.summary == telemetry.summary &&
            current.detail == telemetry.detail &&
            current.diagnostic == telemetry.diagnostic
        ) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            dualCameraSessionTelemetryStore.record(telemetry)
            val persistedTelemetry = dualCameraSessionTelemetryStore.load()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(dualCameraSessionTelemetry = persistedTelemetry) }
            }
        }
    }

    fun requestStorageCapacityChange(capacityGb: Int) {
        val state = _uiState.value
        if (capacityGb == state.settings.storageCapacityGb) return
        val nextBytes = capacityGb.toStorageBytes()
        if (state.storageOverview.requiresCleanupConfirmation(capacityGb)) {
            _uiState.update {
                it.copy(
                    pendingStorageCapacityChange = PendingStorageCapacityChange(
                        nextCapacityGb = capacityGb,
                        currentNormalBytes = state.storageOverview.normalBytes,
                        overflowBytes = state.storageOverview.normalBytes - nextBytes,
                    ),
                    statusMessage = appContext.getString(R.string.vm_storage_change_needs_confirm),
                )
            }
        } else {
            applyStorageCapacityChange(capacityGb = capacityGb, cleanupNow = false)
        }
    }

    fun applyStorageCapacityChange(capacityGb: Int, cleanupNow: Boolean) {
        persistSettings(_uiState.value.settings.copy(storageCapacityGb = capacityGb))
        _uiState.update { it.copy(pendingStorageCapacityChange = null) }
        if (!cleanupNow) {
            _uiState.update { it.copy(statusMessage = appContext.getString(R.string.vm_storage_changed, capacityGb)) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val cleanup = recordingRepository.cleanupNormalSegments(capacityGb)
            val state = _uiState.value
            val segments = recordingRepository.listSegments()
            val overview = recordingRepository.storageOverview(state.settings, state.capability)
            val message = if (cleanup.deletedFiles > 0) {
                appContext.getString(
                    R.string.vm_storage_changed_cleanup,
                    capacityGb,
                    cleanup.deletedFiles,
                    cleanup.deletedBytes.asFileSize(),
                )
            } else {
                appContext.getString(R.string.vm_storage_changed_no_cleanup, capacityGb)
            }
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        allSegments = segments,
                        storageOverview = overview,
                        statusMessage = message,
                    )
                }
            }
        }
    }

    fun cleanupStorageNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = _uiState.value.settings
            val cleanup = recordingRepository.cleanupNormalSegments(settings.storageCapacityGb)
            val state = _uiState.value
            val segments = recordingRepository.listSegments()
            val overview = recordingRepository.storageOverview(state.settings, state.capability)
            val message = if (cleanup.deletedFiles > 0) {
                appContext.getString(
                    R.string.vm_storage_cleanup_done,
                    cleanup.deletedFiles,
                    cleanup.deletedBytes.asFileSize(),
                )
            } else {
                appContext.getString(R.string.vm_storage_cleanup_not_needed, settings.storageCapacityGb)
            }
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        allSegments = segments,
                        storageOverview = overview,
                        statusMessage = message,
                    )
                }
            }
        }
    }

    fun resetSettingsToDefaults() {
        _uiState.update {
            it.copy(
                pendingSettingsReset = false,
                pendingStorageCapacityChange = null,
                pendingGpsMetadataEnable = false,
            )
        }
        persistSettings(VoyageCamSettings())
        setStatus(appContext.getString(R.string.vm_defaults_restored))
    }

    fun applyGpsMetadataSetting(enabled: Boolean) {
        _uiState.update { it.copy(pendingGpsMetadataEnable = false) }
        persistSettings(_uiState.value.settings.copy(gpsMetadataEnabled = enabled))
        setStatus(
            if (enabled) {
                appContext.getString(R.string.vm_gps_enabled)
            } else {
                appContext.getString(R.string.vm_gps_disabled)
            },
        )
    }

    fun setPendingGpsMetadataEnable(enabled: Boolean) {
        _uiState.update { it.copy(pendingGpsMetadataEnable = enabled) }
    }

    fun setPendingSettingsReset(enabled: Boolean) {
        _uiState.update { it.copy(pendingSettingsReset = enabled) }
    }

    fun clearPendingStorageCapacityChange(message: String) {
        _uiState.update {
            it.copy(
                pendingStorageCapacityChange = null,
                statusMessage = message,
            )
        }
    }

    fun selectDay(day: String?) {
        _uiState.update { it.copy(selectedDay = day) }
    }

    fun selectCameraFilter(filter: SegmentCameraFilter) {
        _uiState.update { it.copy(selectedCameraFilter = filter) }
    }

    fun selectLockFilter(filter: SegmentLockFilter) {
        _uiState.update { it.copy(selectedLockFilter = filter) }
    }

    fun openSegment(segment: RecordingSegment) {
        val groupSegments = _uiState.value.allSegments
            .filter { it.groupKey == segment.groupKey }
        val rear = groupSegments.firstOrNull { it.cameraDirection == CameraDirection.Rear }
        val front = groupSegments.firstOrNull { it.cameraDirection == CameraDirection.Front }
        val primary = if (segment.cameraDirection == CameraDirection.Front) front ?: segment else rear ?: segment
        val secondary = when (primary.cameraDirection) {
            CameraDirection.Rear -> front
            CameraDirection.Front -> rear
        }

        _uiState.update {
            it.copy(
                playbackItem = PlaybackItem(
                    title = primary.name,
                    subtitle = primary.playbackSubtitle(appContext, secondary),
                    primaryLabel = appContext.getString(primary.cameraDirection.labelRes()),
                    primaryFile = File(primary.absolutePath),
                    secondaryLabel = secondary?.cameraDirection?.let { direction -> appContext.getString(direction.labelRes()) },
                    secondaryFile = secondary?.let { File(it.absolutePath) },
                ),
            )
        }
    }

    fun closePlayback() {
        _uiState.update { it.copy(playbackItem = null) }
    }

    fun setPendingSegmentDelete(segment: RecordingSegment?) {
        _uiState.update { it.copy(pendingSegmentDelete = segment) }
    }

    fun unlockSegment(segment: RecordingSegment) {
        if (!segment.locked) {
            setStatus(appContext.getString(R.string.vm_segment_already_normal))
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { recordingRepository.unlockSegment(segment) }
            val state = _uiState.value
            val segments = recordingRepository.listSegments()
            val events = recordingRepository.listEmergencyEvents()
            val overview = recordingRepository.storageOverview(state.settings, state.capability)
            withContext(Dispatchers.Main) {
                result
                    .onSuccess { file ->
                        _uiState.update {
                            it.copy(
                                allSegments = segments,
                                emergencyEvents = events,
                                storageOverview = overview,
                                statusMessage = appContext.getString(R.string.vm_segment_unlocked, file.name),
                            )
                        }
                    }
                    .onFailure { error ->
                        setStatus(appContext.getString(R.string.vm_segment_unlock_failed, error.message ?: segment.name))
                    }
            }
        }
    }

    fun deleteSegment(segment: RecordingSegment) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val delete = recordingRepository.deleteSegment(segment)
                if (!delete.deleted) error(appContext.getString(R.string.vm_segment_not_manageable))
                delete
            }
            val state = _uiState.value
            val segments = recordingRepository.listSegments()
            val events = recordingRepository.listEmergencyEvents()
            val overview = recordingRepository.storageOverview(state.settings, state.capability)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(pendingSegmentDelete = null) }
                result
                    .onSuccess { delete ->
                        _uiState.update {
                            it.copy(
                                allSegments = segments,
                                emergencyEvents = events,
                                storageOverview = overview,
                                playbackItem = it.playbackItem?.takeIf { item ->
                                    item.primaryFile.absolutePath != segment.absolutePath &&
                                        item.secondaryFile?.absolutePath != segment.absolutePath
                                },
                                statusMessage = appContext.getString(
                                    R.string.vm_segment_deleted,
                                    segment.name,
                                    delete.deletedBytes.asFileSize(),
                                ),
                            )
                        }
                    }
                    .onFailure { error ->
                        setStatus(appContext.getString(R.string.vm_segment_delete_failed, error.message ?: segment.name))
                    }
            }
        }
    }

    fun setPendingEmergencyEventDelete(event: EmergencyEvent?) {
        _uiState.update { it.copy(pendingEmergencyEventDelete = event) }
    }

    fun deleteEmergencyEvent(event: EmergencyEvent) {
        viewModelScope.launch(Dispatchers.IO) {
            recordingRepository.deleteEmergencyEvent(event.id)
            val events = recordingRepository.listEmergencyEvents()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        pendingEmergencyEventDelete = null,
                        emergencyEvents = events,
                        evidenceExportState = it.evidenceExportState?.takeIf { export -> export.exportId != event.id },
                        statusMessage = appContext.getString(R.string.vm_event_deleted),
                    )
                }
            }
        }
    }

    fun repairEmergencyEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = recordingRepository.repairEmergencyEvents()
            val events = recordingRepository.listEmergencyEvents()
            val message = if (result.removedSegmentPaths == 0) {
                appContext.getString(R.string.vm_events_repair_not_needed)
            } else {
                appContext.getString(
                    R.string.vm_events_repair_done,
                    result.updatedEvents,
                    result.removedSegmentPaths,
                    result.emptyEvents,
                )
            }
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        emergencyEvents = events,
                        statusMessage = message,
                    )
                }
            }
        }
    }

    fun openEmergencyEvent(event: EmergencyEvent) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                recordingRepository.existingSegmentFiles(event)
                    .sortedWith(compareBy<File> { it.name.cameraDirectionSortOrder() }.thenBy { it.name })
                    .takeIf { it.isNotEmpty() }
                    ?: error(appContext.getString(R.string.vm_event_files_missing))
            }
            withContext(Dispatchers.Main) {
                result
                    .onSuccess { files ->
                        val primary = files.first()
                        val secondary = files.drop(1).firstOrNull()
                        _uiState.update {
                            it.copy(
                                playbackItem = PlaybackItem(
                                    title = primary.name,
                                    subtitle = appContext.getString(
                                        R.string.vm_event_subtitle,
                                        appContext.getString(event.trigger.labelRes()),
                                        event.triggeredAtMillis.asTime(),
                                        if (secondary != null) appContext.getString(R.string.vm_event_dual_camera_suffix) else "",
                                    ),
                                    primaryLabel = primary.name.cameraDirectionLabel(appContext),
                                    primaryFile = primary,
                                    secondaryLabel = secondary?.name?.cameraDirectionLabel(appContext),
                                    secondaryFile = secondary,
                                ),
                            )
                        }
                    }
                    .onFailure { error ->
                        setStatus(
                            appContext.getString(
                                R.string.vm_event_open_failed,
                                error.message ?: appContext.getString(event.trigger.labelRes()),
                            ),
                        )
                    }
            }
        }
    }

    fun loadEmergencyEventFiles(
        event: EmergencyEvent,
        onResult: (Result<List<File>>) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                recordingRepository.existingSegmentFiles(event)
                    .takeIf { it.isNotEmpty() }
                    ?: error(appContext.getString(R.string.vm_event_files_missing))
            }
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun exportEmergencyEvent(event: EmergencyEvent) {
        if (hasRunningExport()) {
            setStatus(appContext.getString(R.string.vm_export_busy))
            return
        }

        val cancelFlag = AtomicBoolean(false)
        evidenceExportCancelFlag = cancelFlag
        val title = appContext.getString(
            R.string.vm_event_subtitle,
            appContext.getString(event.trigger.labelRes()),
            event.triggeredAtMillis.asTime(),
            "",
        )
        _uiState.update {
            it.copy(
                evidenceExportState = EvidenceExportState.Running(
                    exportId = event.id,
                    title = title,
                    currentItem = appContext.getString(R.string.vm_export_preparing),
                ),
                statusMessage = appContext.getString(R.string.vm_export_running_evidence),
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val settings = _uiState.value.settings
            val result = runCatching {
                evidenceRepository.export(
                    event = event,
                    settings = settings,
                    onProgress = { progress ->
                        viewModelScope.launch(Dispatchers.Main) {
                            if (evidenceExportCancelFlag === cancelFlag) {
                                _uiState.update {
                                    it.copy(
                                        evidenceExportState = EvidenceExportState.Running(
                                            exportId = event.id,
                                            title = title,
                                            progressPercent = progress.percent,
                                            currentItem = progress.currentItem,
                                        ),
                                    )
                                }
                            }
                        }
                    },
                    isCancelled = { cancelFlag.get() },
                )
            }

            withContext(Dispatchers.Main) {
                if (evidenceExportCancelFlag === cancelFlag) {
                    evidenceExportCancelFlag = null
                }
                result
                    .onSuccess { packageFile ->
                        _uiState.update {
                            it.copy(
                                evidenceExportState = EvidenceExportState.Ready(
                                    exportId = event.id,
                                    title = title,
                                    file = packageFile.file,
                                    itemCount = packageFile.clipCount,
                                ),
                                statusMessage = appContext.getString(R.string.vm_export_ready, packageFile.file.name),
                            )
                        }
                    }
                    .onFailure { error ->
                        if (error is EvidenceExportCancelledException) {
                            _uiState.update {
                                it.copy(
                                    evidenceExportState = EvidenceExportState.Cancelled(
                                        exportId = event.id,
                                        title = title,
                                        message = appContext.getString(R.string.vm_export_cancelled),
                                    ),
                                    statusMessage = appContext.getString(R.string.vm_export_cancelled),
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    evidenceExportState = EvidenceExportState.Failed(
                                        exportId = event.id,
                                        title = title,
                                        message = error.message ?: appContext.getString(R.string.vm_export_failed_default),
                                    ),
                                    statusMessage = appContext.getString(
                                        R.string.vm_export_failed,
                                        error.message ?: appContext.getString(event.trigger.labelRes()),
                                    ),
                                )
                            }
                        }
                    }
            }
        }
    }

    fun cancelEvidenceExport() {
        val running = _uiState.value.evidenceExportState as? EvidenceExportState.Running
        evidenceExportCancelFlag?.set(true)
        if (running != null) {
            _uiState.update {
                it.copy(
                    evidenceExportState = running.copy(currentItem = appContext.getString(R.string.vm_export_cancelling)),
                    statusMessage = appContext.getString(R.string.vm_export_cancelling_status),
                )
            }
        }
    }

    fun dismissEvidenceExport() {
        _uiState.update { it.copy(evidenceExportState = null) }
    }

    fun exportSegmentGroup(groupKey: String, mode: RecordingClipExportMode) {
        if (hasRunningExport()) {
            setStatus(appContext.getString(R.string.vm_export_busy))
            return
        }

        val groupSegments = currentGroupSegments(groupKey)
        if (groupSegments.isEmpty()) {
            setStatus(appContext.getString(R.string.history_export_group_missing))
            return
        }
        val rear = groupSegments.firstOrNull { it.cameraDirection == CameraDirection.Rear }
        val front = groupSegments.firstOrNull { it.cameraDirection == CameraDirection.Front }
        val title = buildGroupExportTitle(groupSegments)
        val cancelFlag = AtomicBoolean(false)
        clipExportCancelFlag = cancelFlag

        _uiState.update {
            it.copy(
                clipExportState = EvidenceExportState.Running(
                    exportId = groupKey,
                    title = title,
                    currentItem = appContext.getString(R.string.vm_export_preparing),
                ),
                statusMessage = appContext.getString(R.string.vm_export_running_clip),
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                evidenceRepository.exportClipGroup(
                    groupKey = groupKey,
                    rearFile = rear?.let { File(it.absolutePath) },
                    frontFile = front?.let { File(it.absolutePath) },
                    mode = mode,
                    onProgress = { progress ->
                        viewModelScope.launch(Dispatchers.Main) {
                            if (clipExportCancelFlag === cancelFlag) {
                                _uiState.update {
                                    it.copy(
                                        clipExportState = EvidenceExportState.Running(
                                            exportId = groupKey,
                                            title = title,
                                            progressPercent = progress.percent,
                                            currentItem = progress.currentItem,
                                        ),
                                    )
                                }
                            }
                        }
                    },
                    isCancelled = { cancelFlag.get() },
                )
            }

            withContext(Dispatchers.Main) {
                if (clipExportCancelFlag === cancelFlag) {
                    clipExportCancelFlag = null
                }
                result
                    .onSuccess { exportFile ->
                        _uiState.update {
                            it.copy(
                                clipExportState = EvidenceExportState.Ready(
                                    exportId = groupKey,
                                    title = title,
                                    file = exportFile.file,
                                    itemCount = exportFile.itemCount,
                                ),
                                statusMessage = appContext.getString(R.string.vm_export_ready, exportFile.file.name),
                            )
                        }
                    }
                    .onFailure { error ->
                        if (error is EvidenceExportCancelledException) {
                            _uiState.update {
                                it.copy(
                                    clipExportState = EvidenceExportState.Cancelled(
                                        exportId = groupKey,
                                        title = title,
                                        message = appContext.getString(R.string.vm_export_cancelled),
                                    ),
                                    statusMessage = appContext.getString(R.string.vm_export_cancelled),
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    clipExportState = EvidenceExportState.Failed(
                                        exportId = groupKey,
                                        title = title,
                                        message = error.message ?: appContext.getString(R.string.vm_export_failed_default),
                                    ),
                                    statusMessage = appContext.getString(
                                        R.string.vm_export_failed,
                                        error.message ?: title,
                                    ),
                                )
                            }
                        }
                    }
            }
        }
    }

    fun cancelClipExport() {
        val running = _uiState.value.clipExportState as? EvidenceExportState.Running
        clipExportCancelFlag?.set(true)
        if (running != null) {
            _uiState.update {
                it.copy(
                    clipExportState = running.copy(currentItem = appContext.getString(R.string.vm_export_cancelling)),
                    statusMessage = appContext.getString(R.string.vm_export_cancelling_status),
                )
            }
        }
    }

    fun dismissClipExport() {
        _uiState.update { it.copy(clipExportState = null) }
    }

    fun setExportStatus(message: String) {
        setStatus(message)
    }

    private fun currentGroupSegments(groupKey: String): List<RecordingSegment> {
        return _uiState.value.allSegments
            .filter { it.groupKey == groupKey }
            .sortedBy { it.cameraDirection != CameraDirection.Rear }
    }

    private fun hasRunningExport(): Boolean {
        return _uiState.value.evidenceExportState is EvidenceExportState.Running ||
            _uiState.value.clipExportState is EvidenceExportState.Running
    }

    private fun buildGroupExportTitle(groupSegments: List<RecordingSegment>): String {
        val rear = groupSegments.firstOrNull { it.cameraDirection == CameraDirection.Rear }
        val front = groupSegments.firstOrNull { it.cameraDirection == CameraDirection.Front }
        val anchor = rear ?: front ?: return appContext.getString(R.string.history_group_unknown)
        val modeLabel = when {
            rear != null && front != null -> appContext.getString(R.string.history_group_relation_dual)
            rear != null -> appContext.getString(R.string.history_group_relation_rear_only)
            else -> appContext.getString(R.string.history_group_relation_front_only)
        }
        return appContext.getString(
            R.string.history_group_export_title,
            anchor.lastModifiedMillis.asTime(),
            modeLabel,
        )
    }

    private fun emptyStorageOverview(
        settings: VoyageCamSettings,
        capability: DualCameraCapability,
    ): RecordingStorageOverview {
        return RecordingStorageOverview(
            normalBytes = 0L,
            lockedBytes = 0L,
            normalClipCount = 0,
            lockedClipCount = 0,
            maxStorageBytes = settings.storageCapacityGb.toStorageBytes(),
            estimatedBytesPerMinute = estimatedBytesPerMinute(settings, capability),
        )
    }
}

private fun estimatedBytesPerMinute(
    settings: VoyageCamSettings,
    capability: DualCameraCapability,
): Long {
    return settings.estimatedManagedBytesPerMinute(capability)
}

data class VoyageCamUiState(
    val settings: VoyageCamSettings,
    val capability: DualCameraCapability,
    val storageLimit: StorageCapacityLimit,
    val statusMessage: String,
    val isRecording: Boolean = false,
    val allSegments: List<RecordingSegment> = emptyList(),
    val storageOverview: RecordingStorageOverview,
    val emergencyEvents: List<EmergencyEvent> = emptyList(),
    val autoStartDiagnostic: AutoStartDiagnostic? = null,
    val dualCameraDiagnostic: PersistedDualCameraDiagnostic? = null,
    val dualCameraSessionTelemetry: PersistedDualCameraSessionTelemetry? = null,
    val latestCrashReport: PersistedCrashReport? = null,
    val recentRuntimeLogs: List<PersistedStructuredLogEntry> = emptyList(),
    val dualCameraFailureArchive: List<PersistedDualCameraFailureArchive> = emptyList(),
    val playbackItem: PlaybackItem? = null,
    val evidenceExportState: EvidenceExportState? = null,
    val clipExportState: EvidenceExportState? = null,
    val selectedDay: String? = null,
    val selectedCameraFilter: SegmentCameraFilter = SegmentCameraFilter.All,
    val selectedLockFilter: SegmentLockFilter = SegmentLockFilter.All,
    val pendingStorageCapacityChange: PendingStorageCapacityChange? = null,
    val pendingSegmentDelete: RecordingSegment? = null,
    val pendingEmergencyEventDelete: EmergencyEvent? = null,
    val pendingSettingsReset: Boolean = false,
    val pendingGpsMetadataEnable: Boolean = false,
) {
    val availableDays: List<String>
        get() = allSegments.map { it.day }.distinct()

    val filteredSegments: List<RecordingSegment>
        get() = allSegments.filterSegments(
            selectedDay = selectedDay,
            cameraFilter = selectedCameraFilter,
            lockFilter = selectedLockFilter,
        )
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

private fun RecordingSegment.playbackSubtitle(context: android.content.Context, secondary: RecordingSegment?): String {
    val lockState = context.getString(
        if (locked) {
            R.string.route_segment_status_locked
        } else {
            R.string.route_segment_status_normal
        },
    )
    val playbackMode = context.getString(
        if (secondary != null) {
            R.string.vm_playback_mode_dual
        } else {
            R.string.vm_playback_mode_single
        },
    )
    return context.getString(
        R.string.vm_playback_subtitle,
        context.getString(cameraDirection.labelRes()),
        lockState,
        playbackMode,
    )
}

private fun String.cameraDirectionLabel(context: android.content.Context): String {
    return context.getString(
        if (contains("_front", ignoreCase = true)) {
            CameraDirection.Front.labelRes()
        } else {
            CameraDirection.Rear.labelRes()
        },
    )
}

private fun String.cameraDirectionSortOrder(): Int {
    return if (contains("_rear", ignoreCase = true)) 0 else 1
}
