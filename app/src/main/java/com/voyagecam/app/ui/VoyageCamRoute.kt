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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voyagecam.app.R
import com.voyagecam.app.core.camera.DualCameraSessionStatus
import com.voyagecam.app.core.camera.DualCameraSessionCoordinator
import com.voyagecam.app.core.model.DeviceCapabilityGrade
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.PendingStorageCapacityChange
import com.voyagecam.app.core.model.RecordingSegment
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.data.settings.recordingModeLabel
import com.voyagecam.app.feature.recording.RecordingForegroundService
import com.voyagecam.app.ui.events.EmergencyEventPanel
import com.voyagecam.app.ui.history.SegmentHistoryPanel
import com.voyagecam.app.ui.playback.PlaybackPanel
import com.voyagecam.app.ui.playback.PlaybackItem
import com.voyagecam.app.ui.preview.RearCameraPreview
import com.voyagecam.app.ui.preview.DualCameraTelemetryPresentation
import com.voyagecam.app.ui.preview.dualCameraPreviewPresentation
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
        val startingMode = context.recordingModeLabel(
            recordingModeAuto = settings.dualCameraEnabled,
            dualCameraActive = settings.dualCameraEnabled && capability.isAvailable,
        )
        viewModel.setStatus(context.getString(R.string.route_starting_mode, startingMode))
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
                    viewModel.setStatus(
                        context.getString(
                            R.string.route_share_emergency_failed,
                            error.message ?: context.getString(event.trigger.labelRes()),
                        ),
                    )
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
        onRefreshRuntimeTelemetry = viewModel::refreshRuntimeTelemetry,
        onClearRuntimeTelemetry = viewModel::clearRuntimeTelemetry,
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
    onRefreshRuntimeTelemetry: () -> Unit = {},
    onClearRuntimeTelemetry: () -> Unit = {},
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
                    title = { Text(stringResource(R.string.app_name)) },
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
                            onSetStatus(context.getString(R.string.route_recording_mode_change_locked))
                            return@SettingsPanel
                        }

                        onPersistSettings(settings.copy(dualCameraEnabled = enabled))
                        onSetStatus(
                            when {
                                enabled && capability.isAvailable ->
                                    context.getString(R.string.route_recording_mode_auto_supported)
                                enabled ->
                                    context.getString(R.string.route_recording_mode_auto_rear_only)
                                else ->
                                    context.getString(R.string.route_recording_mode_rear_only)
                            },
                        )
                    },
                    onRecordingResolutionChanged = { resolution ->
                        if (isRecording) {
                            onSetStatus(context.getString(R.string.route_resolution_change_locked))
                        } else {
                            onPersistSettings(settings.copy(recordingResolution = resolution))
                            onSetStatus(context.getString(R.string.route_resolution_changed, resolution.label))
                        }
                    },
                    onRecordingFrameRateChanged = { frameRate ->
                        if (isRecording) {
                            onSetStatus(context.getString(R.string.route_frame_rate_change_locked))
                        } else {
                            onPersistSettings(settings.copy(recordingFrameRate = frameRate))
                            onSetStatus(context.getString(R.string.route_frame_rate_changed, frameRate.label))
                        }
                    },
                    onRecordingBitrateChanged = { bitrate ->
                        if (isRecording) {
                            onSetStatus(context.getString(R.string.route_bitrate_change_locked))
                        } else {
                            onPersistSettings(settings.copy(recordingBitrate = bitrate))
                            onSetStatus(context.getString(R.string.route_bitrate_changed, bitrate.label))
                        }
                    },
                    onStorageChanged = onRequestStorageCapacityChange,
                    onCleanupStorage = onCleanupStorageNow,
                    onSegmentDurationChanged = { minutes ->
                        if (isRecording) {
                            onSetStatus(context.getString(R.string.route_segment_duration_change_locked))
                        } else {
                            onPersistSettings(settings.copy(segmentDurationMinutes = minutes))
                        }
                    },
                    onCollisionSensitivityChanged = { sensitivity ->
                        if (isRecording) {
                            onSetStatus(context.getString(R.string.route_collision_sensitivity_change_locked))
                        } else {
                            onPersistSettings(settings.copy(collisionSensitivity = sensitivity))
                        }
                    },
                    onAmbientAudioChanged = { enabled ->
                        if (isRecording) {
                            onSetStatus(context.getString(R.string.route_ambient_audio_change_locked))
                            return@SettingsPanel
                        }

                        if (!enabled) {
                            onPersistSettings(settings.copy(ambientAudioEnabled = false))
                            onSetStatus(context.getString(R.string.route_ambient_audio_disabled))
                            return@SettingsPanel
                        }

                        if (permissionCoordinator.audioPermissionGranted) {
                            onPersistSettings(settings.copy(ambientAudioEnabled = true))
                            onSetStatus(context.getString(R.string.route_ambient_audio_enabled))
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
                                context.getString(R.string.route_thermal_guard_enabled)
                            } else {
                                context.getString(R.string.route_thermal_guard_disabled)
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
                                context.getString(R.string.route_low_battery_guard_enabled)
                            } else {
                                context.getString(R.string.route_low_battery_guard_disabled)
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
                                context.getString(R.string.route_slow_segment_guard_enabled)
                            } else {
                                context.getString(R.string.route_slow_segment_guard_disabled)
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
                                context.getString(R.string.route_export_subtitles_enabled)
                            } else {
                                context.getString(R.string.route_export_subtitles_disabled)
                            },
                        )
                    },
                    onExportBurnedWatermarkVideoChanged = { enabled ->
                        onPersistSettings(settings.copy(exportBurnedWatermarkVideoEnabled = enabled))
                        onSetStatus(
                            if (enabled) {
                                context.getString(R.string.route_export_burned_enabled)
                            } else {
                                context.getString(R.string.route_export_burned_disabled)
                            },
                        )
                    },
                    onAutoStartOnPowerChanged = { enabled ->
                        onPersistSettings(settings.copy(autoStartOnPowerConnected = enabled))
                        onSetStatus(
                            if (enabled) {
                                context.getString(R.string.route_auto_start_power_enabled)
                            } else {
                                context.getString(R.string.route_auto_start_power_disabled)
                            },
                        )
                    },
                    onTrustedBluetoothDeviceChanged = { device ->
                        onPersistSettings(settings.copy(trustedBluetoothDevice = device))
                    },
                    onAutoStartOnTrustedBluetoothChanged = { enabled ->
                        if (enabled && settings.trustedBluetoothDevice.isBlank()) {
                            onSetStatus(context.getString(R.string.route_trusted_bluetooth_required))
                            return@SettingsPanel
                        }

                        onPersistSettings(settings.copy(autoStartOnTrustedBluetooth = enabled))
                        onSetStatus(
                            if (enabled) {
                                context.getString(R.string.route_auto_start_bluetooth_enabled)
                            } else {
                                context.getString(R.string.route_auto_start_bluetooth_disabled)
                            },
                        )
                    },
                    onRequestResetSettings = {
                        if (isRecording) {
                            onSetStatus(context.getString(R.string.route_reset_defaults_locked))
                        } else {
                            onSetPendingSettingsReset(true)
                        }
                    },
                    autoStartDiagnostic = uiState.autoStartDiagnostic,
                    dualCameraDiagnostic = uiState.dualCameraDiagnostic,
                    dualCameraSessionTelemetry = uiState.dualCameraSessionTelemetry,
                    latestCrashReport = uiState.latestCrashReport,
                    recentRuntimeLogs = uiState.recentRuntimeLogs,
                    dualCameraFailureArchive = uiState.dualCameraFailureArchive,
                    onRefreshAutoStartDiagnostic = onRefreshAutoStartDiagnostic,
                    onRefreshDualCameraDiagnostic = onRefreshDualCameraDiagnostic,
                    onClearDualCameraDiagnostic = {
                        onClearDualCameraDiagnostic()
                        onSetStatus(context.getString(R.string.route_dual_camera_diagnostic_cleared))
                    },
                    onRefreshDualCameraSessionTelemetry = onRefreshDualCameraSessionTelemetry,
                    onClearDualCameraSessionTelemetry = {
                        onClearDualCameraSessionTelemetry()
                        onSetStatus(context.getString(R.string.route_dual_camera_session_cleared))
                    },
                    onRefreshRuntimeTelemetry = onRefreshRuntimeTelemetry,
                    onClearRuntimeTelemetry = {
                        onClearRuntimeTelemetry()
                        onSetStatus(context.getString(R.string.route_runtime_telemetry_cleared))
                    },
                    bluetoothDevicePickerState = bluetoothDevicePickerState,
                )

                if (uiState.pendingSettingsReset) {
                    SettingsResetConfirmationPanel(
                        onConfirm = onResetSettingsToDefaults,
                        onCancel = {
                            onSetPendingSettingsReset(false)
                            onSetStatus(context.getString(R.string.route_reset_defaults_cancelled))
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
                            onClearPendingStorageCapacityChange(context.getString(R.string.route_storage_adjust_cancelled))
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
                            onSetStatus(context.getString(R.string.route_delete_event_cancelled))
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
                            onSetStatus(context.getString(R.string.route_delete_segment_cancelled))
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
        onStatus(context.getString(R.string.route_emergency_lock_sent))
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
    val context = LocalContext.current
    val dualPreviewPresentation = dualCameraPreviewPresentation(
        dualCameraEnabled = settings.dualCameraEnabled,
        capability = capability,
        isRecording = isRecording,
    )
    val dualCameraSessionStatus by dualCameraSessionStatusFlow.collectAsState()
    val dualCameraTelemetry = context.dualCameraTelemetryPresentation(
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
            text = stringResource(
                if (isRecording) {
                    R.string.route_recording_state_recording
                } else {
                    R.string.route_recording_state_ready
                },
            ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = buildString {
                append(
                    context.recordingModeLabel(
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
                append(context.getString(R.string.route_recording_summary_segment, settings.segmentDurationMinutes))
                append(" · ")
                append(context.getString(R.string.route_recording_summary_storage, settings.storageCapacityGb))
                append(" · ")
                append(context.getString(R.string.route_recording_summary_collision, context.getString(settings.collisionSensitivity.labelRes())))
                append(" · ")
                append(
                    context.getString(
                        if (settings.ambientAudioEnabled) {
                            R.string.route_recording_summary_audio_on
                        } else {
                            R.string.route_recording_summary_audio_off
                        },
                    ),
                )
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
            Text(
                stringResource(
                    if (isRecording) {
                        R.string.route_recording_button_stop
                    } else {
                        R.string.route_recording_button_start
                    },
                ),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onEmergencyLock,
            enabled = isRecording,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.route_emergency_lock_button))
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
            text = stringResource(R.string.route_reset_confirm_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.route_reset_confirm_message),
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
                Text(stringResource(R.string.common_cancel))
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.route_reset_confirm_action))
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
            text = stringResource(R.string.route_storage_confirm_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.route_storage_confirm_message,
                pending.nextCapacityGb,
                pending.currentNormalBytes.asFileSize(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4D6267),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.route_storage_confirm_overflow, pending.overflowBytes.asFileSize()),
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
                Text(stringResource(R.string.common_cancel))
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.route_storage_confirm_action))
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
    val context = LocalContext.current
    SectionCard {
        Text(
            text = stringResource(R.string.route_delete_event_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.route_delete_event_summary,
                context.getString(event.trigger.labelRes()),
                event.triggeredAtMillis.asTime(),
                event.segmentPaths.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4D6267),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.route_delete_event_message),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
        Spacer(modifier = Modifier.height(12.dp))
        ConfirmationButtonRow(
            confirmLabel = stringResource(R.string.route_delete_event_action),
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
    val context = LocalContext.current
    SectionCard {
        Text(
            text = stringResource(R.string.route_delete_segment_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.route_delete_segment_summary,
                context.getString(segment.cameraDirection.labelRes()),
                context.getString(
                    if (segment.locked) {
                        R.string.route_segment_status_locked
                    } else {
                        R.string.route_segment_status_normal
                    },
                ),
                segment.sizeBytes.asFileSize(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4D6267),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.route_delete_segment_message),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9B2C2C),
        )
        Spacer(modifier = Modifier.height(12.dp))
        ConfirmationButtonRow(
            confirmLabel = stringResource(R.string.route_delete_segment_action),
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
            Text(stringResource(R.string.common_cancel))
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
