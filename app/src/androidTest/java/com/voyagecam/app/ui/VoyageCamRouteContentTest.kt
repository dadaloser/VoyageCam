package com.voyagecam.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.voyagecam.app.core.model.CollisionSensitivity
import com.voyagecam.app.core.model.DeviceCapabilityGrade
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.DualCameraSwitchState
import com.voyagecam.app.core.model.RecordingStorageOverview
import com.voyagecam.app.data.settings.StorageCapacityLimit
import com.voyagecam.app.data.settings.RecordingMode
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.ui.settings.BluetoothDevicePickerState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VoyageCamRouteContentTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun gpsToggleDuringRecordingUpdatesContentStateAndServiceController() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val controller = FakeRecordingServiceController()
        var gpsApplyCalls = 0

        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    VoyageCamUiState(
                        settings = VoyageCamSettings(
                            recordingMode = RecordingMode.Auto,
                            storageCapacityGb = 10,
                            segmentDurationMinutes = 3,
                            collisionSensitivity = CollisionSensitivity.Medium,
                            gpsMetadataEnabled = false,
                        ),
                        capability = DualCameraCapability(
                            state = DualCameraSwitchState.AvailableOn,
                            grade = DeviceCapabilityGrade.A,
                            reason = "supported",
                        ),
                        storageLimit = StorageCapacityLimit(maxGb = 64),
                        statusMessage = "Dual recording active",
                        isRecording = true,
                        storageOverview = RecordingStorageOverview(
                            normalBytes = 0L,
                            lockedBytes = 0L,
                            normalClipCount = 0,
                            lockedClipCount = 0,
                            maxStorageBytes = 10L * 1024L * 1024L * 1024L,
                            estimatedBytesPerMinute = 1024L * 1024L,
                        ),
                    ),
                )
            }

            VoyageCamRouteContent(
                uiState = uiState,
                permissionCoordinator = PermissionCoordinator(
                    cameraPermissionGranted = true,
                    notificationPermissionGranted = true,
                    audioPermissionGranted = true,
                    locationPermissionGranted = true,
                    bluetoothPermissionGranted = true,
                    requestStartRecording = {},
                    requestCameraPermission = {},
                    requestNotificationPermission = {},
                    requestAudioPermission = {},
                    requestLocationPermission = {},
                    requestBluetoothPermission = {},
                ),
                bluetoothDevicePickerState = BluetoothDevicePickerState(
                    loadPairedDevices = { emptyList() },
                    trustedDeviceInput = "",
                ),
                recordingServiceController = controller,
                onApplyGpsMetadataSetting = { enabled ->
                    gpsApplyCalls++
                    controller.setGpsMetadataEnabled(context, enabled)
                    uiState = uiState.copy(
                        settings = uiState.settings.copy(gpsMetadataEnabled = enabled),
                    )
                },
            )
        }

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("gps_metadata_switch"))
        composeRule.onNodeWithTag("gps_metadata_switch").performClick()

        assertEquals(1, gpsApplyCalls)
        assertEquals(1, controller.gpsMetadataCalls)
        assertEquals(true, controller.lastGpsMetadataEnabled)
    }
}
