package com.voyagecam.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import com.voyagecam.app.ui.preview.frontInsetVisible
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class RecordingPanelDualCameraFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun keepsFrontInsetVisibleDuringRecordingAndPersistsTelemetryAfterStop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val telemetryStore = DualCameraSessionTelemetryStore(context)
        val sessionStatusFlow = MutableStateFlow(previewSessionStatus(sessionToken = 1, recordingActive = false))
        val readyStatus = context.getString(R.string.route_recording_state_ready)
        val dualRecordingStatus = "Dual recording active"
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

        runBlocking {
            telemetryStore.clear()
        }
        try {
            composeRule.setContent {
                var isRecording = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                RecordingPanel(
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
                    isRecording = isRecording.value,
                    statusMessage = if (isRecording.value) dualRecordingStatus else readyStatus,
                    onToggleRecording = {
                        val nextRecording = !isRecording.value
                        isRecording.value = nextRecording
                        sessionStatusFlow.value = if (nextRecording) {
                            previewSessionStatus(sessionToken = 2, recordingActive = true)
                        } else {
                            previewSessionStatus(sessionToken = 1, recordingActive = false)
                        }
                    },
                    onEmergencyLock = {},
                    onDualCameraTelemetry = { telemetry ->
                        runBlocking {
                            telemetryStore.record(telemetry)
                        }
                    },
                    dualCameraSessionStatusFlow = sessionStatusFlow,
                    previewContent = { previewPresentation ->
                        Box(
                            modifier = Modifier
                                .width(180.dp)
                                .height(100.dp)
                                .testTag("rear_camera_preview"),
                        )
                        if (previewPresentation.frontInsetVisible) {
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

            composeRule.onNodeWithTag("rear_camera_preview").assertIsDisplayed()
            composeRule.onNodeWithTag("front_inset_preview").assertIsDisplayed()
            composeRule.onNodeWithTag("dual_camera_telemetry_summary")
                .assertTextContains(previewSummary)

            composeRule.onNodeWithTag("recording_toggle_button").performClick()

            composeRule.onNodeWithTag("front_inset_preview").assertIsDisplayed()
            composeRule.onNodeWithTag("dual_camera_telemetry_summary")
                .assertTextContains(recordingSummary)

            composeRule.onNodeWithTag("recording_toggle_button").performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runBlocking {
                    telemetryStore.load()?.summary == previewSummary
                }
            }

            composeRule.onNodeWithTag("front_inset_preview").assertIsDisplayed()
            composeRule.onNodeWithTag("dual_camera_telemetry_summary")
                .assertTextContains(previewSummary)

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
