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

class RecordingPanelDualCameraFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun keepsFrontInsetVisibleDuringRecordingAndPersistsTelemetryAfterStop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val telemetryStore = DualCameraSessionTelemetryStore(context)
        val sessionStatusFlow = MutableStateFlow(previewSessionStatus(sessionToken = 1, recordingActive = false))

        telemetryStore.clear()
        try {
            composeRule.setContent {
                var isRecording = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                RecordingPanel(
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
                    isRecording = isRecording.value,
                    statusMessage = if (isRecording.value) "双摄录制中" else "等待开始",
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

            composeRule.onNodeWithTag("rear_camera_preview").assertIsDisplayed()
            composeRule.onNodeWithTag("front_inset_preview").assertIsDisplayed()
            composeRule.onNodeWithTag("dual_camera_telemetry_summary")
                .assertTextContains("双摄 Session 1 · 并发预览已绑定")

            composeRule.onNodeWithTag("recording_toggle_button").performClick()

            composeRule.onNodeWithTag("front_inset_preview").assertIsDisplayed()
            composeRule.onNodeWithTag("dual_camera_telemetry_summary")
                .assertTextContains("双摄 Session 2 · 并发预览/录制已绑定")

            composeRule.onNodeWithTag("recording_toggle_button").performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                telemetryStore.load()?.summary == "双摄 Session 1 · 并发预览已绑定"
            }

            composeRule.onNodeWithTag("front_inset_preview").assertIsDisplayed()
            composeRule.onNodeWithTag("dual_camera_telemetry_summary")
                .assertTextContains("双摄 Session 1 · 并发预览已绑定")

            val persisted = telemetryStore.load()
            assertNotNull(persisted)
            assertEquals("双摄 Session 1 · 并发预览已绑定", persisted?.summary)
            assertEquals("后摄预览已连接 · 前摄预览已连接", persisted?.detail)
        } finally {
            telemetryStore.clear()
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
