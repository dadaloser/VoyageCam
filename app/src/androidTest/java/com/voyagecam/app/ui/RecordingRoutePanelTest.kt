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
import com.voyagecam.app.R
import com.voyagecam.app.core.camera.DualCameraSessionStatus
import com.voyagecam.app.core.model.DeviceCapabilityGrade
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.DualCameraSwitchState
import com.voyagecam.app.data.camera.DualCameraSessionTelemetryStore
import com.voyagecam.app.data.settings.RecordingMode
import com.voyagecam.app.data.settings.VoyageCamSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class RecordingRoutePanelTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun clickingStartDelegatesToPermissionCoordinatorBeforeStartingService() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val controller = FakeRecordingServiceController()
        var permissionStartRequests = 0

        composeRule.setContent {
            RecordingRoutePanel(
                settings = VoyageCamSettings(
                    recordingMode = RecordingMode.Auto,
                    storageCapacityGb = 10,
                    segmentDurationMinutes = 3,
                ),
                capability = DualCameraCapability(
                    state = DualCameraSwitchState.AvailableOn,
                    grade = DeviceCapabilityGrade.A,
                    reason = "supported",
                ),
                isRecording = false,
                statusMessage = context.getString(R.string.route_recording_state_ready),
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
        val readyStatus = context.getString(R.string.route_recording_state_ready)
        val startingStatus = context.getString(R.string.route_starting_mode, context.getString(R.string.recording_mode_auto_dual_active))
        val dualRecordingStatus = "Dual recording active"
        val stoppedStatus = context.getString(R.string.vm_recording_stopped)
        val previewSummary = context.getString(
            R.string.preview_telemetry_summary,
            1,
            context.getString(R.string.preview_telemetry_state_bound_preview),
        )
        val recordingSummary = context.getString(
            R.string.preview_telemetry_summary,
            2,
            context.getString(R.string.preview_telemetry_state_bound_recording),
        )
        val previewDetail = context.getString(
            R.string.preview_telemetry_detail,
            context.getString(R.string.preview_telemetry_detail_rear),
            context.getString(R.string.preview_telemetry_connected),
            context.getString(R.string.preview_telemetry_detail_front),
            context.getString(R.string.preview_telemetry_connected),
        )
        val emergencyLockLabel = context.getString(R.string.route_emergency_lock_button)

        runBlocking {
            telemetryStore.clear()
        }
        try {
            composeRule.setContent {
                var isRecording by remember { mutableStateOf(false) }
                var statusMessage by remember { mutableStateOf(readyStatus) }

                RecordingRoutePanel(
                    settings = VoyageCamSettings(
                        recordingMode = RecordingMode.Auto,
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
                            statusMessage = startingStatus
                            controller.start(
                                context = context,
                                dualCamera = true,
                                ambientAudio = false,
                            )
                            isRecording = true
                            statusMessage = dualRecordingStatus
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
                        statusMessage = stoppedStatus
                        sessionStatusFlow.value = previewSessionStatus(sessionToken = 1, recordingActive = false)
                    },
                    onStatus = { statusMessage = it },
                    onRefreshRecordingData = { controller.refreshCalls++ },
                    onDualCameraTelemetry = { telemetry ->
                        runBlocking {
                            telemetryStore.record(telemetry)
                        }
                    },
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
                .assertTextContains(previewSummary)

            composeRule.onNodeWithTag("recording_toggle_button").performClick()

            assertEquals(1, permissionStartRequests)
            assertEquals(1, controller.startCalls)
            composeRule.onNodeWithTag("front_inset_preview").assertIsDisplayed()
            composeRule.onNodeWithTag("dual_camera_telemetry_summary")
                .assertTextContains(recordingSummary)

            composeRule.onNodeWithText(emergencyLockLabel).performClick()

            assertEquals(1, controller.lockCalls)
            assertEquals(1, controller.refreshCalls)

            composeRule.onNodeWithTag("recording_toggle_button").performClick()

            assertEquals(1, controller.stopCalls)
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runBlocking {
                    telemetryStore.load()?.summary == previewSummary
                }
            }

            val persisted = runBlocking { telemetryStore.load() }
            assertNotNull(persisted)
            assertEquals(previewSummary, persisted?.summary)
            assertEquals(previewDetail, persisted?.detail)
        } finally {
            runBlocking {
                telemetryStore.clear()
            }
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
