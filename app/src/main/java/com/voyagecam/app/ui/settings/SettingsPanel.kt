package com.voyagecam.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.voyagecam.app.core.model.AutoStartDiagnostic
import com.voyagecam.app.core.model.AutoStartResult
import com.voyagecam.app.core.model.CollisionSensitivity
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.PersistedCrashReport
import com.voyagecam.app.core.model.PersistedDualCameraDiagnostic
import com.voyagecam.app.core.model.PersistedDualCameraFailureArchive
import com.voyagecam.app.core.model.PersistedDualCameraSessionTelemetry
import com.voyagecam.app.core.model.PersistedStructuredLogEntry
import com.voyagecam.app.core.model.RecordingStorageOverview
import com.voyagecam.app.core.model.TrustedBluetoothDevice
import com.voyagecam.app.R
import com.voyagecam.app.data.settings.RecordingBitratePreset
import com.voyagecam.app.data.settings.RecordingFrameRatePreset
import com.voyagecam.app.data.settings.RecordingMode
import com.voyagecam.app.data.settings.RecordingOrientationStrategy
import com.voyagecam.app.data.settings.RecordingResolutionPreset
import com.voyagecam.app.data.settings.StorageCapacityLimit
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.data.settings.VoyageCamSettingsStore
import com.voyagecam.app.data.settings.recordingModeDescription
import com.voyagecam.app.data.settings.recordingVideoProfile
import com.voyagecam.app.data.settings.resolveRecordingConfig
import com.voyagecam.app.data.settings.supportsDualCamera
import com.voyagecam.app.ui.dualCameraDiagnosticSummary
import com.voyagecam.app.ui.labelRes
import com.voyagecam.app.ui.theme.SectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsPanel(
    settings: VoyageCamSettings,
    capability: DualCameraCapability,
    isRecording: Boolean,
    storageLimit: StorageCapacityLimit,
    cameraPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    audioPermissionGranted: Boolean,
    locationPermissionGranted: Boolean,
    bluetoothPermissionGranted: Boolean,
    storageOverview: RecordingStorageOverview,
    pendingStorageCapacityGb: Int?,
    onRequestCameraPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onRedetect: () -> Unit,
    onRecordingModeChanged: (RecordingMode) -> Unit,
    onStorageChanged: (Int) -> Unit,
    onCleanupStorage: () -> Unit,
    onFrontCameraMirrorChanged: (Boolean) -> Unit,
    onRecordingOrientationStrategyChanged: (RecordingOrientationStrategy) -> Unit,
    onRecordingResolutionChanged: (RecordingResolutionPreset) -> Unit,
    onRecordingFrameRateChanged: (RecordingFrameRatePreset) -> Unit,
    onRecordingBitrateChanged: (RecordingBitratePreset) -> Unit,
    onSegmentDurationChanged: (Int) -> Unit,
    onCollisionSensitivityChanged: (CollisionSensitivity) -> Unit,
    onAmbientAudioChanged: (Boolean) -> Unit,
    onThermalGuardChanged: (Boolean) -> Unit,
    onLowBatteryGuardChanged: (Boolean) -> Unit,
    onSlowSegmentGuardChanged: (Boolean) -> Unit,
    onGpsMetadataChanged: (Boolean) -> Unit,
    onExportWatermarkSubtitlesChanged: (Boolean) -> Unit,
    onExportBurnedWatermarkVideoChanged: (Boolean) -> Unit,
    onAutoStartOnPowerChanged: (Boolean) -> Unit,
    onTrustedBluetoothDeviceChanged: (String) -> Unit,
    onAutoStartOnTrustedBluetoothChanged: (Boolean) -> Unit,
    onRequestResetSettings: () -> Unit,
    autoStartDiagnostic: AutoStartDiagnostic? = null,
    dualCameraDiagnostic: PersistedDualCameraDiagnostic? = null,
    dualCameraSessionTelemetry: PersistedDualCameraSessionTelemetry? = null,
    latestCrashReport: PersistedCrashReport? = null,
    recentRuntimeLogs: List<PersistedStructuredLogEntry> = emptyList(),
    dualCameraFailureArchive: List<PersistedDualCameraFailureArchive> = emptyList(),
    onRefreshAutoStartDiagnostic: () -> Unit = {},
    onRefreshDualCameraDiagnostic: () -> Unit = {},
    onClearDualCameraDiagnostic: () -> Unit = {},
    onRefreshDualCameraSessionTelemetry: () -> Unit = {},
    onClearDualCameraSessionTelemetry: () -> Unit = {},
    onRefreshRuntimeTelemetry: () -> Unit = {},
    onClearRuntimeTelemetry: () -> Unit = {},
    bluetoothDevicePickerState: BluetoothDevicePickerState,
) {
    val context = LocalContext.current
    val resolvedConfig = settings.resolveRecordingConfig(capability)
    val frontCameraAvailable = capability.frontCameraId != null
    val showLockedHint = isRecording
    val showDualProfileFallbackHint = settings.recordingMode == RecordingMode.Auto &&
        capability.isAvailable &&
        !settings.recordingVideoProfile().supportsDualCamera()
    val visibleStorageCapacityGb = pendingStorageCapacityGb ?: settings.storageCapacityGb
    val storageInputState = androidx.compose.runtime.remember(visibleStorageCapacityGb) {
        androidx.compose.runtime.mutableStateOf(visibleStorageCapacityGb.toString())
    }
    val storageInput = storageInputState.value

    SectionCard {
        Text(
            text = stringResource(R.string.settings_device_permissions_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(10.dp))
        CapabilityDetail(capability = capability)
        Spacer(modifier = Modifier.height(12.dp))
        PermissionRow(
            stringResource(R.string.settings_permission_camera),
            cameraPermissionGranted,
            stringResource(R.string.settings_permission_request_camera),
            onRequestCameraPermission,
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionRow(
            stringResource(R.string.settings_permission_notification),
            notificationPermissionGranted,
            stringResource(R.string.settings_permission_request_notification),
            onRequestNotificationPermission,
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionRow(
            stringResource(R.string.settings_permission_microphone),
            audioPermissionGranted,
            stringResource(R.string.settings_permission_request_on_demand),
            {},
            enabled = false,
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionRow(
            stringResource(R.string.settings_permission_location),
            locationPermissionGranted,
            stringResource(R.string.settings_permission_request_location),
            onRequestLocationPermission,
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionRow(
            stringResource(R.string.settings_permission_bluetooth),
            bluetoothPermissionGranted,
            stringResource(R.string.settings_permission_request_bluetooth),
            onRequestBluetoothPermission,
        )
    }

    SectionCard {
        Text(
            text = stringResource(R.string.settings_recording_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(14.dp))
        if (showLockedHint) {
            Text(
                text = stringResource(R.string.settings_locked_apply_after_stop),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9B2C2C),
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Text(
            text = stringResource(R.string.settings_recording_mode_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SelectionButtonRow(
            options = listOf(
                SelectionOption(
                    label = stringResource(R.string.recording_mode_rear_only),
                    selected = settings.recordingMode == RecordingMode.RearOnly,
                    onClick = { onRecordingModeChanged(RecordingMode.RearOnly) },
                ),
                SelectionOption(
                    label = stringResource(R.string.recording_mode_front_only),
                    selected = settings.recordingMode == RecordingMode.FrontOnly,
                    enabled = frontCameraAvailable,
                    onClick = { onRecordingModeChanged(RecordingMode.FrontOnly) },
                ),
                SelectionOption(
                    label = stringResource(R.string.recording_mode_auto),
                    selected = settings.recordingMode == RecordingMode.Auto,
                    onClick = { onRecordingModeChanged(RecordingMode.Auto) },
                ),
            ),
            enabled = !isRecording,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when {
                isRecording -> stringResource(R.string.settings_locked_apply_after_stop)
                else -> context.recordingModeDescription(
                    settings = settings,
                    dualCameraSupported = capability.isAvailable,
                    hasFrontCamera = frontCameraAvailable,
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
        if (showDualProfileFallbackHint) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_dual_profile_fallback_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9B2C2C),
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        SettingSwitchRow(
            title = stringResource(R.string.settings_front_mirror_title),
            subtitle = when {
                settings.recordingMode == RecordingMode.RearOnly ->
                    stringResource(R.string.settings_front_mirror_summary_rear_only)
                resolvedConfig.frontCameraActive ->
                    stringResource(R.string.settings_front_mirror_summary_active)
                else ->
                    stringResource(R.string.settings_front_mirror_summary_pending)
            },
            checked = settings.frontCameraMirrorEnabled,
            enabled = !isRecording && settings.recordingMode != RecordingMode.RearOnly && frontCameraAvailable,
            onCheckedChange = onFrontCameraMirrorChanged,
            switchModifier = Modifier.testTag("front_mirror_switch"),
        )

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.settings_orientation_strategy_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SelectionButtonRow(
            options = RecordingOrientationStrategy.entries.map { strategy ->
                SelectionOption(
                    label = stringResource(
                        when (strategy) {
                            RecordingOrientationStrategy.FollowSystem -> R.string.settings_orientation_follow_system
                            RecordingOrientationStrategy.FixedLandscapeDriving -> R.string.settings_orientation_fixed_landscape
                        },
                    ),
                    selected = settings.recordingOrientationStrategy == strategy,
                    onClick = { onRecordingOrientationStrategyChanged(strategy) },
                )
            },
            enabled = !isRecording,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_orientation_strategy_summary),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onRedetect,
            enabled = !isRecording,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_redetect_dual_camera))
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.settings_resolution_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SelectionButtonRow(
            options = RecordingResolutionPreset.entries.map { preset ->
                SelectionOption(
                    label = preset.label,
                    selected = settings.recordingResolution == preset,
                    onClick = { onRecordingResolutionChanged(preset) },
                )
            },
            enabled = !isRecording,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_resolution_summary),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.settings_frame_rate_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SelectionButtonRow(
            options = RecordingFrameRatePreset.entries.map { preset ->
                SelectionOption(
                    label = preset.label,
                    selected = settings.recordingFrameRate == preset,
                    onClick = { onRecordingFrameRateChanged(preset) },
                )
            },
            enabled = !isRecording,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_frame_rate_summary),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.settings_bitrate_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SelectionButtonRow(
            options = RecordingBitratePreset.entries.map { preset ->
                SelectionOption(
                    label = preset.label,
                    selected = settings.recordingBitrate == preset,
                    onClick = { onRecordingBitrateChanged(preset) },
                )
            },
            enabled = !isRecording,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_bitrate_summary),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.settings_segment_duration_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SegmentDurationRow(settings.segmentDurationMinutes, !isRecording, onSegmentDurationChanged)

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.settings_collision_sensitivity_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        CollisionSensitivityRow(settings.collisionSensitivity, !isRecording, onCollisionSensitivityChanged)

        Spacer(modifier = Modifier.height(18.dp))
        PresetStorageRow(
            currentGb = visibleStorageCapacityGb,
            maxGb = storageLimit.maxGb,
            onStorageChanged = {
                storageInputState.value = it.toString()
                onStorageChanged(it)
            },
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = storageInput,
            onValueChange = { value: String ->
                storageInputState.value = value.filter { it.isDigit() }.take(3)
                val next = storageInputState.value.toIntOrNull()
                if (next != null && next in VoyageCamSettingsStore.MIN_STORAGE_GB..storageLimit.maxGb) {
                    onStorageChanged(next)
                }
            },
            label = { Text(stringResource(R.string.settings_custom_storage_label)) },
            suffix = { Text(stringResource(R.string.settings_storage_suffix_gb)) },
            supportingText = {
                Text(
                    stringResource(
                        R.string.settings_custom_storage_summary,
                        VoyageCamSettingsStore.MIN_STORAGE_GB,
                        storageLimit.maxGb,
                    ),
                )
            },
            isError = storageInput.toIntOrNull()?.let {
                it !in VoyageCamSettingsStore.MIN_STORAGE_GB..storageLimit.maxGb
            } == true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        StorageOverviewPanel(storageOverview = storageOverview)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCleanupStorage,
            enabled = !isRecording,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_cleanup_storage_now))
        }

        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            title = stringResource(R.string.settings_ambient_audio_title),
            subtitle = when {
                settings.recordingMode == RecordingMode.FrontOnly ->
                    stringResource(R.string.settings_ambient_audio_summary_front_only)
                else ->
                    stringResource(R.string.settings_ambient_audio_summary)
            },
            checked = settings.ambientAudioEnabled,
            enabled = !isRecording,
            onCheckedChange = onAmbientAudioChanged,
        )

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.settings_performance_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingSwitchRow(
            title = stringResource(R.string.settings_thermal_guard_title),
            subtitle = if (settings.thermalGuardEnabled) {
                stringResource(R.string.settings_thermal_guard_enabled_summary)
            } else {
                stringResource(R.string.settings_thermal_guard_disabled_summary)
            },
            checked = settings.thermalGuardEnabled,
            enabled = true,
            onCheckedChange = onThermalGuardChanged,
            switchModifier = Modifier.testTag("thermal_guard_switch"),
        )

        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            title = stringResource(R.string.settings_low_battery_guard_title),
            subtitle = if (settings.lowBatteryGuardEnabled) {
                stringResource(R.string.settings_low_battery_guard_enabled_summary)
            } else {
                stringResource(R.string.settings_low_battery_guard_disabled_summary)
            },
            checked = settings.lowBatteryGuardEnabled,
            enabled = true,
            onCheckedChange = onLowBatteryGuardChanged,
            switchModifier = Modifier.testTag("low_battery_guard_switch"),
        )

        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            title = stringResource(R.string.settings_slow_segment_guard_title),
            subtitle = if (settings.slowSegmentGuardEnabled) {
                stringResource(R.string.settings_slow_segment_guard_enabled_summary)
            } else {
                stringResource(R.string.settings_slow_segment_guard_disabled_summary)
            },
            checked = settings.slowSegmentGuardEnabled,
            enabled = true,
            onCheckedChange = onSlowSegmentGuardChanged,
            switchModifier = Modifier.testTag("slow_segment_guard_switch"),
        )

        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            title = stringResource(R.string.settings_gps_metadata_title),
            subtitle = when {
                locationPermissionGranted -> stringResource(R.string.settings_gps_metadata_summary_enabled)
                settings.gpsMetadataEnabled -> stringResource(R.string.settings_gps_metadata_summary_granted)
                else -> stringResource(R.string.settings_gps_metadata_summary_disabled)
            },
            checked = settings.gpsMetadataEnabled,
            enabled = true,
            onCheckedChange = onGpsMetadataChanged,
            switchModifier = Modifier.testTag("gps_metadata_switch"),
        )

        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            title = stringResource(R.string.settings_export_subtitles_title),
            subtitle = stringResource(R.string.settings_export_subtitles_summary),
            checked = settings.exportWatermarkSubtitlesEnabled,
            enabled = true,
            onCheckedChange = onExportWatermarkSubtitlesChanged,
        )

        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            title = stringResource(R.string.settings_export_burned_title),
            subtitle = stringResource(R.string.settings_export_burned_summary),
            checked = settings.exportBurnedWatermarkVideoEnabled,
            enabled = true,
            onCheckedChange = onExportBurnedWatermarkVideoChanged,
        )

        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            title = stringResource(R.string.settings_auto_start_power_title),
            subtitle = stringResource(R.string.settings_auto_start_power_summary),
            checked = settings.autoStartOnPowerConnected,
            enabled = !isRecording,
            onCheckedChange = onAutoStartOnPowerChanged,
        )

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = bluetoothDevicePickerState.trustedDeviceInput,
            onValueChange = { value ->
                onTrustedBluetoothDeviceChanged(
                    bluetoothDevicePickerState.updateTrustedDeviceInput(value),
                )
            },
            label = { Text(stringResource(R.string.settings_trusted_bluetooth_label)) },
            supportingText = { Text(stringResource(R.string.settings_trusted_bluetooth_supporting)) },
            enabled = !isRecording,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        PairedBluetoothDevicePanel(
            devices = bluetoothDevicePickerState.pairedDevices,
            bluetoothPermissionGranted = bluetoothPermissionGranted,
            onRefresh = bluetoothDevicePickerState::refreshPairedDevices,
            onSelected = { device ->
                onTrustedBluetoothDeviceChanged(bluetoothDevicePickerState.selectDevice(device))
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            title = stringResource(R.string.settings_auto_start_bluetooth_title),
            subtitle = stringResource(R.string.settings_auto_start_bluetooth_summary),
            checked = settings.autoStartOnTrustedBluetooth,
            enabled = !isRecording && bluetoothDevicePickerState.trustedDeviceInput.isNotBlank(),
            onCheckedChange = onAutoStartOnTrustedBluetoothChanged,
        )

        Spacer(modifier = Modifier.height(16.dp))
        AutoStartDiagnosticPanel(autoStartDiagnostic, onRefreshAutoStartDiagnostic)

        Spacer(modifier = Modifier.height(16.dp))
        DualCameraDiagnosticPanel(
            diagnostic = dualCameraDiagnostic,
            onRefresh = onRefreshDualCameraDiagnostic,
            onClear = onClearDualCameraDiagnostic,
        )

        Spacer(modifier = Modifier.height(16.dp))
        DualCameraSessionTelemetryPanel(
            telemetry = dualCameraSessionTelemetry,
            onRefresh = onRefreshDualCameraSessionTelemetry,
            onClear = onClearDualCameraSessionTelemetry,
        )

        Spacer(modifier = Modifier.height(16.dp))
        RuntimeTelemetryPanel(
            latestCrashReport = latestCrashReport,
            recentRuntimeLogs = recentRuntimeLogs,
            dualCameraFailureArchive = dualCameraFailureArchive,
            onRefresh = onRefreshRuntimeTelemetry,
            onClear = onClearRuntimeTelemetry,
        )

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRequestResetSettings,
            enabled = !isRecording,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_reset_defaults))
        }
    }
}

