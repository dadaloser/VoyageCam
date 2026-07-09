package com.voyagecam.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voyagecam.app.core.camera.DualCameraSessionStatus
import com.voyagecam.app.core.camera.DualCameraSessionCoordinator
import com.voyagecam.app.core.model.DeviceCapabilityGrade
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.PendingStorageCapacityChange
import com.voyagecam.app.core.model.RecordingSegment
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.data.settings.recordingModeDescription
import com.voyagecam.app.data.settings.recordingModeLabel
import com.voyagecam.app.feature.recording.RecordingForegroundService
import com.voyagecam.app.ui.events.EmergencyEventPanel
import com.voyagecam.app.ui.history.SegmentHistoryPanel
import com.voyagecam.app.ui.playback.PlaybackPanel
import com.voyagecam.app.ui.playback.PlaybackItem
import com.voyagecam.app.ui.preview.RearCameraPreview
import com.voyagecam.app.ui.preview.DualCameraTelemetryPresentation
import com.voyagecam.app.ui.preview.dualCameraPreviewPresentation
import com.voyagecam.app.ui.preview.dualCameraTelemetryPresentation
import com.voyagecam.app.ui.settings.BluetoothDevicePickerState
import com.voyagecam.app.ui.settings.SettingsPanel
import com.voyagecam.app.ui.settings.rememberBluetoothDevicePickerState
import com.voyagecam.app.ui.theme.SectionCard
import com.voyagecam.app.ui.theme.VoyageCamTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

interface RecordingServiceController {
    fun start(context: Context, dualCamera: Boolean, ambientAudio: Boolean)
    fun stop(context: Context)
    fun lockCurrent(context: Context)
    fun setGpsMetadataEnabled(context: Context, enabled: Boolean)
}

private object ForegroundRecordingServiceController : RecordingServiceController {
    override fun start(context: Context, dualCamera: Boolean, ambientAudio: Boolean) {
        RecordingForegroundService.start(
            context = context,
            dualCamera = dualCamera,
            ambientAudio = ambientAudio,
        )
    }

    override fun stop(context: Context) {
        RecordingForegroundService.stop(context)
    }

    override fun lockCurrent(context: Context) {
        RecordingForegroundService.lockCurrent(context)
    }

    override fun setGpsMetadataEnabled(context: Context, enabled: Boolean) {
        RecordingForegroundService.setGpsMetadataEnabled(context, enabled)
    }
}

data class PermissionCoordinatorParams(
    val context: Context,
    val settings: VoyageCamSettings,
    val isRecording: Boolean,
    val pendingGpsMetadataEnable: Boolean,
    val onRedetect: () -> Unit,
    val onBeginRecording: () -> Unit,
    val onPersistSettings: (VoyageCamSettings) -> Unit,
    val onApplyGpsMetadataSetting: (Boolean) -> Unit,
    val onSetPendingGpsMetadataEnable: (Boolean) -> Unit,
    val onSetStatus: (String) -> Unit,
    val onBluetoothPermissionGranted: () -> Unit,
)

