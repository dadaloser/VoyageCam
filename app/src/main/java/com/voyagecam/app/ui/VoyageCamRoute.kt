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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voyagecam.app.core.camera.DualCameraSessionCoordinator
import com.voyagecam.app.core.model.DeviceCapabilityGrade
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.PendingStorageCapacityChange
import com.voyagecam.app.core.model.RecordingSegment
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.feature.recording.RecordingForegroundService
import com.voyagecam.app.ui.events.EmergencyEventPanel
import com.voyagecam.app.ui.history.SegmentHistoryPanel
import com.voyagecam.app.ui.playback.PlaybackPanel
import com.voyagecam.app.ui.preview.RearCameraPreview
import com.voyagecam.app.ui.preview.dualCameraPreviewPresentation
import com.voyagecam.app.ui.preview.dualCameraTelemetryPresentation
import com.voyagecam.app.ui.settings.SettingsPanel
import com.voyagecam.app.ui.settings.rememberBluetoothDevicePickerState
import com.voyagecam.app.ui.theme.SectionCard
import com.voyagecam.app.ui.theme.VoyageCamTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoyageCamRoute() {
    val context = LocalContext.current
    val viewModel: VoyageCamViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    val capability = uiState.capability
    val isRecording = uiState.isRecording
    val statusMessage = uiState.statusMessage
    val allSegments = uiState.allSegments
    val emergencyEvents = uiState.emergencyEvents
    val availableDays = uiState.availableDays
    val filteredSegments = uiState.filteredSegments
    val bluetoothDevicePickerState = rememberBluetoothDevicePickerState(
        context = context,
        trustedDeviceInput = settings.trustedBluetoothDevice,
    )
    val shareLauncher = rememberShareLauncher(
        context = context,
        onStatus = viewModel::setStatus,
    )

    fun beginRecordingService() {
        viewModel.setStatus("正在启动后摄录制...")
        RecordingForegroundService.start(
            context = context,
            dualCamera = settings.dualCameraEnabled && capability.isAvailable,
            ambientAudio = settings.ambientAudioEnabled,
        )
        viewModel.setRecordingStarted()
    }

    fun applyGpsMetadataSetting(enabled: Boolean) {
        viewModel.applyGpsMetadataSetting(enabled)
        if (isRecording) {
            RecordingForegroundService.setGpsMetadataEnabled(context, enabled)
        }
    }

    val permissionCoordinator = rememberPermissionCoordinator(
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
    )

    fun stopRecording() {
        RecordingForegroundService.stop(context)
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
                RecordingPanel(
                    settings = settings,
                    capability = capability,
                    isRecording = isRecording,
                    statusMessage = statusMessage,
                    onToggleRecording = {
                        if (isRecording) stopRecording() else permissionCoordinator.requestStartRecording()
                    },
                    onEmergencyLock = {
                        RecordingForegroundService.lockCurrent(context)
                        viewModel.setStatus("紧急锁定已发送：当前片段、上一片段和下一片段会被保护。")
                        viewModel.refreshRecordingData()
                    },
                    onDualCameraTelemetry = viewModel::recordDualCameraSessionTelemetry,
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
                    onRedetect = viewModel::redetect,
                    onDualCameraChanged = { enabled ->
                        if (!isRecording) {
                            viewModel.persistSettings(settings.copy(dualCameraEnabled = enabled))
                            viewModel.redetect()
                        } else {
                            viewModel.setStatus("录制中不可切换双摄；停止后修改会在下一次录制生效。")
                        }
                    },
                    onStorageChanged = viewModel::requestStorageCapacityChange,
                    onCleanupStorage = viewModel::cleanupStorageNow,
                    onSegmentDurationChanged = { minutes ->
                        if (isRecording) {
                            viewModel.setStatus("录制中不可修改分段时长；停止后修改会在下一次录制生效。")
                        } else {
                            viewModel.persistSettings(settings.copy(segmentDurationMinutes = minutes))
                        }
                    },
                    onCollisionSensitivityChanged = { sensitivity ->
                        if (isRecording) {
                            viewModel.setStatus("录制中不可修改碰撞检测灵敏度；停止后修改会在下一次录制生效。")
                        } else {
                            viewModel.persistSettings(settings.copy(collisionSensitivity = sensitivity))
                        }
                    },
                    onAmbientAudioChanged = { enabled ->
                        if (isRecording) {
                            viewModel.setStatus("录制中不可切换环境声；停止后修改会在下一次录制生效。")
                            return@SettingsPanel
                        }

                        if (!enabled) {
                            viewModel.persistSettings(settings.copy(ambientAudioEnabled = false))
                            viewModel.setStatus("已切换为静音录制；后续录制不写入音频轨道。")
                            return@SettingsPanel
                        }

                        if (permissionCoordinator.audioPermissionGranted) {
                            viewModel.persistSettings(settings.copy(ambientAudioEnabled = true))
                            viewModel.setStatus("行车环境声已开启；音频仅写入本地行车视频。")
                        } else {
                            permissionCoordinator.requestAudioPermission()
                        }
                    },
                    onGpsMetadataChanged = { enabled ->
                        if (!enabled) {
                            applyGpsMetadataSetting(false)
                            return@SettingsPanel
                        }

                        if (permissionCoordinator.locationPermissionGranted) {
                            applyGpsMetadataSetting(true)
                        } else {
                            viewModel.setPendingGpsMetadataEnable(true)
                            permissionCoordinator.requestLocationPermission()
                        }
                    },
                    onExportWatermarkSubtitlesChanged = { enabled ->
                        viewModel.persistSettings(settings.copy(exportWatermarkSubtitlesEnabled = enabled))
                        viewModel.setStatus(
                            if (enabled) {
                                "证据包导出会附带时间/速度水印字幕；原始视频保持不变。"
                            } else {
                                "已关闭导出水印字幕；证据包只包含元数据、轨迹和原始片段。"
                            },
                        )
                    },
                    onAutoStartOnPowerChanged = { enabled ->
                        viewModel.persistSettings(settings.copy(autoStartOnPowerConnected = enabled))
                        viewModel.setStatus(
                            if (enabled) {
                                "已开启连接充电器自动开始录制；需提前授权相机和通知权限。"
                            } else {
                                "已关闭连接充电器自动开始录制。"
                            },
                        )
                    },
                    onTrustedBluetoothDeviceChanged = { device ->
                        viewModel.persistSettings(settings.copy(trustedBluetoothDevice = device))
                    },
                    onAutoStartOnTrustedBluetoothChanged = { enabled ->
                        if (enabled && settings.trustedBluetoothDevice.isBlank()) {
                            viewModel.setStatus("请先填写可信蓝牙设备名称或 MAC 地址。")
                            return@SettingsPanel
                        }

                        viewModel.persistSettings(settings.copy(autoStartOnTrustedBluetooth = enabled))
                        viewModel.setStatus(
                            if (enabled) {
                                "已开启可信蓝牙连接自动开始录制；需提前授权相机、通知和蓝牙权限。"
                            } else {
                                "已关闭可信蓝牙连接自动开始录制。"
                            },
                        )
                    },
                    onRequestResetSettings = {
                        if (isRecording) {
                            viewModel.setStatus("录制中不可恢复默认设置；停止后再操作。")
                        } else {
                            viewModel.setPendingSettingsReset(true)
                        }
                    },
                    autoStartDiagnostic = uiState.autoStartDiagnostic,
                    dualCameraDiagnostic = uiState.dualCameraDiagnostic,
                    dualCameraSessionTelemetry = uiState.dualCameraSessionTelemetry,
                    onRefreshAutoStartDiagnostic = viewModel::refreshAutoStartDiagnostic,
                    onRefreshDualCameraDiagnostic = viewModel::refreshDualCameraDiagnostic,
                    onRefreshDualCameraSessionTelemetry = viewModel::refreshDualCameraSessionTelemetry,
                    bluetoothDevicePickerState = bluetoothDevicePickerState,
                )

                if (uiState.pendingSettingsReset) {
                    SettingsResetConfirmationPanel(
                        onConfirm = viewModel::resetSettingsToDefaults,
                        onCancel = {
                            viewModel.setPendingSettingsReset(false)
                            viewModel.setStatus("已取消恢复默认设置。")
                        },
                    )
                }

                uiState.pendingStorageCapacityChange?.let { pending ->
                    StorageCapacityConfirmationPanel(
                        pending = pending,
                        onConfirm = {
                            viewModel.applyStorageCapacityChange(
                                capacityGb = pending.nextCapacityGb,
                                cleanupNow = true,
                            )
                        },
                        onCancel = {
                            viewModel.clearPendingStorageCapacityChange("已取消调整录像容量。")
                        },
                    )
                }

                uiState.playbackItem?.let { item ->
                    PlaybackPanel(
                        item = item,
                        onClose = viewModel::closePlayback,
                        onOpenInSystem = {
                            shareLauncher.openVideoFile(item.primaryFile)
                        },
                    )
                }

                uiState.pendingEmergencyEventDelete?.let { event ->
                    EmergencyEventDeleteConfirmationPanel(
                        event = event,
                        onConfirm = {
                            viewModel.deleteEmergencyEvent(event)
                        },
                        onCancel = {
                            viewModel.setPendingEmergencyEventDelete(null)
                            viewModel.setStatus("已取消删除紧急事件。")
                        },
                    )
                }

                EmergencyEventPanel(
                    events = emergencyEvents,
                    exportState = uiState.evidenceExportState,
                    onRefresh = viewModel::refreshRecordingData,
                    onRepairMissingSegments = viewModel::repairEmergencyEvents,
                    onOpen = viewModel::openEmergencyEvent,
                    onShare = ::shareEmergencyEvent,
                    onExport = viewModel::exportEmergencyEvent,
                    onCancelExport = viewModel::cancelEvidenceExport,
                    onShareExport = shareLauncher::shareEvidencePackage,
                    onDismissExport = viewModel::dismissEvidenceExport,
                    onOpenMap = shareLauncher::openEmergencyEventMap,
                    onDelete = viewModel::setPendingEmergencyEventDelete,
                )

                uiState.pendingSegmentDelete?.let { segment ->
                    SegmentDeleteConfirmationPanel(
                        segment = segment,
                        onConfirm = {
                            viewModel.deleteSegment(segment)
                        },
                        onCancel = {
                            viewModel.setPendingSegmentDelete(null)
                            viewModel.setStatus("已取消删除录像片段。")
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
                    onSelectedDayChanged = viewModel::selectDay,
                    onCameraFilterChanged = viewModel::selectCameraFilter,
                    onLockFilterChanged = viewModel::selectLockFilter,
                    onRefresh = viewModel::refreshRecordingData,
                    onOpen = viewModel::openSegment,
                    onShare = shareLauncher::shareSegment,
                    onUnlock = viewModel::unlockSegment,
                    onDelete = viewModel::setPendingSegmentDelete,
                )
            }
        }
    }
}

