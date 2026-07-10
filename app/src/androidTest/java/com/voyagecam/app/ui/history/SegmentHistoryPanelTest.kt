package com.voyagecam.app.ui.history

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.core.model.RecordingSegment
import com.voyagecam.app.feature.evidence.RecordingClipExportMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SegmentHistoryPanelTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun groupedTimelineAllowsDualPackageExport() {
        var exportedGroupKey: String? = null
        var exportedMode: RecordingClipExportMode? = null
        val segments = listOf(
            testSegment(
                name = "20260710_120000_rear.mp4",
                groupKey = "20260710/20260710_120000",
                direction = CameraDirection.Rear,
                absolutePath = "/tmp/rear.mp4",
            ),
            testSegment(
                name = "20260710_120000_front.mp4",
                groupKey = "20260710/20260710_120000",
                direction = CameraDirection.Front,
                absolutePath = "/tmp/front.mp4",
            ),
        )

        composeRule.setContent {
            SegmentHistoryPanel(
                segments = segments,
                allSegments = segments,
                totalSegmentCount = segments.size,
                exportState = null,
                availableDays = listOf("20260710"),
                selectedDay = null,
                selectedCameraFilter = SegmentCameraFilter.All,
                selectedLockFilter = SegmentLockFilter.All,
                onSelectedDayChanged = {},
                onCameraFilterChanged = {},
                onLockFilterChanged = {},
                onRefresh = {},
                onOpen = {},
                onShare = {},
                onExportGroup = { groupKey, mode ->
                    exportedGroupKey = groupKey
                    exportedMode = mode
                },
                onCancelExport = {},
                onShareExport = {},
                onDismissExport = {},
                onUnlock = {},
                onDelete = {},
            )
        }

        composeRule.onNodeWithTag("history_export_button_20260710_20260710_120000")
            .performClick()
        composeRule.onNodeWithTag("history_export_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("history_export_mode_dual").performClick()

        assertEquals("20260710/20260710_120000", exportedGroupKey)
        assertEquals(RecordingClipExportMode.DualPackage, exportedMode)
    }

    @Test
    fun rearOnlyGroupDisablesFrontAndDualExportModes() {
        val segments = listOf(
            testSegment(
                name = "20260710_130000_rear.mp4",
                groupKey = "20260710/20260710_130000",
                direction = CameraDirection.Rear,
                absolutePath = "/tmp/rear_only.mp4",
            ),
        )

        composeRule.setContent {
            SegmentHistoryPanel(
                segments = segments,
                allSegments = segments,
                totalSegmentCount = segments.size,
                exportState = null,
                availableDays = listOf("20260710"),
                selectedDay = null,
                selectedCameraFilter = SegmentCameraFilter.All,
                selectedLockFilter = SegmentLockFilter.All,
                onSelectedDayChanged = {},
                onCameraFilterChanged = {},
                onLockFilterChanged = {},
                onRefresh = {},
                onOpen = {},
                onShare = {},
                onExportGroup = { _, _ -> },
                onCancelExport = {},
                onShareExport = {},
                onDismissExport = {},
                onUnlock = {},
                onDelete = {},
            )
        }

        composeRule.onNodeWithTag("history_export_button_20260710_20260710_130000")
            .performClick()
        composeRule.onNodeWithTag("history_export_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("history_export_mode_rear").assertIsDisplayed()
        composeRule.onNodeWithTag("history_export_mode_front").assertIsNotEnabled()
        composeRule.onNodeWithTag("history_export_mode_dual").assertIsNotEnabled()
    }

    private fun testSegment(
        name: String,
        groupKey: String,
        direction: CameraDirection,
        absolutePath: String,
    ): RecordingSegment {
        return RecordingSegment(
            name = name,
            relativePath = groupKey,
            groupKey = groupKey,
            absolutePath = absolutePath,
            day = "20260710",
            cameraDirection = direction,
            locked = false,
            sizeBytes = 1024L,
            lastModifiedMillis = 1_720_000_000_000L,
        )
    }
}
