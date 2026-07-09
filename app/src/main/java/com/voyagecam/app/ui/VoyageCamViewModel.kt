package com.voyagecam.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voyagecam.app.core.camera.CameraCapabilityDetector
import com.voyagecam.app.core.model.AutoStartDiagnostic
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.DualCameraSwitchState
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.PendingStorageCapacityChange
import com.voyagecam.app.core.model.PersistedDualCameraDiagnostic
import com.voyagecam.app.core.model.PersistedDualCameraSessionTelemetry
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
import com.voyagecam.app.feature.evidence.EvidenceExportCancelledException
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
    private val storageLimit = StorageCapacityLimit.from(appContext)
    private var evidenceExportCancelFlag: AtomicBoolean? = null

    private val initialSettings = settingsStore.load().coerceTo(storageLimit)
    private val initialCapability = settingsStore.loadCapability() ?: DualCameraCapability(
        state = DualCameraSwitchState.Checking,
        reason = "正在检测前后摄像头并发能力",
    )

    private val _uiState = MutableStateFlow(
        VoyageCamUiState(
            settings = initialSettings,
            capability = initialCapability,
            storageLimit = storageLimit,
            statusMessage = "已支持后摄单录到本地 MP4；双摄录制仍在下一阶段接入。",
            storageOverview = emptyStorageOverview(initialSettings, initialCapability),
        ),
    )
    val uiState: StateFlow<VoyageCamUiState> = _uiState

    init {
        refreshRecordingData()
        refreshAutoStartDiagnostic()
        refreshDualCameraDiagnostic()
        refreshDualCameraSessionTelemetry()
        redetect()
    }

    fun setRecordingStarted() {
        _uiState.update {
            it.copy(
                isRecording = true,
                statusMessage = "后摄录制服务已启动；预览与录制共用 CameraX 管线，每 ${it.settings.segmentDurationMinutes} 分钟分段。",
            )
        }
    }

    fun setRecordingStopped() {
        _uiState.update {
            it.copy(
                isRecording = false,
                statusMessage = "录制服务已停止。",
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
                reason = "正在检测前后摄像头并发能力",
            ),
        )
        viewModelScope.launch(Dispatchers.IO) {
            val currentSettings = _uiState.value.settings
            val result = detector.detect(currentSettings.dualCameraEnabled)
            withContext(Dispatchers.Main) {
                persistCapability(result)
                if (!result.isAvailable && _uiState.value.settings.dualCameraEnabled) {
                    persistSettings(_uiState.value.settings.copy(dualCameraEnabled = false))
                }
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
        _uiState.update { it.copy(dualCameraDiagnostic = dualCameraDiagnosticsStore.load()) }
    }

    fun clearDualCameraDiagnostic() {
        dualCameraDiagnosticsStore.clear()
        refreshDualCameraDiagnostic()
    }

    fun refreshDualCameraSessionTelemetry() {
        _uiState.update { it.copy(dualCameraSessionTelemetry = dualCameraSessionTelemetryStore.load()) }
    }

    fun clearDualCameraSessionTelemetry() {
        dualCameraSessionTelemetryStore.clear()
        refreshDualCameraSessionTelemetry()
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
        dualCameraSessionTelemetryStore.record(telemetry)
        refreshDualCameraSessionTelemetry()
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
                    statusMessage = "录像容量低于当前普通片段占用，请确认后再应用。",
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
            _uiState.update { it.copy(statusMessage = "已将录像容量调整为 ${capacityGb}GB。") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val cleanup = recordingRepository.cleanupNormalSegments(capacityGb)
            val state = _uiState.value
            val segments = recordingRepository.listSegments()
            val overview = recordingRepository.storageOverview(state.settings, state.capability)
            val message = if (cleanup.deletedFiles > 0) {
                "已将录像容量调整为 ${capacityGb}GB，并清理 ${cleanup.deletedFiles} 个普通片段、释放 ${cleanup.deletedBytes.asFileSize()}。"
            } else {
                "已将录像容量调整为 ${capacityGb}GB；当前普通片段无需清理。"
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
                "已清理 ${cleanup.deletedFiles} 个普通片段，释放 ${cleanup.deletedBytes.asFileSize()}；锁定片段未受影响。"
            } else {
                "当前普通片段未超过 ${settings.storageCapacityGb}GB 容量，无需清理。"
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
        setStatus("已恢复默认设置；录像、紧急事件和已导出的证据包均未删除。")
    }

    fun applyGpsMetadataSetting(enabled: Boolean) {
        _uiState.update { it.copy(pendingGpsMetadataEnable = false) }
        persistSettings(_uiState.value.settings.copy(gpsMetadataEnabled = enabled))
        setStatus(
            if (enabled) {
                "已开启GPS位置与轨迹记录；后续紧急事件会保存最近位置和轨迹。"
            } else {
                "已关闭GPS位置与轨迹记录；后续紧急事件不记录位置和轨迹。"
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
                    subtitle = primary.playbackSubtitle(secondary),
                    primaryLabel = primary.cameraDirection.label,
                    primaryFile = File(primary.absolutePath),
                    secondaryLabel = secondary?.cameraDirection?.label,
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
            setStatus("该片段已经是普通片段。")
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
                                statusMessage = "已解除锁定：${file.name}。该片段之后会按循环空间策略管理。",
                            )
                        }
                    }
                    .onFailure { error ->
                        setStatus("解除锁定失败：${error.message ?: segment.name}")
                    }
            }
        }
    }

    fun deleteSegment(segment: RecordingSegment) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val delete = recordingRepository.deleteSegment(segment)
                if (!delete.deleted) error("片段不存在或不在可管理目录内")
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
                                statusMessage = "已删除片段：${segment.name}，释放 ${delete.deletedBytes.asFileSize()}。",
                            )
                        }
                    }
                    .onFailure { error ->
                        setStatus("删除片段失败：${error.message ?: segment.name}")
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
                        evidenceExportState = it.evidenceExportState?.takeIf { export -> export.eventId != event.id },
                        statusMessage = "已删除紧急事件记录；关联录像仍保留在历史列表中。",
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
                "紧急事件关联片段正常，无需修复。"
            } else {
                "已修复 ${result.updatedEvents} 个紧急事件，移除 ${result.removedSegmentPaths} 条失效片段引用；${result.emptyEvents} 个事件当前没有可用片段。"
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
                    ?: error("关联片段文件不存在")
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
                                    subtitle = "${event.trigger.label} · ${event.triggeredAtMillis.asTime()}${if (secondary != null) " · 双摄事件" else ""}",
                                    primaryLabel = primary.name.cameraDirectionLabel(),
                                    primaryFile = primary,
                                    secondaryLabel = secondary?.name?.cameraDirectionLabel(),
                                    secondaryFile = secondary,
                                ),
                            )
                        }
                    }
                    .onFailure { error ->
                        setStatus("无法打开紧急事件片段：${error.message ?: event.trigger.label}")
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
                    ?: error("关联片段文件不存在")
            }
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun exportEmergencyEvent(event: EmergencyEvent) {
        if (_uiState.value.evidenceExportState is EvidenceExportState.Running) {
            setStatus("证据包正在导出，请稍候。")
            return
        }

        val cancelFlag = AtomicBoolean(false)
        evidenceExportCancelFlag = cancelFlag
        val title = "${event.trigger.label} · ${event.triggeredAtMillis.asTime()}"
        _uiState.update {
            it.copy(
                evidenceExportState = EvidenceExportState.Running(
                    eventId = event.id,
                    title = title,
                    currentItem = "准备导出",
                ),
                statusMessage = "正在导出紧急事件证据包...",
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
                                            eventId = event.id,
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
                                    eventId = event.id,
                                    file = packageFile.file,
                                    clipCount = packageFile.clipCount,
                                ),
                                statusMessage = "证据包已导出：${packageFile.file.name}",
                            )
                        }
                    }
                    .onFailure { error ->
                        if (error is EvidenceExportCancelledException) {
                            _uiState.update {
                                it.copy(
                                    evidenceExportState = EvidenceExportState.Cancelled(eventId = event.id),
                                    statusMessage = "证据包导出已取消，原始录像未受影响。",
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    evidenceExportState = EvidenceExportState.Failed(
                                        eventId = event.id,
                                        message = error.message ?: "导出失败",
                                    ),
                                    statusMessage = "证据包导出失败：${error.message ?: event.trigger.label}",
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
                    evidenceExportState = running.copy(currentItem = "正在取消..."),
                    statusMessage = "正在取消证据包导出...",
                )
            }
        }
    }

    fun dismissEvidenceExport() {
        _uiState.update { it.copy(evidenceExportState = null) }
    }

    fun setExportStatus(message: String) {
        setStatus(message)
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
    return REAR_VIDEO_BYTES_PER_MINUTE +
        if (settings.dualCameraEnabled && capability.isAvailable) FRONT_VIDEO_BYTES_PER_MINUTE else 0L +
        if (settings.ambientAudioEnabled) AUDIO_BYTES_PER_MINUTE else 0L
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
    val playbackItem: PlaybackItem? = null,
    val evidenceExportState: EvidenceExportState? = null,
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

private fun RecordingSegment.playbackSubtitle(secondary: RecordingSegment?): String {
    val lockState = if (locked) "已锁定" else "普通"
    val playbackMode = if (secondary != null) "双摄同步回放" else "单摄回放"
    return "${cameraDirection.label} · $lockState · $playbackMode"
}

private fun String.cameraDirectionLabel(): String {
    return if (contains("_front", ignoreCase = true)) CameraDirection.Front.label else CameraDirection.Rear.label
}

private fun String.cameraDirectionSortOrder(): Int {
    return if (contains("_rear", ignoreCase = true)) 0 else 1
}

private const val REAR_VIDEO_BYTES_PER_MINUTE = 90L * 1024L * 1024L
private const val FRONT_VIDEO_BYTES_PER_MINUTE = 55L * 1024L * 1024L
private const val AUDIO_BYTES_PER_MINUTE = 1L * 1024L * 1024L