@Composable
private fun defaultPermissionCoordinatorFactory(
    params: PermissionCoordinatorParams,
): PermissionCoordinator {
    return rememberPermissionCoordinator(
        context = params.context,
        settings = params.settings,
        isRecording = params.isRecording,
        pendingGpsMetadataEnable = params.pendingGpsMetadataEnable,
        onRedetect = params.onRedetect,
        onBeginRecording = params.onBeginRecording,
        onPersistSettings = params.onPersistSettings,
        onApplyGpsMetadataSetting = params.onApplyGpsMetadataSetting,
        onSetPendingGpsMetadataEnable = params.onSetPendingGpsMetadataEnable,
        onSetStatus = params.onSetStatus,
        onBluetoothPermissionGranted = params.onBluetoothPermissionGranted,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoyageCamRoute(
    recordingServiceController: RecordingServiceController = ForegroundRecordingServiceController,
    viewModelProvider: @Composable () -> VoyageCamViewModel = { viewModel() },
    permissionCoordinatorFactory: @Composable (PermissionCoordinatorParams) -> PermissionCoordinator = { params ->
        defaultPermissionCoordinatorFactory(params)
    },
    bluetoothDevicePickerStateFactory: @Composable (Context, String) -> BluetoothDevicePickerState = { factoryContext, trustedDeviceInput ->
        rememberBluetoothDevicePickerState(
            context = factoryContext,
            trustedDeviceInput = trustedDeviceInput,
        )
    },
    routeContentOverride: (@Composable (
        uiState: VoyageCamUiState,
        permissionCoordinator: PermissionCoordinator,
        bluetoothDevicePickerState: BluetoothDevicePickerState,
        recordingServiceController: RecordingServiceController,
    ) -> Unit)? = null,
) {
    val context = LocalContext.current
    val viewModel: VoyageCamViewModel = viewModelProvider()
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    val capability = uiState.capability
    val isRecording = uiState.isRecording
    val statusMessage = uiState.statusMessage
    val allSegments = uiState.allSegments
    val emergencyEvents = uiState.emergencyEvents
    val availableDays = uiState.availableDays
    val filteredSegments = uiState.filteredSegments
    val bluetoothDevicePickerState = bluetoothDevicePickerStateFactory(
        context,
        settings.trustedBluetoothDevice,
    )
    val shareLauncher = rememberShareLauncher(
        context = context,
        onStatus = viewModel::setStatus,
    )

    fun beginRecordingService() {
        val startingMode = recordingModeLabel(
            recordingModeAuto = settings.dualCameraEnabled,
            dualCameraActive = settings.dualCameraEnabled && capability.isAvailable,
        )
        viewModel.setStatus("正在启动$startingMode...")
        recordingServiceController.start(
            context = context,
            dualCamera = settings.dualCameraEnabled && capability.isAvailable,
            ambientAudio = settings.ambientAudioEnabled,
        )
        viewModel.setRecordingStarted()
    }

    fun applyGpsMetadataSetting(enabled: Boolean) {
        viewModel.applyGpsMetadataSetting(enabled)
        if (isRecording) {
            recordingServiceController.setGpsMetadataEnabled(context, enabled)
        }
    }

    val permissionCoordinator = permissionCoordinatorFactory(
        PermissionCoordinatorParams(
            context = context,
            settings = settings,
            isRecording = isRecording,
            pendingGpsMetadataEnable = uiState.pendingGpsMetadataEnable,
            onRedetect = viewModel::redetect,
            onBeginRecording = ::beginRecordingService,
            onPersistSettings = viewModel::persistSettings,
            onApplyGpsMetadataSetting = ::applyGpsMetadataSetting,
            onSetPendingGpsMetadataEnable = viewModel::setPendingGpsMetadataEnable,
            onSetStatus = viewModel::setStatus,
            onBluetoothPermissionGranted = bluetoothDevicePickerState::refreshPairedDevices,
        ),
    )

    fun stopRecording() {
        recordingServiceController.stop(context)
        viewModel.setRecordingStopped()
    }

    fun shareEmergencyEvent(event: EmergencyEvent) {
        viewModel.loadEmergencyEventFiles(event) { result ->
            result
                .onSuccess { files ->
                    shareLauncher.shareEmergencyEventFiles(event, files)
                }
                .onFailure { error ->
                    viewModel.setStatus("无法分享紧急事件：${error.message ?: event.trigger.label}")
                }
        }
    }

    routeContentOverride?.invoke(
        uiState,
        permissionCoordinator,
        bluetoothDevicePickerState,
        recordingServiceController,
    ) ?: VoyageCamRouteContent(
        uiState = uiState,
        permissionCoordinator = permissionCoordinator,
        bluetoothDevicePickerState = bluetoothDevicePickerState,
        recordingServiceController = recordingServiceController,
        onSetStatus = viewModel::setStatus,
        onRecordDualCameraTelemetry = viewModel::recordDualCameraSessionTelemetry,
        onPersistSettings = viewModel::persistSettings,
        onRedetect = viewModel::redetect,
        onRequestStorageCapacityChange = viewModel::requestStorageCapacityChange,
        onCleanupStorageNow = viewModel::cleanupStorageNow,
        onApplyGpsMetadataSetting = ::applyGpsMetadataSetting,
        onRecordingStopped = viewModel::setRecordingStopped,
        onSetPendingGpsMetadataEnable = viewModel::setPendingGpsMetadataEnable,
        onSetPendingSettingsReset = viewModel::setPendingSettingsReset,
        onRefreshAutoStartDiagnostic = viewModel::refreshAutoStartDiagnostic,
        onRefreshDualCameraDiagnostic = viewModel::refreshDualCameraDiagnostic,
        onClearDualCameraDiagnostic = viewModel::clearDualCameraDiagnostic,
        onRefreshDualCameraSessionTelemetry = viewModel::refreshDualCameraSessionTelemetry,
        onClearDualCameraSessionTelemetry = viewModel::clearDualCameraSessionTelemetry,
        onResetSettingsToDefaults = viewModel::resetSettingsToDefaults,
        onApplyStorageCapacityChange = viewModel::applyStorageCapacityChange,
        onClearPendingStorageCapacityChange = viewModel::clearPendingStorageCapacityChange,
        onClosePlayback = viewModel::closePlayback,
        onOpenPlaybackInSystem = { item -> shareLauncher.openVideoFile(item.primaryFile) },
        onSetPendingEmergencyEventDelete = viewModel::setPendingEmergencyEventDelete,
        onDeleteEmergencyEvent = viewModel::deleteEmergencyEvent,
        onRefreshRecordingData = viewModel::refreshRecordingData,
        onRepairEmergencyEvents = viewModel::repairEmergencyEvents,
        onOpenEmergencyEvent = viewModel::openEmergencyEvent,
        onShareEmergencyEvent = ::shareEmergencyEvent,
        onExportEmergencyEvent = viewModel::exportEmergencyEvent,
        onCancelEvidenceExport = viewModel::cancelEvidenceExport,
        onShareEvidencePackage = shareLauncher::shareEvidencePackage,
        onDismissEvidenceExport = viewModel::dismissEvidenceExport,
        onOpenEmergencyEventMap = shareLauncher::openEmergencyEventMap,
        onSetPendingSegmentDelete = viewModel::setPendingSegmentDelete,
        onDeleteSegment = viewModel::deleteSegment,
        onSelectDay = viewModel::selectDay,
        onSelectCameraFilter = viewModel::selectCameraFilter,
        onSelectLockFilter = viewModel::selectLockFilter,
        onOpenSegment = viewModel::openSegment,
        onShareSegment = shareLauncher::shareSegment,
        onUnlockSegment = viewModel::unlockSegment,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoyageCamRouteContent(
    uiState: VoyageCamUiState,
    permissionCoordinator: PermissionCoordinator,
    bluetoothDevicePickerState: BluetoothDevicePickerState,
    recordingServiceController: RecordingServiceController,
    onSetStatus: (String) -> Unit = {},
    onRecordDualCameraTelemetry: (DualCameraTelemetryPresentation) -> Unit = {},
    onPersistSettings: (VoyageCamSettings) -> Unit = {},
    onRedetect: () -> Unit = {},
    onRequestStorageCapacityChange: (Int) -> Unit = {},
    onCleanupStorageNow: () -> Unit = {},
    onApplyGpsMetadataSetting: (Boolean) -> Unit = {},
    onRecordingStopped: () -> Unit = {},
    onSetPendingGpsMetadataEnable: (Boolean) -> Unit = {},
    onSetPendingSettingsReset: (Boolean) -> Unit = {},
    onRefreshAutoStartDiagnostic: () -> Unit = {},
    onRefreshDualCameraDiagnostic: () -> Unit = {},
    onClearDualCameraDiagnostic: () -> Unit = {},
    onRefreshDualCameraSessionTelemetry: () -> Unit = {},
    onClearDualCameraSessionTelemetry: () -> Unit = {},
    onResetSettingsToDefaults: () -> Unit = {},
    onApplyStorageCapacityChange: (Int, Boolean) -> Unit = { _, _ -> },
    onClearPendingStorageCapacityChange: (String) -> Unit = {},
    onClosePlayback: () -> Unit = {},
    onOpenPlaybackInSystem: (PlaybackItem) -> Unit = {},
    onSetPendingEmergencyEventDelete: (EmergencyEvent?) -> Unit = {},
    onDeleteEmergencyEvent: (EmergencyEvent) -> Unit = {},
    onRefreshRecordingData: () -> Unit = {},
    onRepairEmergencyEvents: () -> Unit = {},
    onOpenEmergencyEvent: (EmergencyEvent) -> Unit = {},
    onShareEmergencyEvent: (EmergencyEvent) -> Unit = {},
    onExportEmergencyEvent: (EmergencyEvent) -> Unit = {},
    onCancelEvidenceExport: () -> Unit = {},
    onShareEvidencePackage: (File) -> Unit = {},
    onDismissEvidenceExport: () -> Unit = {},
    onOpenEmergencyEventMap: (EmergencyEvent) -> Unit = {},
    onSetPendingSegmentDelete: (RecordingSegment?) -> Unit = {},
    onDeleteSegment: (RecordingSegment) -> Unit = {},
    onSelectDay: (String?) -> Unit = {},
    onSelectCameraFilter: (com.voyagecam.app.ui.history.SegmentCameraFilter) -> Unit = {},
    onSelectLockFilter: (com.voyagecam.app.ui.history.SegmentLockFilter) -> Unit = {},
    onOpenSegment: (RecordingSegment) -> Unit = {},
    onShareSegment: (RecordingSegment) -> Unit = {},
    onUnlockSegment: (RecordingSegment) -> Unit = {},
) {
    val settings = uiState.settings
    val capability = uiState.capability
    val isRecording = uiState.isRecording
    val statusMessage = uiState.statusMessage
    val allSegments = uiState.allSegments
    val emergencyEvents = uiState.emergencyEvents
    val availableDays = uiState.availableDays
    val filteredSegments = uiState.filteredSegments
    val context = LocalContext.current

    VoyageCamTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("VoyageCam") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFFF7FAF9),
                        titleContentColor = Color(0xFF163036),
                    ),
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF7FAF9))
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RecordingRoutePanel(
                    settings = settings,
                    capability = capability,
                    isRecording = isRecording,
                    statusMessage = statusMessage,
                    permissionCoordinator = permissionCoordinator,
                    recordingServiceController = recordingServiceController,
                    onRecordingStopped = onRecordingStopped,
                    onStatus = onSetStatus,
                    onRefreshRecordingData = onRefreshRecordingData,
                    onDualCameraTelemetry = onRecordDualCameraTelemetry,
                )

                SettingsPanel(
                    settings = settings,
                    capability = capability,
                    isRecording = isRecording,
                    storageLimit = uiState.storageLimit,
                    cameraPermissionGranted = permissionCoordinator.cameraPermissionGranted,
                    notificationPermissionGranted = permissionCoordinator.notificationPermissionGranted,
                    audioPermissionGranted = permissionCoordinator.audioPermissionGranted,
                    locationPermissionGranted = permissionCoordinator.locationPermissionGranted,
                    bluetoothPermissionGranted = permissionCoordinator.bluetoothPermissionGranted,
                    storageOverview = uiState.storageOverview,
                    pendingStorageCapacityGb = uiState.pendingStorageCapacityChange?.nextCapacityGb,
                    onRequestCameraPermission = permissionCoordinator.requestCameraPermission,
                    onRequestNotificationPermission = permissionCoordinator.requestNotificationPermission,
                    onRequestLocationPermission = permissionCoordinator.requestLocationPermission,
                    onRequestBluetoothPermission = permissionCoordinator.requestBluetoothPermission,
                    onRedetect = onRedetect,
                    onRecordingModeAutoChanged = { enabled ->
                        if (isRecording) {
                            onSetStatus("录制中不可切换录制模式；停止后修改会在下一次录制生效。")
                            return@SettingsPanel
                        }

                        onPersistSettings(settings.copy(dualCameraEnabled = enabled))
                        onSetStatus(
                            when {
                                enabled && capability.isAvailable ->
                                    "已切换为自动模式：支持时使用双摄；不支持或降级时自动切回后摄。"
                                enabled ->
                                    "已切换为自动模式：当前设备仅支持后摄录制。"
                                else ->
                                    "已切换为仅后摄：始终使用后摄录制。"
                            },
                        )
                    },
                    onRecordingResolutionChanged = { resolution ->
                        if (isRecording) {
                            onSetStatus("录制中不可修改分辨率；停止后修改会在下一次录制生效。")
                        } else {
                            onPersistSettings(settings.copy(recordingResolution = resolution))
                            onSetStatus("已将录制分辨率设置为 ${resolution.label}。")
                        }
                    },
                    onRecordingFrameRateChanged = { frameRate ->
                        if (isRecording) {
                            onSetStatus("录制中不可修改帧率；停止后修改会在下一次录制生效。")
                        } else {
                            onPersistSettings(settings.copy(recordingFrameRate = frameRate))
                            onSetStatus("已将录制帧率设置为 ${frameRate.label}。")
                        }
                    },
                    onRecordingBitrateChanged = { bitrate ->
                        if (isRecording) {
                            onSetStatus("录制中不可修改码率；停止后修改会在下一次录制生效。")
                        } else {
                            onPersistSettings(settings.copy(recordingBitrate = bitrate))
                            onSetStatus("已将录制码率设置为 ${bitrate.label}。")
                        }
                    },
                    onStorageChanged = onRequestStorageCapacityChange,
                    onCleanupStorage = onCleanupStorageNow,
                    onSegmentDurationChanged = { minutes ->
                        if (isRecording) {
                            onSetStatus("录制中不可修改分段时长；停止后修改会在下一次录制生效。")
                        } else {
                            onPersistSettings(settings.copy(segmentDurationMinutes = minutes))
                        }
                    },
                    onCollisionSensitivityChanged = { sensitivity ->
                        if (isRecording) {
                            onSetStatus("录制中不可修改碰撞检测灵敏度；停止后修改会在下一次录制生效。")
                        } else {
                            onPersistSettings(settings.copy(collisionSensitivity = sensitivity))
                        }
                    },
                    onAmbientAudioChanged = { enabled ->
                        if (isRecording) {
                            onSetStatus("录制中不可切换环境声；停止后修改会在下一次录制生效。")
                            return@SettingsPanel
                        }

                        if (!enabled) {
                            onPersistSettings(settings.copy(ambientAudioEnabled = false))
                            onSetStatus("已切换为静音录制；后续录制不写入音频轨道。")
                            return@SettingsPanel
                        }

                        if (permissionCoordinator.audioPermissionGranted) {
                            onPersistSettings(settings.copy(ambientAudioEnabled = true))
                            onSetStatus("行车环境声已开启；音频仅写入本地行车视频。")
                        } else {
                            permissionCoordinator.requestAudioPermission()
                        }
                    },
                    onThermalGuardChanged = { enabled ->
                        onPersistSettings(settings.copy(thermalGuardEnabled = enabled))
                        if (isRecording) {
                            RecordingForegroundService.refreshPerformanceGuard(context)
                        }
                        onSetStatus(
                            if (enabled) {
                                "已开启过热性能保护；设备严重过热时会自动关闭前摄，优先保持后摄录制。"
                            } else {
                                "已关闭过热性能保护；设备过热时不会自动关闭前摄。"
                            },
                        )
                    },
                    onLowBatteryGuardChanged = { enabled ->
                        onPersistSettings(settings.copy(lowBatteryGuardEnabled = enabled))
                        if (isRecording) {
                            RecordingForegroundService.refreshPerformanceGuard(context)
                        }
                        onSetStatus(
                            if (enabled) {
                                "已开启低电量性能保护；未充电且电量过低时会自动关闭前摄。"
                            } else {
                                "已关闭低电量性能保护；低电量时不会自动关闭前摄。"
                            },
                        )
                    },
                    onSlowSegmentGuardChanged = { enabled ->
                        onPersistSettings(settings.copy(slowSegmentGuardEnabled = enabled))
                        if (isRecording) {
                            RecordingForegroundService.refreshPerformanceGuard(context)
                        }
                        onSetStatus(
                            if (enabled) {
                                "已开启分段切换性能保护；分段间隙过长时会自动关闭前摄。"
                            } else {
                                "已关闭分段切换性能保护；分段压力较高时不会自动关闭前摄。"
                            },
                        )
                    },
                    onGpsMetadataChanged = { enabled ->
                        if (!enabled) {
                            onApplyGpsMetadataSetting(false)
                            return@SettingsPanel
                        }

                        if (permissionCoordinator.locationPermissionGranted) {
                            onApplyGpsMetadataSetting(true)
                        } else {
                            onSetPendingGpsMetadataEnable(true)
                            permissionCoordinator.requestLocationPermission()
                        }
                    },
                    onExportWatermarkSubtitlesChanged = { enabled ->
                        onPersistSettings(settings.copy(exportWatermarkSubtitlesEnabled = enabled))
                        onSetStatus(
                            if (enabled) {
                                "证据包导出会附带时间/速度水印字幕，方便在外部播放器预览。"
                            } else {
                                "已关闭导出水印字幕。"
                            },
                        )
                    },
                    onExportBurnedWatermarkVideoChanged = { enabled ->
                        onPersistSettings(settings.copy(exportBurnedWatermarkVideoEnabled = enabled))
                        onSetStatus(
                            if (enabled) {
                                "证据包导出会额外生成带烧录时间/速度/位置水印的视频副本；原始视频保持不变。"
                            } else {
                                "已关闭导出烧录水印视频副本；证据包会继续保留原始片段。"
                            },
                        )
                    },
                    onAutoStartOnPowerChanged = { enabled ->
                        onPersistSettings(settings.copy(autoStartOnPowerConnected = enabled))
                        onSetStatus(
                            if (enabled) {
                                "已开启连接充电器自动开始录制；需提前授权相机和通知权限。"
                            } else {
                                "已关闭连接充电器自动开始录制。"
                            },
                        )
                    },
                    onTrustedBluetoothDeviceChanged = { device ->
                        onPersistSettings(settings.copy(trustedBluetoothDevice = device))
                    },
                    onAutoStartOnTrustedBluetoothChanged = { enabled ->
                        if (enabled && settings.trustedBluetoothDevice.isBlank()) {
                            onSetStatus("请先填写可信蓝牙设备名称或 MAC 地址。")
                            return@SettingsPanel
                        }

                        onPersistSettings(settings.copy(autoStartOnTrustedBluetooth = enabled))
                        onSetStatus(
                            if (enabled) {
                                "已开启可信蓝牙连接自动开始录制；需提前授权相机、通知和蓝牙权限。"
                            } else {
                                "已关闭可信蓝牙连接自动开始录制。"
                            },
                        )
                    },
                    onRequestResetSettings = {
                        if (isRecording) {
                            onSetStatus("录制中不可恢复默认设置；停止后再操作。")
                        } else {
                            onSetPendingSettingsReset(true)
                        }
                    },
                    autoStartDiagnostic = uiState.autoStartDiagnostic,
                    dualCameraDiagnostic = uiState.dualCameraDiagnostic,
                    dualCameraSessionTelemetry = uiState.dualCameraSessionTelemetry,
                    onRefreshAutoStartDiagnostic = onRefreshAutoStartDiagnostic,
                    onRefreshDualCameraDiagnostic = onRefreshDualCameraDiagnostic,
                    onClearDualCameraDiagnostic = {
                        onClearDualCameraDiagnostic()
                        onSetStatus("已清空双摄降级诊断记录。")
                    },
                    onRefreshDualCameraSessionTelemetry = onRefreshDualCameraSessionTelemetry,
                    onClearDualCameraSessionTelemetry = {
                        onClearDualCameraSessionTelemetry()
                        onSetStatus("已清空双摄会话状态记录。")
                    },
                    bluetoothDevicePickerState = bluetoothDevicePickerState,
                )

                if (uiState.pendingSettingsReset) {
                    SettingsResetConfirmationPanel(
                        onConfirm = onResetSettingsToDefaults,
                        onCancel = {
                            onSetPendingSettingsReset(false)
                            onSetStatus("已取消恢复默认设置。")
                        },
                    )
                }

                uiState.pendingStorageCapacityChange?.let { pending ->
                    StorageCapacityConfirmationPanel(
                        pending = pending,
                        onConfirm = {
                            onApplyStorageCapacityChange(
                                pending.nextCapacityGb,
                                true,
                            )
                        },
                        onCancel = {
                            onClearPendingStorageCapacityChange("已取消调整录像容量。")
                        },
                    )
                }

                uiState.playbackItem?.let { item ->
                    PlaybackPanel(
                        item = item,
                        onClose = onClosePlayback,
                        onOpenInSystem = {
                            onOpenPlaybackInSystem(item)
                        },
                    )
                }

                uiState.pendingEmergencyEventDelete?.let { event ->
                    EmergencyEventDeleteConfirmationPanel(
                        event = event,
                        onConfirm = {
                            onDeleteEmergencyEvent(event)
                        },
                        onCancel = {
                            onSetPendingEmergencyEventDelete(null)
                            onSetStatus("已取消删除紧急事件。")
                        },
                    )
                }

                EmergencyEventPanel(
                    events = emergencyEvents,
                    exportState = uiState.evidenceExportState,
                    onRefresh = onRefreshRecordingData,
                    onRepairMissingSegments = onRepairEmergencyEvents,
                    onOpen = onOpenEmergencyEvent,
                    onShare = onShareEmergencyEvent,
                    onExport = onExportEmergencyEvent,
                    onCancelExport = onCancelEvidenceExport,
                    onShareExport = onShareEvidencePackage,
                    onDismissExport = onDismissEvidenceExport,
                    onOpenMap = onOpenEmergencyEventMap,
                    onDelete = onSetPendingEmergencyEventDelete,
                )

                uiState.pendingSegmentDelete?.let { segment ->
                    SegmentDeleteConfirmationPanel(
                        segment = segment,
                        onConfirm = {
                            onDeleteSegment(segment)
                        },
                        onCancel = {
                            onSetPendingSegmentDelete(null)
                            onSetStatus("已取消删除录像片段。")
                        },
                    )
                }

                SegmentHistoryPanel(
                    segments = filteredSegments,
                    allSegments = allSegments,
                    totalSegmentCount = allSegments.size,
                    availableDays = availableDays,
                    selectedDay = uiState.selectedDay,
                    selectedCameraFilter = uiState.selectedCameraFilter,
                    selectedLockFilter = uiState.selectedLockFilter,
                    onSelectedDayChanged = onSelectDay,
                    onCameraFilterChanged = onSelectCameraFilter,
                    onLockFilterChanged = onSelectLockFilter,
                    onRefresh = onRefreshRecordingData,
                    onOpen = onOpenSegment,
                    onShare = onShareSegment,
                    onUnlock = onUnlockSegment,
                    onDelete = onSetPendingSegmentDelete,
                )
            }
        }
    }
}