@Composable
private fun RecordingPanel(
    settings: VoyageCamSettings,
    capability: DualCameraCapability,
    isRecording: Boolean,
    statusMessage: String,
    onToggleRecording: () -> Unit,
    onEmergencyLock: () -> Unit,
    onDualCameraTelemetry: (com.voyagecam.app.ui.preview.DualCameraTelemetryPresentation) -> Unit,
) {
    val dualPreviewPresentation = dualCameraPreviewPresentation(
        dualCameraEnabled = settings.dualCameraEnabled,
        capability = capability,
        isRecording = isRecording,
    )
    val dualCameraSessionStatus by DualCameraSessionCoordinator.sessionStatus.collectAsState()
    val dualCameraTelemetry = dualCameraTelemetryPresentation(
        frontInsetEnabled = dualPreviewPresentation.showFrontInset,
        sessionToken = dualPreviewPresentation.sessionToken,
        sessionStatus = dualCameraSessionStatus,
    )
    LaunchedEffect(dualCameraTelemetry?.summary, dualCameraTelemetry?.detail, dualCameraTelemetry?.diagnostic) {
        dualCameraTelemetry?.let(onDualCameraTelemetry)
    }
    SectionCard {
        RearCameraPreview(
            enabled = true,
            frontInsetEnabled = dualPreviewPresentation.showFrontInset,
            dualCameraSessionToken = dualPreviewPresentation.sessionToken,
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
                append(if (settings.dualCameraEnabled && capability.isAvailable) "前后双摄" else "后摄单录")
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
            modifier = Modifier.fillMaxWidth(),
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
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = telemetry.detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
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
            text = "将恢复默认录制、存储、音频、GPS、水印字幕和自动启动设置。录像片段、锁定事件和已导出的证据包不会被删除。",
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
