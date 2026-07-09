package com.voyagecam.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.voyagecam.app.R
import com.voyagecam.app.core.model.DualCameraDiagnostic
import com.voyagecam.app.core.model.DualCameraDiagnosticStage
import com.voyagecam.app.data.camera.DualCameraDiagnosticsStore
import com.voyagecam.app.data.camera.DualCameraSessionTelemetryStore
import com.voyagecam.app.ui.preview.DualCameraTelemetryPresentation
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class VoyageCamRouteDualCameraTelemetryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun routeLoadsPersistedDualCameraRecordsAndClearsThemFromSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val diagnosticStore = DualCameraDiagnosticsStore(context)
        val telemetryStore = DualCameraSessionTelemetryStore(context)
        val sessionTitle = context.getString(R.string.settings_dual_camera_session_title)
        val diagnosticTitle = context.getString(R.string.settings_dual_camera_diagnostic_title)
        val clearLabel = context.getString(R.string.settings_clear)
        val emptyDiagnostic = context.getString(R.string.settings_dual_camera_diagnostic_empty)
        val emptySession = context.getString(R.string.settings_dual_camera_session_empty)
        val summary = context.getString(
            R.string.preview_telemetry_summary,
            2,
            context.getString(R.string.preview_telemetry_state_rear_fallback),
        )
        val detail = context.getString(
            R.string.preview_telemetry_detail,
            context.getString(R.string.preview_telemetry_detail_rear),
            context.getString(R.string.preview_telemetry_connected),
            context.getString(R.string.preview_telemetry_detail_front),
            context.getString(R.string.preview_telemetry_connected),
        )
        val diagnostic = context.getString(
            R.string.preview_dual_camera_diagnostic_summary,
            context.getString(R.string.label_dual_camera_stage_session),
            "bind failed",
        )

        try {
            runBlocking {
                diagnosticStore.clear()
                telemetryStore.clear()
                diagnosticStore.record(
                    DualCameraDiagnostic(
                        stage = DualCameraDiagnosticStage.Session,
                        detail = "bind failed",
                    ),
                )
                telemetryStore.record(
                    DualCameraTelemetryPresentation(
                        summary = summary,
                        detail = detail,
                        diagnostic = diagnostic,
                    ),
                )
            }

            composeRule.setContent {
                VoyageCamRoute()
            }

            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText(sessionTitle)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasScrollAction())
                .performScrollToNode(hasText(sessionTitle))

            composeRule.onNodeWithText(diagnosticTitle).assertIsDisplayed()
            composeRule.onNodeWithText(sessionTitle).assertIsDisplayed()
            composeRule.onNodeWithText(summary).assertIsDisplayed()
            composeRule.onNodeWithText(diagnostic).assertIsDisplayed()

            composeRule.onAllNodesWithText(clearLabel)[0].performClick()
            composeRule.onAllNodesWithText(clearLabel)[1].performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText(emptySession)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasScrollAction())
                .performScrollToNode(hasText(emptyDiagnostic))

            composeRule.onNodeWithText(emptyDiagnostic).assertIsDisplayed()
            composeRule.onNodeWithText(emptySession).assertIsDisplayed()
        } finally {
            runBlocking {
                diagnosticStore.clear()
                telemetryStore.clear()
            }
        }
    }
}