@Composable
internal fun RecordingRoutePanel(
    settings: VoyageCamSettings,
    capability: DualCameraCapability,
    isRecording: Boolean,
    statusMessage: String,
    permissionCoordinator: PermissionCoordinator,
    recordingServiceController: RecordingServiceController,
    onRecordingStopped: () -> Unit,
    onStatus: (String) -> Unit,
    onRefreshRecordingData: () -> Unit,
    onDualCameraTelemetry: (com.voyagecam.app.ui.preview.DualCameraTelemetryPresentation) -> Unit,
    dualCameraSessionStatusFlow: StateFlow<DualCameraSessionStatus> = DualCameraSessionCoordinator.sessionStatus,
    previewContent: @Composable (frontInsetEnabled: Boolean, dualCameraSessionToken: Int) -> Unit = { frontInsetEnabled, dualCameraSessionToken ->
        RearCameraPreview(
            enabled = true,
            frontInsetEnabled = frontInsetEnabled,
            dualCameraSessionToken = dualCameraSessionToken,
        )
    },
) {
    val context = LocalContext.current

    fun stopRecording() {
        recordingServiceController.stop(context)
        onRecordingStopped()
    }

    fun requestEmergencyLock() {
        recordingServiceController.lockCurrent(context)
        onStatus("紧急锁定已发送：当前片段、上一片段和下一片段会被保护。")
        onRefreshRecordingData()
    }

    RecordingPanel(
        settings = settings,
        capability = capability,
        isRecording = isRecording,
        statusMessage = statusMessage,
        onToggleRecording = {
            if (isRecording) stopRecording() else permissionCoordinator.requestStartRecording()
        },
        onEmergencyLock = ::requestEmergencyLock,
        onDualCameraTelemetry = onDualCameraTelemetry,
        dualCameraSessionStatusFlow = dualCameraSessionStatusFlow,
        previewContent = previewContent,
    )
}

