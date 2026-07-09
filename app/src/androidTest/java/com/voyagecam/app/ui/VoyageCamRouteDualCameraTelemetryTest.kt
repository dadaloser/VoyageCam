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
                        summary = "双摄 Session 2 · 已回落到后摄预览",
                        detail = "后摄预览已连接 · 前摄预览已连接",
                        diagnostic = "双摄会话：bind failed",
                    ),
                )
            }

            composeRule.setContent {
                VoyageCamRoute()
            }

            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("双摄会话状态")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasScrollAction())
                .performScrollToNode(hasText("双摄会话状态"))

            composeRule.onNodeWithText("双摄诊断").assertIsDisplayed()
            composeRule.onNodeWithText("双摄会话状态").assertIsDisplayed()
            composeRule.onNodeWithText("双摄 Session 2 · 已回落到后摄预览").assertIsDisplayed()
            composeRule.onNodeWithText("双摄会话：bind failed").assertIsDisplayed()

            composeRule.onAllNodesWithText("清空")[0].performClick()
            composeRule.onAllNodesWithText("清空")[1].performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("暂无双摄会话记录。")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasScrollAction())
                .performScrollToNode(hasText("暂无双摄降级记录。"))

            composeRule.onNodeWithText("暂无双摄降级记录。").assertIsDisplayed()
            composeRule.onNodeWithText("暂无双摄会话记录。").assertIsDisplayed()
        } finally {
            runBlocking {
                diagnosticStore.clear()
                telemetryStore.clear()
            }
        }
    }
}
