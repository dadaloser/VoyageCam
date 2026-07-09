package com.voyagecam.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.voyagecam.app.core.camera.DualCameraSessionStatus
import com.voyagecam.app.core.model.DeviceCapabilityGrade
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.DualCameraSwitchState
import com.voyagecam.app.data.camera.DualCameraSessionTelemetryStore
import com.voyagecam.app.data.settings.VoyageCamSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow

class RecordingRoutePanelTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun clickingStartDelegatesToPermissionCoordinatorBeforeStartingService() {
        val controller = FakeRecordingServiceController()
        var permissionStartRequests = 0

        composeRule.setContent {
            RecordingRoutePanel(
                settings = VoyageCamSettings(
                    dualCameraEnabled = true,
                    storageCapacityGb = 10,
                    segmentDurationMinutes = 3,
                ),
                capability = DualCameraCapability(
                    state = DualCameraSwitchState.AvailableOn,
                    grade = DeviceCapabilityGrade.A,
                    reason = "supported",
                ),
                isRecording = false,
                statusMessage = "等待开始",
                permissionCoordinator = PermissionCoordinator(
                    cameraPermissionGranted = false,
                    notificationPermissionGranted = false,
                    audioPermissionGranted = false,
                    locationPermissionGranted = false,
                    bluetoothPermissionGranted = false,
                    requestStartRecording = { permissionStartRequests++ },
                    requestCameraPermission = {},
                    requestNotificationPermission = {},
                    requestAudioPermission = {},
                    requestLocationPermission = {},
                    requestBluetoothPermission = {},
                ),
                recordingServiceController = controller,
                onRecordingStopped = {},
                onStatus = {},
                onRefreshRecordingData = {},
                onDualCameraTelemetry = {},
                previewContent = { _, _ ->
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(100.dp)
                            .testTag("rear_camera_preview"),
                    )
                },
            )
        }

        composeRule.onNodeWithTag("recording_toggle_button").performClick()

        assertEquals(1, permissionStartRequests)
        assertEquals(0, controller.startCalls)
        assertEquals(0, controller.stopCalls)
    }

    @Test
    fun clickingRoutePanelButtonsUsesPermissionCoordinatorControllerAndPersistsTelemetry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val telemetryStore = DualCameraSessionTelemetryStore(context)
        val sessionStatusFlow = MutableStateFlow(previewSessionStatus(sessionToken = 1, recordingActive = false))
        val controller = FakeRecordingServiceController()
        var permissionStartRequests = 0

        telemetryStore.clear()
        try {
            composeRule.setContent {
                var isRecording by remember { mutableStateOf(false) }
                var statusMessage by remember { mutableStateOf("等待开始") }

                RecordingRoutePanel(
                    settings = VoyageCamSettings(
                        dualCameraEnabled = true,
                        storageCapacityGb = 10,
                        segmentDurationMinutes = 3,
                    ),
                    capability = DualCameraCapability(
                        state = DualCameraSwitchState.AvailableOn,
                        grade = DeviceCapabilityGrade.A,
                        reason = "supported",
                    ),
                    isRecording = isRecording,
                    statusMessage = statusMessage,
                    permissionCoordinator = PermissionCoordinator(
                        cameraPermissionGranted = true,
                        notificationPermissionGranted = true,
                        audioPermissionGranted = true,
                        locationPermissionGranted = true,
                        bluetoothPermissionGranted = true,
                        requestStartRecording = {
                            permissionStartRequests++
                            statusMessage = "正在启动后摄录制..."
                            controller.start(
                                context = context,
                                dualCamera = true,
                                ambientAudio = false,
                            )
                            isRecording = true
                            statusMessage = "双摄录制中"
                            sessionStatusFlow.value = previewSessionStatus(sessionToken = 2, recordingActive = true)
                        },
                        requestCameraPermission = {},
                        requestNotificationPermission = {},
                        requestAudioPermission = {},
                        requestLocationPermission = {},
                        requestBluetoothPermission = {},
                    ),
                    recordingServiceController = controller,
                    onRecordingStopped = {
                        isRecording = false
                        statusMessage = "录制服务已停止。"
                        sessionStatusFlow.value = previewSessionStatus(sessionToken = 1, recordingActive = false)
                    },
                    onStatus = { statusMessage = it },
                    onRefreshRecordingData = { controller.refreshCalls++ },
                    onDualCameraTelemetry = telemetryStore::record,
                    dualCameraSessionStatusFlow = sessionStatusFlow,
                    previewContent = { frontInsetEnabled, _ ->
                        Box(
                            modifier = Modifier
                                .width(180.dp)
                                .height(100.dp)
                                .testTag("rear_camera_preview"),
                        )
                        if (frontInsetEnabled) {
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(36.dp)
                                    .testTag("front_inset_preview"),
                            )
                        }
                    },
                )
            }

            composeRule.onNodeWithTag("front_inset_preview").assertIsDisplayed()
            composeRule.onNodeWithTag("dual_camera_telemetry_summary")
                .assertTextContains("双摄 Session 1 · 并发预览已绑定")

            composeRule.onNodeWithTag("recording_toggle_button").performClick()

            assertEquals(1, permissionStartRequests)
            assertEquals(1, controller.startCalls)
            composeRule.onNodeWithTag("front_inset_preview").assertIsDisplayed()
            composeRule.onNodeWithTag("dual_camera_telemetry_summary")
                .assertTextContains("双摄 Session 2 · 并发预览/录制已绑定")

            composeRule.onNodeWithText("紧急锁定").performClick()

            assertEquals(1, controller.lockCalls)
            assertEquals(1, controller.refreshCalls)

            composeRule.onNodeWithTag("recording_toggle_button").performClick()

            assertEquals(1, controller.stopCalls)
            composeRule.waitUntil(timeoutMillis = 5_000) {
                telemetryStore.load()?.summary == "双摄 Session 1 · 并发预览已绑定"
            }

            val persisted = telemetryStore.load()
            assertNotNull(persisted)
            assertEquals("双摄 Session 1 · 并发预览已绑定", persisted?.summary)
            assertEquals("后摄预览已连接 · 前摄预览已连接", persisted?.detail)
        } finally {
            telemetryStore.clear()
        }
    }
}

class FakeRecordingServiceController : RecordingServiceController {
    var startCalls = 0
    var stopCalls = 0
    var lockCalls = 0
    var refreshCalls = 0
    var gpsMetadataCalls = 0
    var lastGpsMetadataEnabled: Boolean? = null

    override fun start(context: android.content.Context, dualCamera: Boolean, ambientAudio: Boolean) {
        startCalls++
    }

    override fun stop(context: android.content.Context) {
        stopCalls++
    }

    override fun lockCurrent(context: android.content.Context) {
        lockCalls++
    }

    override fun setGpsMetadataEnabled(context: android.content.Context, enabled: Boolean) {
        gpsMetadataCalls++
        lastGpsMetadataEnabled = enabled
    }
}

private fun previewSessionStatus(
    sessionToken: Int,
    recordingActive: Boolean,
): DualCameraSessionStatus {
    return DualCameraSessionStatus(
        previewSessionToken = sessionToken,
        concurrentCameraActive = true,
        recordingActive = recordingActive,
        rearPreviewAttached = true,
        frontPreviewAttached = true,
    )
}