@Composable
internal fun RecordingPanel(
    settings: VoyageCamSettings,
    capability: DualCameraCapability,
    isRecording: Boolean,
    statusMessage: String,
    onToggleRecording: () -> Unit,
    onEmergencyLock: () -> Unit,
    onDualCameraTelemetry: (com.voyagecam.app.ui.preview.DualCameraTelemetryPresentation) -> Unit,
    dualCameraSessionStatusFlow: StateFlow<DualCameraSessionStatus> = DualCameraSessionCoordinator.sessionStatus,
    previewContent: @Composable (frontInsetEnabled: Boolean, dualCameraSessionToken: Int) -> Unit = { frontInsetEnabled, dualCameraSessionToken ->
        RearCameraPreview(
            enabled = true,
            frontInsetEnabled = frontInsetEnabled,
            dualCameraSessionToken = dualCameraSessionToken,
        )
    },
) {
    val dualPreviewPresentation = dualCameraPreviewPresentation(
        dualCameraEnabled = settings.dualCameraEnabled,
        capability = capability,
        isRecording = isRecording,
    )
    val dualCameraSessionStatus by dualCameraSessionStatusFlow.collectAsState()
    val dualCameraTelemetry = dualCameraTelemetryPresentation(
        frontInsetEnabled = dualPreviewPresentation.showFrontInset,
        sessionToken = dualPreviewPresentation.sessionToken,
        sessionStatus = dualCameraSessionStatus,
    )
    LaunchedEffect(dualCameraTelemetry?.summary, dualCameraTelemetry?.detail, dualCameraTelemetry?.diagnostic) {
        dualCameraTelemetry?.let(onDualCameraTelemetry)
    }
    SectionCard {
        previewContent(
            dualPreviewPresentation.showFrontInset,
            dualPreviewPresentation.sessionToken,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (isRecording) "正在录制" else "准备录制",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = buildString {
                append(
                    recordingModeLabel(
                        recordingModeAuto = settings.dualCameraEnabled,
                        dualCameraActive = settings.dualCameraEnabled && capability.isAvailable,
                    ),
                )
                append(" · ")
                append(settings.recordingResolution.label)
                append(" / ")
                append(settings.recordingFrameRate.label)
                append(" / ")
                append(settings.recordingBitrate.label)
                append(" · ")
                append("${settings.segmentDurationMinutes}分钟分段")
                append(" · ")
                append("${settings.storageCapacityGb}GB 循环空间")
                append(" · ")
                append("碰撞${settings.collisionSensitivity.label}")
                append(" · ")
                append(if (settings.ambientAudioEnabled) "环境声开启" else "静音录制")
            },
            color = Color(0xFF4D6267),
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onToggleRecording,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("recording_toggle_button"),
        ) {
            Text(if (isRecording) "停止录制" else "开始录制")
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onEmergencyLock,
            enabled = isRecording,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("紧急锁定")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
        dualCameraTelemetry?.let { telemetry ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = telemetry.summary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (telemetry.diagnostic == null) Color(0xFF1F6F78) else Color(0xFF9B2C2C),
                modifier = Modifier.testTag("dual_camera_telemetry_summary"),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = telemetry.detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
                modifier = Modifier.testTag("dual_camera_telemetry_detail"),
            )
            telemetry.diagnostic?.let { diagnostic ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = diagnostic,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9B2C2C),
                )
            }
        }
    }
}

