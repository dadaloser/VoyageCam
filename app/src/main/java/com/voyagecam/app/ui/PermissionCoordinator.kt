package com.voyagecam.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.voyagecam.app.data.location.hasAnyLocationPermission
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.feature.recording.RecordingForegroundService

data class PermissionCoordinator(
    val cameraPermissionGranted: Boolean,
    val notificationPermissionGranted: Boolean,
    val audioPermissionGranted: Boolean,
    val locationPermissionGranted: Boolean,
    val bluetoothPermissionGranted: Boolean,
    val requestStartRecording: () -> Unit,
    val requestCameraPermission: () -> Unit,
    val requestNotificationPermission: () -> Unit,
    val requestAudioPermission: () -> Unit,
    val requestLocationPermission: () -> Unit,
    val requestBluetoothPermission: () -> Unit,
)

@Composable
fun rememberPermissionCoordinator(
    context: Context,
    settings: VoyageCamSettings,
    isRecording: Boolean,
    pendingGpsMetadataEnable: Boolean,
    onRedetect: () -> Unit,
    onBeginRecording: () -> Unit,
    onPersistSettings: (VoyageCamSettings) -> Unit,
    onApplyGpsMetadataSetting: (Boolean) -> Unit,
    onSetPendingGpsMetadataEnable: (Boolean) -> Unit,
    onSetStatus: (String) -> Unit,
    onBluetoothPermissionGranted: () -> Unit,
): PermissionCoordinator {
    var pendingStart by rememberSaveable { mutableStateOf(false) }
    var permissionRefreshKey by remember { mutableIntStateOf(0) }

    fun hasPermission(permission: String): Boolean {
        return permissionRefreshKey >= 0 &&
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun hasLocationPermission(): Boolean {
        return context.hasAnyLocationPermission()
    }

    fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRefreshKey++
        if (granted) {
            onRedetect()
            if (pendingStart && hasNotificationPermission()) {
                pendingStart = false
                onBeginRecording()
            }
        } else {
            pendingStart = false
            onSetStatus("相机权限未授权，无法开始行车记录。")
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRefreshKey++
        if (granted && pendingStart && hasPermission(Manifest.permission.CAMERA)) {
            pendingStart = false
            onBeginRecording()
        } else if (!granted) {
            pendingStart = false
            onSetStatus("通知权限未授权，后台录制状态无法可靠展示。")
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRefreshKey++
        onPersistSettings(settings.copy(ambientAudioEnabled = granted))
        onSetStatus(
            if (granted) {
                "行车环境声已开启；下一次录制会带音频。"
            } else {
                "麦克风权限未授权，已保持静音录制。"
            },
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        permissionRefreshKey++
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && pendingGpsMetadataEnable) {
            onApplyGpsMetadataSetting(true)
        } else if (!granted && pendingGpsMetadataEnable) {
            onSetPendingGpsMetadataEnable(false)
            onPersistSettings(settings.copy(gpsMetadataEnabled = false))
            onSetStatus("定位权限未授权；已保持关闭GPS位置与轨迹记录。")
        } else {
            onSetStatus(
                if (granted) {
                    if (settings.gpsMetadataEnabled && isRecording) {
                        RecordingForegroundService.setGpsMetadataEnabled(context, true)
                    }
                    "定位权限已授权；紧急事件可记录最近可用坐标。"
                } else {
                    "定位权限未授权；紧急事件仍会记录时间、触发类型和片段。"
                },
            )
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRefreshKey++
        if (granted) onBluetoothPermissionGranted()
        onSetStatus(
            if (granted) {
                "蓝牙权限已授权；可信蓝牙连接后可自动开始录制。"
            } else {
                "蓝牙权限未授权，可信蓝牙自动启动不可用。"
            },
        )
    }

    fun requestStartRecording() {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            pendingStart = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        if (!hasNotificationPermission()) {
            pendingStart = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        onBeginRecording()
    }

    return PermissionCoordinator(
        cameraPermissionGranted = hasPermission(Manifest.permission.CAMERA),
        notificationPermissionGranted = hasNotificationPermission(),
        audioPermissionGranted = hasPermission(Manifest.permission.RECORD_AUDIO),
        locationPermissionGranted = hasLocationPermission(),
        bluetoothPermissionGranted = hasBluetoothConnectPermission(),
        requestStartRecording = ::requestStartRecording,
        requestCameraPermission = {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        },
        requestNotificationPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        requestAudioPermission = {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
        requestLocationPermission = {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        },
        requestBluetoothPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        },
    )
}
