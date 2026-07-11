package com.voyagecam.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.voyagecam.app.R
import com.voyagecam.app.core.camera.DualCameraSessionStatus
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.core.model.DeviceCapabilityGrade
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.DualCameraDiagnostic
import com.voyagecam.app.core.model.DualCameraDiagnosticStage
import com.voyagecam.app.core.model.DualCameraSwitchState
import com.voyagecam.app.data.settings.RecordingOrientationStrategy
import com.voyagecam.app.data.settings.RecordingMode
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.ui.preview.DualCameraPreviewPresentation
import com.voyagecam.app.ui.preview.DualCameraPreviewLayout
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import org.junit.Assert.assertTrue

class RecordingPanelDualPreviewInteractionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun keepsSwappedMainPreviewAcrossRecordingAndStateRestore() {
        val restorationTester = StateRestorationTester(composeRule)
        val sessionStatusFlow = MutableStateFlow(previewSessionStatus(sessionToken = 1, recordingActive = false))
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        restorationTester.setContent {
            var isRecording by remember { mutableStateOf(false) }
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
                isRecording = isRecording,
                statusMessage = if (isRecording) {
                    context.getString(R.string.route_recording_state_recording)
                } else {
                    context.getString(R.string.route_recording_state_ready)
                },
                onToggleRecording = {
                    val next = !isRecording
                    isRecording = next
                    sessionStatusFlow.value = if (next) {
                        previewSessionStatus(sessionToken = 2, recordingActive = true)
                    } else {
                        previewSessionStatus(sessionToken = 1, recordingActive = false)
                    }
                },
                onEmergencyLock = {},
                onDualCameraTelemetry = {},
                dualCameraSessionStatusFlow = sessionStatusFlow,
                previewContent = { presentation ->
                    previewLayoutStub(presentation)
                },
            )
        }

        composeRule.onNodeWithTag("rear_main_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("front_inset_preview").assertIsDisplayed()

        composeRule.onNodeWithTag("dual_preview_swap_button").performClick()

        composeRule.onNodeWithTag("front_main_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("rear_inset_preview").assertIsDisplayed()

        composeRule.onNodeWithTag("recording_toggle_button").performClick()

        composeRule.onNodeWithTag("front_main_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("rear_inset_preview").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("front_main_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("rear_inset_preview").assertIsDisplayed()
    }

    @Test
    fun hidesSwapActionAndShowsDiagnosticWhenDualPreviewFallsBackToRear() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionStatusFlow = MutableStateFlow(
            DualCameraSessionStatus(
                previewSessionToken = 1,
                concurrentCameraActive = false,
                recordingActive = false,
                rearPreviewAttached = true,
                frontPreviewAttached = false,
                lastDiagnostic = DualCameraDiagnostic(
                    stage = DualCameraDiagnosticStage.Preview,
                    detail = "bind failed",
                ),
            ),
        )

        composeRule.setContent {
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
                isRecording = false,
                statusMessage = context.getString(R.string.route_recording_state_ready),
                onToggleRecording = {},
                onEmergencyLock = {},
                onDualCameraTelemetry = {},
                dualCameraSessionStatusFlow = sessionStatusFlow,
                previewContent = { presentation ->
                    previewLayoutStub(presentation)
                },
            )
        }

        composeRule.onAllNodesWithTag("dual_preview_swap_button").assertCountEquals(0)
        composeRule.onNodeWithTag("dual_camera_telemetry_summary")
            .assertTextContains(context.getString(R.string.preview_telemetry_state_rear_fallback))
        composeRule.onNodeWithText(
            context.getString(
                R.string.preview_dual_camera_diagnostic_summary,
                context.getString(R.string.label_dual_camera_stage_preview),
                "bind failed",
            ),
        ).assertIsDisplayed()
    }

    @Test
    fun draggableInsetPreviewSnapsToNearestEdge() {
        var mainCameraDirection by mutableStateOf(CameraDirection.Rear)
        composeRule.setContent {
            DualCameraPreviewLayout(
                mainCameraDirection = mainCameraDirection,
                frontMirrorEnabled = true,
                orientationStrategy = RecordingOrientationStrategy.FixedLandscapeDriving,
                modifier = Modifier
                    .width(240.dp)
                    .testTag("dual_camera_preview"),
                rearPreview = { surfaceModifier ->
                    Box(modifier = surfaceModifier)
                },
                frontPreview = { surfaceModifier ->
                    Box(modifier = surfaceModifier)
                },
            )
        }

        val initialBounds = composeRule.onNodeWithTag("front_inset_preview")
            .fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithTag("front_inset_preview").performTouchInput {
            down(center)
            moveBy(Offset(-220f, 90f))
            up()
        }

        composeRule.waitForIdle()

        val previewBounds = composeRule.onNodeWithTag("dual_camera_preview")
            .fetchSemanticsNode().boundsInRoot
        val snappedBounds = composeRule.onNodeWithTag("front_inset_preview")
            .fetchSemanticsNode().boundsInRoot
        val expectedPadding = with(composeRule.density) { 12.dp.toPx() }

        assertTrue(snappedBounds.left < initialBounds.left)
        assertTrue(snappedBounds.top > initialBounds.top)
        assertTrue(abs(snappedBounds.left - (previewBounds.left + expectedPadding)) < 4f)

        composeRule.runOnUiThread {
            mainCameraDirection = CameraDirection.Front
        }

        composeRule.waitForIdle()

        val swappedInsetBounds = composeRule.onNodeWithTag("rear_inset_preview")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(swappedInsetBounds.left - snappedBounds.left) < 4f)
        assertTrue(abs(swappedInsetBounds.top - snappedBounds.top) < 4f)
    }
}

@Composable
private fun previewLayoutStub(presentation: DualCameraPreviewPresentation) {
    Box(
        modifier = Modifier
            .width(180.dp)
            .height(100.dp)
            .testTag("rear_camera_preview"),
    )
    Box(
        modifier = Modifier
            .width(180.dp)
            .height(100.dp)
            .testTag(
                if (presentation.mainCameraDirection == CameraDirection.Rear) {
                    "rear_main_preview"
                } else {
                    "front_main_preview"
                },
            ),
    )
    when (presentation.insetCameraDirection) {
        CameraDirection.Front -> {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(36.dp)
                    .testTag("front_inset_preview"),
            )
        }

        CameraDirection.Rear -> {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(36.dp)
                    .testTag("rear_inset_preview"),
            )
        }

        null -> Unit
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
