package com.voyagecam.app.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.ui.settings.BluetoothDevicePickerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VoyageCamRouteAssemblyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun routeUsesInjectedFactoriesAndPassesAssemblyDependenciesIntoContent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val viewModel = VoyageCamViewModel(context.applicationContext as Application)
        viewModel.persistSettings(
            VoyageCamSettings(
                trustedBluetoothDevice = "AA:BB:CC:DD:EE:FF",
            ),
        )

        val controller = FakeRecordingServiceController()
        var permissionFactoryCalls = 0
        var permissionStartRequests = 0
        var capturedTrustedDeviceInput: String? = null
        var capturedControllerMatches = false

        composeRule.setContent {
            VoyageCamRoute(
                recordingServiceController = controller,
                viewModelProvider = { viewModel },
                permissionCoordinatorFactory = { params ->
                    permissionFactoryCalls++
                    PermissionCoordinator(
                        cameraPermissionGranted = true,
                        notificationPermissionGranted = true,
                        audioPermissionGranted = true,
                        locationPermissionGranted = true,
                        bluetoothPermissionGranted = true,
                        requestStartRecording = { permissionStartRequests++ },
                        requestCameraPermission = {},
                        requestNotificationPermission = {},
                        requestAudioPermission = {},
                        requestLocationPermission = {},
                        requestBluetoothPermission = {},
                    )
                },
                bluetoothDevicePickerStateFactory = { _, trustedDeviceInput ->
                    capturedTrustedDeviceInput = trustedDeviceInput
                    BluetoothDevicePickerState(
                        loadPairedDevices = { emptyList() },
                        trustedDeviceInput = trustedDeviceInput,
                    )
                },
                routeContentOverride = { uiState, permissionCoordinator, bluetoothDevicePickerState, recordingServiceController ->
                    capturedControllerMatches = recordingServiceController === controller
                    Text(
                        text = uiState.settings.trustedBluetoothDevice,
                        modifier = Modifier.testTag("route_trusted_device"),
                    )
                    Text(
                        text = bluetoothDevicePickerState.trustedDeviceInput,
                        modifier = Modifier.testTag("route_bt_picker_input"),
                    )
                    Button(
                        onClick = permissionCoordinator.requestStartRecording,
                        modifier = Modifier.testTag("route_permission_start_button"),
                    ) {
                        Text("Start via permissions")
                    }
                },
            )
        }

        composeRule.onNodeWithTag("route_trusted_device")
            .assertTextContains("AA:BB:CC:DD:EE:FF")
        composeRule.onNodeWithTag("route_bt_picker_input")
            .assertTextContains("AA:BB:CC:DD:EE:FF")

        composeRule.onNodeWithTag("route_permission_start_button").performClick()

        assertTrue(capturedControllerMatches)
        assertEquals(1, permissionFactoryCalls)
        assertEquals("AA:BB:CC:DD:EE:FF", capturedTrustedDeviceInput)
        assertEquals(1, permissionStartRequests)
        assertEquals(0, controller.startCalls)
    }
}