@Composable
private fun SettingsResetConfirmationPanel(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    SectionCard {
        Text(
            text = "确认恢复默认设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "将恢复默认录制模式、分辨率、帧率、码率、存储、音频、性能保护、GPS、水印导出和自动启动设置。录像片段、锁定事件和已导出的证据包不会被删除。",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4D6267),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            ) {
                Text("取消")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            ) {
                Text("恢复默认")
            }
        }
    }
}

@Composable
private fun StorageCapacityConfirmationPanel(
    pending: PendingStorageCapacityChange,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    SectionCard {
        Text(
            text = "确认调整录像容量",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "目标容量 ${pending.nextCapacityGb}GB 低于当前普通片段占用 ${pending.currentNormalBytes.asFileSize()}。确认后会立即清理最旧的普通片段，锁定片段不会被删除。",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4D6267),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "预计至少需要释放 ${pending.overflowBytes.asFileSize()}。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9B2C2C),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            ) {
                Text("取消")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            ) {
                Text("确认清理")
            }
        }
    }
}

@Composable
private fun EmergencyEventDeleteConfirmationPanel(
    event: EmergencyEvent,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    SectionCard {
        Text(
            text = "确认删除紧急事件",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${event.trigger.label} · ${event.triggeredAtMillis.asTime()} · ${event.segmentPaths.size} 段关联录像",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4D6267),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "这只会删除事件记录和取证元数据，不会删除关联录像文件。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
        Spacer(modifier = Modifier.height(12.dp))
        ConfirmationButtonRow(
            confirmLabel = "删除事件",
            onConfirm = onConfirm,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun SegmentDeleteConfirmationPanel(
    segment: RecordingSegment,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    SectionCard {
        Text(
            text = "确认删除录像片段",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${segment.cameraDirection.label} · ${if (segment.locked) "已锁定" else "普通"} · ${segment.sizeBytes.asFileSize()}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4D6267),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "确认后会删除本地视频文件，并从紧急事件记录中移除该片段引用。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9B2C2C),
        )
        Spacer(modifier = Modifier.height(12.dp))
        ConfirmationButtonRow(
            confirmLabel = "删除片段",
            onConfirm = onConfirm,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun ConfirmationButtonRow(
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        ) {
            Text("取消")
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
        ) {
            Text(confirmLabel)
        }
    }
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
