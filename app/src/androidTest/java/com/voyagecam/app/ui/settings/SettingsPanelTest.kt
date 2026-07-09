package com.voyagecam.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        var clearDiagnosticCount = 0
        var clearTelemetryCount = 0
        var thermalGuardEnabled = true
        var lowBatteryGuardEnabled = true
        var slowSegmentGuardEnabled = true
        var recordingResolution = RecordingResolutionPreset.FHD_1080P
        var recordingFrameRate = RecordingFrameRatePreset.FPS_30
        var recordingBitrate = RecordingBitratePreset.MBPS_12

        composeRule.setContent {
            SettingsPanel(
                settings = VoyageCamSettings(
                    dualCameraEnabled = true,
                    storageCapacityGb = 10,
                    segmentDurationMinutes = 3,
                    recordingResolution = recordingResolution,
                    recordingFrameRate = recordingFrameRate,
                    recordingBitrate = recordingBitrate,
                    collisionSensitivity = CollisionSensitivity.Medium,
                    thermalGuardEnabled = thermalGuardEnabled,
                    lowBatteryGuardEnabled = lowBatteryGuardEnabled,
                    slowSegmentGuardEnabled = slowSegmentGuardEnabled,
                ),
                capability = DualCameraCapability(
                    state = DualCameraSwitchState.AvailableOn,
                    grade = DeviceCapabilityGrade.A,
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
                onRecordingModeAutoChanged = {},
                onStorageChanged = {},
                onCleanupStorage = {},
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
                    summary = "双摄 Session 2 · 已回落到后摄预览",
                    detail = "后摄预览已连接 · 前摄预览已连接",
                    diagnostic = "双摄会话：bind failed",
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

        composeRule.onNodeWithText("双摄诊断").assertIsDisplayed()
        composeRule.onNodeWithText("双摄会话状态").assertIsDisplayed()
        composeRule.onNodeWithText("双摄 Session 2 · 已回落到后摄预览").assertIsDisplayed()
        composeRule.onNodeWithText("双摄会话：bind failed").assertIsDisplayed()
        composeRule.onNodeWithText("导出烧录时间/速度/位置水印视频").assertIsDisplayed()
        composeRule.onNodeWithText("过热时自动关闭前摄").assertIsDisplayed()
        composeRule.onNodeWithText("录制模式").assertIsDisplayed()
        composeRule.onNodeWithText("1080p").assertIsDisplayed()
        composeRule.onNodeWithText("30fps").assertIsDisplayed()
        composeRule.onNodeWithText("12Mbps").assertIsDisplayed()

        composeRule.onAllNodesWithText("清空")[0].performClick()
        composeRule.onAllNodesWithText("清空")[1].performClick()
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