@Composable
private fun RuntimeTelemetryPanel(
    latestCrashReport: PersistedCrashReport?,
    recentRuntimeLogs: List<PersistedStructuredLogEntry>,
    dualCameraFailureArchive: List<PersistedDualCameraFailureArchive>,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.settings_runtime_telemetry_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh) {
                Text(stringResource(R.string.settings_refresh))
            }
            OutlinedButton(onClick = onClear) {
                Text(stringResource(R.string.settings_runtime_telemetry_clear))
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = stringResource(R.string.settings_runtime_crash_title),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF163036),
    )
    Spacer(modifier = Modifier.height(4.dp))
    if (latestCrashReport == null) {
        Text(
            text = stringResource(R.string.settings_runtime_crash_empty),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    } else {
        Text(
            text = stringResource(
                R.string.settings_runtime_crash_summary,
                latestCrashReport.exceptionType,
                latestCrashReport.recordedAtMillis.asTime(),
            ),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF9B2C2C),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_runtime_crash_thread, latestCrashReport.threadName),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4D6267),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_runtime_crash_version, latestCrashReport.appVersion),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4D6267),
        )
        latestCrashReport.message?.takeIf { it.isNotBlank() }?.let { message ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_runtime_crash_message, message),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4D6267),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.settings_runtime_crash_stack,
                latestCrashReport.stacktrace.lineSequence().take(4).joinToString(separator = "\n"),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.settings_runtime_logs_title),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF163036),
    )
    Spacer(modifier = Modifier.height(4.dp))
    if (recentRuntimeLogs.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_runtime_logs_empty),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    } else {
        recentRuntimeLogs.forEach { logEntry ->
            Text(
                text = stringResource(
                    R.string.settings_runtime_log_summary,
                    stringResource(logEntry.level.labelRes()),
                    "${logEntry.category}/${logEntry.event}",
                    logEntry.recordedAtMillis.asTime(),
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = when (logEntry.level) {
                    com.voyagecam.app.core.model.StructuredLogLevel.Warn -> Color(0xFF9B2C2C)
                    com.voyagecam.app.core.model.StructuredLogLevel.Error,
                    com.voyagecam.app.core.model.StructuredLogLevel.Fatal -> Color(0xFF9B2C2C)
                    else -> Color(0xFF1F6F78)
                },
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = logEntry.message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4D6267),
            )
            if (logEntry.attributes.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.settings_runtime_log_attributes, logEntry.attributes),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64777B),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    Text(
        text = stringResource(R.string.settings_runtime_dual_camera_archive_title),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF163036),
    )
    Spacer(modifier = Modifier.height(4.dp))
    if (dualCameraFailureArchive.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_runtime_dual_camera_archive_empty),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    } else {
        dualCameraFailureArchive.forEach { archive ->
            val stageLabel = archive.stage?.let { context.getString(it.labelRes()) }
                ?: context.getString(R.string.settings_runtime_dual_camera_archive_stage_unknown)
            Text(
                text = stringResource(
                    R.string.settings_runtime_dual_camera_archive_summary,
                    context.getString(archive.source.labelRes()),
                    stageLabel,
                    archive.recordedAtMillis.asTime(),
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF9B2C2C),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = archive.detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4D6267),
            )
            if (archive.attributes.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.settings_runtime_log_attributes, archive.attributes),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64777B),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun StorageOverviewPanel(storageOverview: RecordingStorageOverview) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_storage_overview_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        LinearProgressIndicator(
            progress = storageOverview.normalUsagePercent / 100f,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.settings_storage_overview_normal,
                storageOverview.normalBytes.asFileSize(),
                storageOverview.maxStorageBytes.asFileSize(),
                storageOverview.normalUsagePercent,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4D6267),
        )
        Text(
            text = stringResource(
                R.string.settings_storage_overview_locked,
                storageOverview.lockedBytes.asFileSize(),
                storageOverview.normalClipCount,
                storageOverview.lockedClipCount,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
        Text(
            text = stringResource(
                R.string.settings_storage_overview_remaining,
                storageOverview.estimatedRemainingMinutes.asDurationText(context),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    }
}

@Composable
private fun PairedBluetoothDevicePanel(
    devices: List<TrustedBluetoothDevice>,
    bluetoothPermissionGranted: Boolean,
    onRefresh: () -> Unit,
    onSelected: (TrustedBluetoothDevice) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.settings_paired_bluetooth_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        OutlinedButton(onClick = onRefresh) {
            Text(stringResource(R.string.settings_refresh))
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (!bluetoothPermissionGranted) {
        Text(
            text = stringResource(R.string.settings_paired_bluetooth_permission_required),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    } else if (devices.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_paired_bluetooth_empty),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    } else {
        devices.take(MAX_PAIRED_BLUETOOTH_DEVICES).forEach { device ->
            OutlinedButton(
                onClick = { onSelected(device) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(device.displayLabel())
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AutoStartDiagnosticPanel(
    diagnostic: AutoStartDiagnostic?,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.settings_auto_start_diagnostic_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        OutlinedButton(onClick = onRefresh) {
            Text(stringResource(R.string.settings_refresh))
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (diagnostic == null) {
        Text(
            text = stringResource(R.string.settings_auto_start_diagnostic_empty),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    } else {
        Text(
            text = "${context.getString(diagnostic.source.labelRes())} · ${context.getString(diagnostic.result.labelRes())} · ${diagnostic.recordedAtMillis.asTime()}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (diagnostic.result == AutoStartResult.Started) Color(0xFF1F6F78) else Color(0xFF9B2C2C),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = diagnostic.reason,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4D6267),
        )
        if (diagnostic.detail.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = diagnostic.detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        }
    }
}

@Composable
private fun DualCameraDiagnosticPanel(
    diagnostic: PersistedDualCameraDiagnostic?,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.settings_dual_camera_diagnostic_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh) {
                Text(stringResource(R.string.settings_refresh))
            }
            OutlinedButton(onClick = onClear) {
                Text(stringResource(R.string.settings_clear))
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (diagnostic == null) {
        Text(
            text = stringResource(R.string.settings_dual_camera_diagnostic_empty),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    } else {
        Text(
            text = "${context.getString(diagnostic.stage.labelRes())} · ${diagnostic.recordedAtMillis.asTime()}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF9B2C2C),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = diagnostic.detail,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4D6267),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_dual_camera_diagnostic_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    }
}

@Composable
private fun DualCameraSessionTelemetryPanel(
    telemetry: PersistedDualCameraSessionTelemetry?,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.settings_dual_camera_session_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh) {
                Text(stringResource(R.string.settings_refresh))
            }
            OutlinedButton(onClick = onClear) {
                Text(stringResource(R.string.settings_clear))
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (telemetry == null) {
        Text(
            text = stringResource(R.string.settings_dual_camera_session_empty),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    } else {
        Text(
            text = "${telemetry.summary} · ${telemetry.recordedAtMillis.asTime()}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (telemetry.diagnostic == null) Color(0xFF1F6F78) else Color(0xFF9B2C2C),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = telemetry.detail,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4D6267),
        )
        telemetry.diagnostic?.let { diagnostic ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = diagnostic,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9B2C2C),
            )
        }
    }
}

@Composable
private fun CapabilityDetail(capability: DualCameraCapability) {
    val context = LocalContext.current
    Text(
        text = stringResource(
            R.string.settings_capability_grade,
            capability.grade.name,
            stringResource(capability.grade.labelRes()),
        ),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF163036),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(
            R.string.settings_capability_state,
            stringResource(capability.state.asLabelRes()),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF64777B),
    )
    Spacer(modifier = Modifier.height(8.dp))
    capability.failureReason?.let { failureReason ->
        Text(
            text = stringResource(
                R.string.settings_capability_failure_reason,
                stringResource(failureReason.labelRes()),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9B2C2C),
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
    Text(
        text = stringResource(R.string.settings_capability_reason, capability.reason),
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF64777B),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(
            R.string.settings_capability_recommended_mode,
            capability.recommendedModeSummary(context),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF2F6F62),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(
            R.string.settings_capability_preview_probe,
            stringResource(capability.previewProbe.status.labelRes()),
            capability.previewProbe.detail,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF4D6267),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(
            R.string.settings_capability_recording_probe,
            stringResource(capability.recordingProbe.status.labelRes()),
            capability.recordingProbe.detail,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF4D6267),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(
            R.string.settings_capability_encoding_probe,
            stringResource(capability.encodingProbe.status.labelRes()),
            capability.encodingProbe.detail,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF4D6267),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(
            R.string.settings_capability_rear,
            capability.rearCameraId ?: "-",
            capability.rearSummary,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF4D6267),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(
            R.string.settings_capability_front,
            capability.frontCameraId ?: "-",
            capability.frontSummary,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF4D6267),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(
            R.string.settings_capability_checked_at,
            capability.systemSummary,
            capability.checkedAtMillis.asTime(),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF64777B),
    )
}

private fun DualCameraCapability.recommendedModeSummary(context: android.content.Context): String {
    return when {
        failureReason == com.voyagecam.app.core.model.DualCameraFailureReason.PermissionMissing ->
            context.getString(R.string.settings_capability_recommend_permission)

        grade == com.voyagecam.app.core.model.DeviceCapabilityGrade.A &&
            encodingProbe.status == com.voyagecam.app.core.model.DualCameraProbeStatus.SupportedWithDowngrade ->
            context.getString(R.string.settings_capability_recommend_auto_downgrade)

        grade == com.voyagecam.app.core.model.DeviceCapabilityGrade.A ->
            context.getString(R.string.settings_capability_recommend_auto)

        grade == com.voyagecam.app.core.model.DeviceCapabilityGrade.B ->
            context.getString(R.string.settings_capability_recommend_preview_only)

        grade == com.voyagecam.app.core.model.DeviceCapabilityGrade.C ->
            context.getString(R.string.settings_capability_recommend_rear_only)

        else ->
            context.getString(R.string.settings_capability_recommend_retry)
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    actionLabel: String,
    onRequestPermission: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(
                R.string.settings_permission_row,
                label,
                stringResource(
                    if (granted) {
                        R.string.settings_permission_state
                    } else {
                        R.string.settings_permission_state_not_granted
                    },
                ),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4D6267),
        )
        if (!granted) {
            OutlinedButton(
                onClick = onRequestPermission,
                enabled = enabled,
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun SegmentDurationRow(
    currentMinutes: Int,
    enabled: Boolean,
    onSegmentDurationChanged: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VoyageCamSettingsStore.ALLOWED_SEGMENT_DURATIONS_MINUTES.forEach { option ->
            if (currentMinutes == option) {
                Button(
                    onClick = { onSegmentDurationChanged(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_segment_minutes, option))
                }
            } else {
                OutlinedButton(
                    onClick = { onSegmentDurationChanged(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_segment_minutes, option))
                }
            }
        }
    }
}

@Composable
private fun CollisionSensitivityRow(
    current: CollisionSensitivity,
    enabled: Boolean,
    onCollisionSensitivityChanged: (CollisionSensitivity) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CollisionSensitivity.entries.forEach { option ->
            val label = stringResource(
                R.string.settings_collision_option,
                stringResource(option.labelRes()),
                option.thresholdG,
            )
            if (current == option) {
                Button(
                    onClick = { onCollisionSensitivityChanged(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(label)
                }
            } else {
                OutlinedButton(
                    onClick = { onCollisionSensitivityChanged(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun PresetStorageRow(
    currentGb: Int,
    maxGb: Int,
    onStorageChanged: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(5, 10, 20).forEach { option ->
            val enabled = option <= maxGb
            if (currentGb == option) {
                Button(
                    onClick = { onStorageChanged(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_storage_preset_gb, option))
                }
            } else {
                OutlinedButton(
                    onClick = { onStorageChanged(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_storage_preset_gb, option))
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF163036),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = switchModifier,
        )
    }
}

private data class SelectionOption(
    val label: String,
    val selected: Boolean,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
private fun SelectionButtonRow(
    options: List<SelectionOption>,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            if (option.selected) {
                Button(
                    onClick = option.onClick,
                    enabled = enabled && option.enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(option.label)
                }
            } else {
                OutlinedButton(
                    onClick = option.onClick,
                    enabled = enabled && option.enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(option.label)
                }
            }
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

private fun Long.asDurationText(context: android.content.Context): String {
    if (this <= 0L) return context.getString(R.string.settings_duration_less_than_one_minute)
    val hours = this / 60
    val minutes = this % 60
    return when {
        hours > 0 && minutes > 0 -> context.getString(
            R.string.settings_duration_hours_minutes,
            hours,
            minutes,
        )
        hours > 0 -> context.getString(R.string.settings_duration_hours_only, hours)
        else -> context.getString(R.string.settings_duration_minutes_only, minutes)
    }
}

private const val MAX_PAIRED_BLUETOOTH_DEVICES = 6
