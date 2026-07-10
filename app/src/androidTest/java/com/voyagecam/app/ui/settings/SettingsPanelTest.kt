package com.voyagecam.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.voyagecam.app.R
import com.voyagecam.app.core.model.AutoStartDiagnostic
import com.voyagecam.app.core.model.AutoStartResult
import com.voyagecam.app.core.model.AutoStartSource
import com.voyagecam.app.core.model.CollisionSensitivity
import com.voyagecam.app.core.model.DeviceCapabilityGrade
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.DualCameraDiagnostic
import com.voyagecam.app.core.model.DualCameraDiagnosticStage
import com.voyagecam.app.core.model.DualCameraSwitchState
import com.voyagecam.app.core.model.PersistedDualCameraDiagnostic
import com.voyagecam.app.core.model.PersistedDualCameraSessionTelemetry
import com.voyagecam.app.core.model.RecordingStorageOverview
import com.voyagecam.app.data.settings.RecordingBitratePreset
import com.voyagecam.app.data.settings.RecordingFrameRatePreset
import com.voyagecam.app.data.settings.RecordingMode
import com.voyagecam.app.data.settings.RecordingOrientationStrategy
import com.voyagecam.app.data.settings.RecordingResolutionPreset
import com.voyagecam.app.data.settings.StorageCapacityLimit
import com.voyagecam.app.data.settings.VoyageCamSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsPanelTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun displaysDualCameraTelemetryAndInvokesClearActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var clearDiagnosticCount = 0
        var clearTelemetryCount = 0
        var thermalGuardEnabled = true
        var lowBatteryGuardEnabled = true
        var slowSegmentGuardEnabled = true
        var recordingResolution = RecordingResolutionPreset.FHD_1080P
        var recordingFrameRate = RecordingFrameRatePreset.FPS_30
        var recordingBitrate = RecordingBitratePreset.MBPS_12
        var frontMirrorEnabled = false
        var orientationStrategy = RecordingOrientationStrategy.FollowSystem
        val telemetrySummary = context.getString(
            R.string.preview_telemetry_summary,
            2,
            context.getString(R.string.preview_telemetry_state_rear_fallback),
        )
        val telemetryDetail = context.getString(
            R.string.preview_telemetry_detail,
            context.getString(R.string.preview_telemetry_detail_rear),
            context.getString(R.string.preview_telemetry_connected),
            context.getString(R.string.preview_telemetry_detail_front),
            context.getString(R.string.preview_telemetry_connected),
        )
        val telemetryDiagnostic = context.getString(
            R.string.preview_dual_camera_diagnostic_summary,
            context.getString(R.string.label_dual_camera_stage_session),
            "bind failed",
        )
        val dualDiagnosticTitle = context.getString(R.string.settings_dual_camera_diagnostic_title)
        val dualSessionTitle = context.getString(R.string.settings_dual_camera_session_title)
        val exportBurnedTitle = context.getString(R.string.settings_export_burned_title)
        val thermalGuardTitle = context.getString(R.string.settings_thermal_guard_title)
        val recordingModeTitle = context.getString(R.string.settings_recording_mode_title)
        val clearLabel = context.getString(R.string.settings_clear)

        composeRule.setContent {
            SettingsPanel(
                settings = VoyageCamSettings(
                    recordingMode = RecordingMode.Auto,
                    storageCapacityGb = 10,
                    segmentDurationMinutes = 3,
                    recordingResolution = recordingResolution,
                    recordingFrameRate = recordingFrameRate,
                    recordingBitrate = recordingBitrate,
                    collisionSensitivity = CollisionSensitivity.Medium,
                    frontCameraMirrorEnabled = frontMirrorEnabled,
                    recordingOrientationStrategy = orientationStrategy,
                    thermalGuardEnabled = thermalGuardEnabled,
                    lowBatteryGuardEnabled = lowBatteryGuardEnabled,
                    slowSegmentGuardEnabled = slowSegmentGuardEnabled,
                ),
                capability = DualCameraCapability(
                    state = DualCameraSwitchState.AvailableOn,
                    grade = DeviceCapabilityGrade.A,
                    rearCameraId = "rear",
                    frontCameraId = "front",
                    reason = "supported",
                ),
                isRecording = false,
                storageLimit = StorageCapacityLimit(maxGb = 32),
                cameraPermissionGranted = true,
                notificationPermissionGranted = true,
                audioPermissionGranted = true,
                locationPermissionGranted = true,
                bluetoothPermissionGranted = true,
                storageOverview = RecordingStorageOverview(
                    normalBytes = 2L * 1024 * 1024,
                    lockedBytes = 1024L,
                    normalClipCount = 2,
                    lockedClipCount = 1,
                    maxStorageBytes = 10L * 1024 * 1024 * 1024,
                    estimatedBytesPerMinute = 1024L * 1024,
                ),
                pendingStorageCapacityGb = null,
                onRequestCameraPermission = {},
                onRequestNotificationPermission = {},
                onRequestLocationPermission = {},
                onRequestBluetoothPermission = {},
                onRedetect = {},
                onRecordingModeChanged = {},
                onStorageChanged = {},
                onCleanupStorage = {},
                onFrontCameraMirrorChanged = { frontMirrorEnabled = it },
                onRecordingOrientationStrategyChanged = { orientationStrategy = it },
                onRecordingResolutionChanged = { recordingResolution = it },
                onRecordingFrameRateChanged = { recordingFrameRate = it },
                onRecordingBitrateChanged = { recordingBitrate = it },
                onSegmentDurationChanged = {},
                onCollisionSensitivityChanged = {},
                onAmbientAudioChanged = {},
                onThermalGuardChanged = { thermalGuardEnabled = it },
                onLowBatteryGuardChanged = { lowBatteryGuardEnabled = it },
                onSlowSegmentGuardChanged = { slowSegmentGuardEnabled = it },
                onGpsMetadataChanged = {},
                onExportWatermarkSubtitlesChanged = {},
                onExportBurnedWatermarkVideoChanged = {},
                onAutoStartOnPowerChanged = {},
                onTrustedBluetoothDeviceChanged = {},
                onAutoStartOnTrustedBluetoothChanged = {},
                onRequestResetSettings = {},
                autoStartDiagnostic = AutoStartDiagnostic(
                    source = AutoStartSource.Power,
                    result = AutoStartResult.Started,
                    reason = "triggered",
                    detail = "detail",
                    recordedAtMillis = 1_720_000_000_000L,
                ),
                dualCameraDiagnostic = PersistedDualCameraDiagnostic(
                    diagnostic = DualCameraDiagnostic(
                        stage = DualCameraDiagnosticStage.Session,
                        detail = "bind failed",
                    ),
                    recordedAtMillis = 1_720_000_100_000L,
                ),
                dualCameraSessionTelemetry = PersistedDualCameraSessionTelemetry(
                    summary = telemetrySummary,
                    detail = telemetryDetail,
                    diagnostic = telemetryDiagnostic,
                    recordedAtMillis = 1_720_000_200_000L,
                ),
                onRefreshAutoStartDiagnostic = {},
                onRefreshDualCameraDiagnostic = {},
                onClearDualCameraDiagnostic = { clearDiagnosticCount++ },
                onRefreshDualCameraSessionTelemetry = {},
                onClearDualCameraSessionTelemetry = { clearTelemetryCount++ },
                bluetoothDevicePickerState = BluetoothDevicePickerState(
                    loadPairedDevices = { emptyList() },
                    trustedDeviceInput = "",
                ),
            )
        }

        composeRule.onNodeWithText(dualDiagnosticTitle).assertIsDisplayed()
        composeRule.onNodeWithText(dualSessionTitle).assertIsDisplayed()
        composeRule.onNodeWithText(telemetrySummary).assertIsDisplayed()
        composeRule.onNodeWithText(telemetryDiagnostic).assertIsDisplayed()
        composeRule.onNodeWithText(exportBurnedTitle).assertIsDisplayed()
        composeRule.onNodeWithText(thermalGuardTitle).assertIsDisplayed()
        composeRule.onNodeWithText(recordingModeTitle).assertIsDisplayed()
        composeRule.onNodeWithText("1080p").assertIsDisplayed()
        composeRule.onNodeWithText("30fps").assertIsDisplayed()
        composeRule.onNodeWithText("12Mbps").assertIsDisplayed()

        composeRule.onAllNodesWithText(clearLabel)[0].performClick()
        composeRule.onAllNodesWithText(clearLabel)[1].performClick()
        composeRule.onNodeWithText("720p").performClick()
        composeRule.onNodeWithText("60fps").performClick()
        composeRule.onNodeWithText("24Mbps").performClick()
        composeRule.onNodeWithTag("thermal_guard_switch").performClick()
        composeRule.onNodeWithTag("low_battery_guard_switch").performClick()
        composeRule.onNodeWithTag("slow_segment_guard_switch").performClick()

        assertEquals(1, clearDiagnosticCount)
        assertEquals(1, clearTelemetryCount)
        assertEquals(RecordingResolutionPreset.HD_720P, recordingResolution)
        assertEquals(RecordingFrameRatePreset.FPS_60, recordingFrameRate)
        assertEquals(RecordingBitratePreset.MBPS_24, recordingBitrate)
        assertEquals(false, thermalGuardEnabled)
        assertEquals(false, lowBatteryGuardEnabled)
        assertEquals(false, slowSegmentGuardEnabled)
    }
}
