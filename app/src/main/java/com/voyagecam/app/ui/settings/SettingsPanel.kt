package com.voyagecam.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.voyagecam.app.core.model.AutoStartDiagnostic
import com.voyagecam.app.core.model.AutoStartResult
import com.voyagecam.app.core.model.CollisionSensitivity
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.DualCameraSwitchState
import com.voyagecam.app.core.model.PersistedDualCameraDiagnostic
import com.voyagecam.app.core.model.PersistedDualCameraSessionTelemetry
import com.voyagecam.app.core.model.RecordingStorageOverview
import com.voyagecam.app.core.model.TrustedBluetoothDevice
import com.voyagecam.app.data.settings.StorageCapacityLimit
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.data.settings.VoyageCamSettingsStore
import com.voyagecam.app.ui.theme.SectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsPanel(
    settings: VoyageCamSettings,
    capability: DualCameraCapability,
    isRecording: Boolean,
    storageLimit: StorageCapacityLimit,
    cameraPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    audioPermissionGranted: Boolean,
    locationPermissionGranted: Boolean,
    bluetoothPermissionGranted: Boolean,
    storageOverview: RecordingStorageOverview,
    pendingStorageCapacityGb: Int?,
    onRequestCameraPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onRedetect: () -> Unit,
    onDualCameraChanged: (Boolean) -> Unit,
    onStorageChanged: (Int) -> Unit,
    onCleanupStorage: () -> Unit,
    onSegmentDurationChanged: (Int) -> Unit,
    onCollisionSensitivityChanged: (CollisionSensitivity) -> Unit,
    onAmbientAudioChanged: (Boolean) -> Unit,
    onGpsMetadataChanged: (Boolean) -> Unit,
    onExportWatermarkSubtitlesChanged: (Boolean) -> Unit,
    onAutoStartOnPowerChanged: (Boolean) -> Unit,
    onTrustedBluetoothDeviceChanged: (String) -> Unit,
    onAutoStartOnTrustedBluetoothChanged: (Boolean) -> Unit,
    onRequestResetSettings: () -> Unit,
    autoStartDiagnostic: AutoStartDiagnostic?,
    dualCameraDiagnostic: PersistedDualCameraDiagnostic?,
    dualCameraSessionTelemetry: PersistedDualCameraSessionTelemetry?,
    onRefreshAutoStartDiagnostic: () -> Unit,
    onRefreshDualCameraDiagnostic: () -> Unit,
    onRefreshDualCameraSessionTelemetry: () -> Unit,
    bluetoothDevicePickerState: BluetoothDevicePickerState,
) {
    val visibleStorageCapacityGb = pendingStorageCapacityGb ?: settings.storageCapacityGb
    val storageInputState = androidx.compose.runtime.remember(visibleStorageCapacityGb) {
        androidx.compose.runtime.mutableStateOf(visibleStorageCapacityGb.toString())
    }
    val storageInput = storageInputState.value

    SectionCard {
        Text(
            text = "设备与权限",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(10.dp))
        CapabilityDetail(capability = capability)
        Spacer(modifier = Modifier.height(12.dp))
        PermissionRow("相机权限", cameraPermissionGranted, "授权相机", onRequestCameraPermission)
        Spacer(modifier = Modifier.height(8.dp))
        PermissionRow("通知权限", notificationPermissionGranted, "授权通知", onRequestNotificationPermission)
        Spacer(modifier = Modifier.height(8.dp))
        PermissionRow("麦克风权限", audioPermissionGranted, "按需授权", {}, enabled = false)
        Spacer(modifier = Modifier.height(8.dp))
        PermissionRow("定位权限", locationPermissionGranted, "授权定位", onRequestLocationPermission)
        Spacer(modifier = Modifier.height(8.dp))
        PermissionRow("蓝牙权限", bluetoothPermissionGranted, "授权蓝牙", onRequestBluetoothPermission)
    }

    SectionCard {
        Text(
            text = "录制设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(14.dp))

        SettingSwitchRow(
            title = "同时开启前后摄像头",
            subtitle = when {
                isRecording -> "录制中不可切换，停止后修改"
                capability.isAvailable -> capability.reason
                capability.state == DualCameraSwitchState.Checking -> "检测中，完成后自动刷新"
                else -> capability.reason
            },
            checked = settings.dualCameraEnabled && capability.isAvailable,
            enabled = capability.isAvailable && !isRecording,
            onCheckedChange = onDualCameraChanged,
        )

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onRedetect,
            enabled = !isRecording,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("重新检测双摄能力")
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "分段时长",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SegmentDurationRow(settings.segmentDurationMinutes, !isRecording, onSegmentDurationChanged)

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "碰撞检测灵敏度",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        CollisionSensitivityRow(settings.collisionSensitivity, !isRecording, onCollisionSensitivityChanged)

        Spacer(modifier = Modifier.height(18.dp))
        PresetStorageRow(
            currentGb = visibleStorageCapacityGb,
            maxGb = storageLimit.maxGb,
            onStorageChanged = {
                storageInputState.value = it.toString()
                onStorageChanged(it)
            },
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = storageInput,
            onValueChange = { value: String ->
                storageInputState.value = value.filter { it.isDigit() }.take(3)
                val next = storageInputState.value.toIntOrNull()
                if (next != null && next in VoyageCamSettingsStore.MIN_STORAGE_GB..storageLimit.maxGb) {
                    onStorageChanged(next)
                }
            },
            label = { Text("自定义视频存储容量") },
            suffix = { Text("GB") },
            supportingText = {
                Text("范围 ${VoyageCamSettingsStore.MIN_STORAGE_GB}-${storageLimit.maxGb}GB，约为当前可用空间的 80% 上限")
            },
            isError = storageInput.toIntOrNull()?.let {
                it !in VoyageCamSettingsStore.MIN_STORAGE_GB..storageLimit.maxGb
            } == true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        StorageOverviewPanel(storageOverview = storageOverview)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCleanupStorage,
            enabled = !isRecording,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("立即按容量清理普通片段")
        }

        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            title = "行车环境声录制",
            subtitle = "默认关闭；开启后请求麦克风权限，关闭时不占用麦克风、不写入音频轨道",
            checked = settings.ambientAudioEnabled,
            enabled = !isRecording,
            onCheckedChange = onAmbientAudioChanged,
        )

        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            title = "GPS位置与轨迹记录",
            subtitle = when {
                locationPermissionGranted -> "默认关闭；开启后紧急事件会保存位置和最近轨迹，关闭后不记录位置元数据"
                settings.gpsMetadataEnabled -> "已允许记录；授权定位后才会保存位置和最近轨迹"
                else -> "默认关闭；开启前需要授权定位，关闭后核心录制仍可使用"
            },
            checked = settings.gpsMetadataEnabled,
            enabled = true,
            onCheckedChange = onGpsMetadataChanged,
        )

        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            title = "导出时间/速度水印字幕",
            subtitle = "默认关闭；开启后仅在证据包导出时生成SRT侧车字幕，原始视频不转码、不改写",
            checked = settings.exportWatermarkSubtitlesEnabled,
            enabled = true,
            onCheckedChange = onExportWatermarkSubtitlesChanged,
        )

        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            title = "连接充电器自动开始录制",
            subtitle = "插入电源时自动启动前台录制；需要已授权相机和通知权限",
            checked = settings.autoStartOnPowerConnected,
            enabled = !isRecording,
            onCheckedChange = onAutoStartOnPowerChanged,
        )

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = bluetoothDevicePickerState.trustedDeviceInput,
            onValueChange = { value ->
                onTrustedBluetoothDeviceChanged(
                    bluetoothDevicePickerState.updateTrustedDeviceInput(value),
                )
            },
            label = { Text("可信蓝牙设备") },
            supportingText = { Text("填写车机蓝牙名称或 MAC 地址，完全匹配后自动启动") },
            enabled = !isRecording,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        PairedBluetoothDevicePanel(
            devices = bluetoothDevicePickerState.pairedDevices,
            bluetoothPermissionGranted = bluetoothPermissionGranted,
            onRefresh = bluetoothDevicePickerState::refreshPairedDevices,
            onSelected = { device ->
                onTrustedBluetoothDeviceChanged(bluetoothDevicePickerState.selectDevice(device))
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            title = "可信蓝牙连接自动开始录制",
            subtitle = "连接指定蓝牙设备时自动启动；需要相机、通知和蓝牙权限",
            checked = settings.autoStartOnTrustedBluetooth,
            enabled = !isRecording && bluetoothDevicePickerState.trustedDeviceInput.isNotBlank(),
            onCheckedChange = onAutoStartOnTrustedBluetoothChanged,
        )

        Spacer(modifier = Modifier.height(16.dp))
        AutoStartDiagnosticPanel(autoStartDiagnostic, onRefreshAutoStartDiagnostic)

        Spacer(modifier = Modifier.height(16.dp))
        DualCameraDiagnosticPanel(dualCameraDiagnostic, onRefreshDualCameraDiagnostic)

        Spacer(modifier = Modifier.height(16.dp))
        DualCameraSessionTelemetryPanel(dualCameraSessionTelemetry, onRefreshDualCameraSessionTelemetry)

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRequestResetSettings,
            enabled = !isRecording,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("恢复默认设置")
        }
    }
}

@Composable
private fun StorageOverviewPanel(storageOverview: RecordingStorageOverview) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "录像空间概览",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        LinearProgressIndicator(
            progress = storageOverview.normalUsagePercent / 100f,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "普通片段 ${storageOverview.normalBytes.asFileSize()} / ${storageOverview.maxStorageBytes.asFileSize()} · ${storageOverview.normalUsagePercent}%",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4D6267),
        )
        Text(
            text = "锁定片段 ${storageOverview.lockedBytes.asFileSize()} · 普通 ${storageOverview.normalClipCount} 个，锁定 ${storageOverview.lockedClipCount} 个",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
        Text(
            text = "按当前配置预计还可录制 ${storageOverview.estimatedRemainingMinutes.asDurationText()}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    }
}

@Composable
private fun PairedBluetoothDevicePanel(
    devices: List<TrustedBluetoothDevice>,
    bluetoothPermissionGranted: Boolean,
    onRefresh: () -> Unit,
    onSelected: (TrustedBluetoothDevice) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "已配对蓝牙设备",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        OutlinedButton(onClick = onRefresh) {
            Text("刷新")
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (!bluetoothPermissionGranted) {
        Text(
            text = "授权蓝牙权限后可选择已配对设备。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    } else if (devices.isEmpty()) {
        Text(
            text = "暂无可读取的已配对设备，可手动填写车机蓝牙名称或 MAC 地址。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    } else {
        devices.take(MAX_PAIRED_BLUETOOTH_DEVICES).forEach { device ->
            OutlinedButton(
                onClick = { onSelected(device) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(device.displayLabel())
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AutoStartDiagnosticPanel(
    diagnostic: AutoStartDiagnostic?,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "自动启动诊断",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        OutlinedButton(onClick = onRefresh) {
            Text("刷新")
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (diagnostic == null) {
        Text(
            text = "暂无自动启动触发记录。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    } else {
        Text(
            text = "${diagnostic.source.label} · ${diagnostic.result.label} · ${diagnostic.recordedAtMillis.asTime()}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (diagnostic.result == AutoStartResult.Started) Color(0xFF1F6F78) else Color(0xFF9B2C2C),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = diagnostic.reason,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4D6267),
        )
        if (diagnostic.detail.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = diagnostic.detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        }
    }
}

@Composable
private fun DualCameraDiagnosticPanel(
    diagnostic: PersistedDualCameraDiagnostic?,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "双摄诊断",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        OutlinedButton(onClick = onRefresh) {
            Text("刷新")
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (diagnostic == null) {
        Text(
            text = "暂无双摄降级记录。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    } else {
        Text(
            text = "${diagnostic.stage.label} · ${diagnostic.recordedAtMillis.asTime()}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF9B2C2C),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = diagnostic.detail,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4D6267),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "最近一次双摄录制已回落为后摄单录，可结合通知栏状态一起排查。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    }
}

@Composable
private fun DualCameraSessionTelemetryPanel(
    telemetry: PersistedDualCameraSessionTelemetry?,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "双摄会话状态",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        OutlinedButton(onClick = onRefresh) {
            Text("刷新")
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (telemetry == null) {
        Text(
            text = "暂无双摄会话记录。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    } else {
        Text(
            text = "${telemetry.summary} · ${telemetry.recordedAtMillis.asTime()}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (telemetry.diagnostic == null) Color(0xFF1F6F78) else Color(0xFF9B2C2C),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = telemetry.detail,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4D6267),
        )
        telemetry.diagnostic?.let { diagnostic ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = diagnostic,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9B2C2C),
            )
        }
    }
}

@Composable
private fun CapabilityDetail(capability: DualCameraCapability) {
    Text(
        text = "能力等级 ${capability.grade.name} · ${capability.state.asLabel()}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF163036),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = capability.reason,
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF64777B),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "后摄 ${capability.rearCameraId ?: "-"}：${capability.rearSummary}",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF4D6267),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "前摄 ${capability.frontCameraId ?: "-"}：${capability.frontSummary}",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF4D6267),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "${capability.systemSummary} · 最近检测 ${capability.checkedAtMillis.asTime()}",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF64777B),
    )
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    actionLabel: String,
    onRequestPermission: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "$label：${if (granted) "已授权" else "未授权"}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4D6267),
        )
        if (!granted) {
            OutlinedButton(
                onClick = onRequestPermission,
                enabled = enabled,
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun SegmentDurationRow(
    currentMinutes: Int,
    enabled: Boolean,
    onSegmentDurationChanged: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VoyageCamSettingsStore.ALLOWED_SEGMENT_DURATIONS_MINUTES.forEach { option ->
            if (currentMinutes == option) {
                Button(
                    onClick = { onSegmentDurationChanged(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("${option}分钟")
                }
            } else {
                OutlinedButton(
                    onClick = { onSegmentDurationChanged(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("${option}分钟")
                }
            }
        }
    }
}

@Composable
private fun CollisionSensitivityRow(
    current: CollisionSensitivity,
    enabled: Boolean,
    onCollisionSensitivityChanged: (CollisionSensitivity) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CollisionSensitivity.entries.forEach { option ->
            val label = "${option.label} ${option.thresholdG}g"
            if (current == option) {
                Button(
                    onClick = { onCollisionSensitivityChanged(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(label)
                }
            } else {
                OutlinedButton(
                    onClick = { onCollisionSensitivityChanged(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun PresetStorageRow(
    currentGb: Int,
    maxGb: Int,
    onStorageChanged: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(5, 10, 20).forEach { option ->
            val enabled = option <= maxGb
            if (currentGb == option) {
                Button(
                    onClick = { onStorageChanged(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("${option}GB")
                }
            } else {
                OutlinedButton(
                    onClick = { onStorageChanged(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("${option}GB")
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF163036),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun Long.asTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))
}

private fun Long.asFileSize(): String {
    val kb = this / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.getDefault(), "%.1fGB", gb)
        mb >= 1.0 -> String.format(Locale.getDefault(), "%.1fMB", mb)
        kb >= 1.0 -> String.format(Locale.getDefault(), "%.0fKB", kb)
        else -> "${this}B"
    }
}

private fun Long.asDurationText(): String {
    if (this <= 0L) return "不足 1 分钟"
    val hours = this / 60
    val minutes = this % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}小时${minutes}分钟"
        hours > 0 -> "${hours}小时"
        else -> "${minutes}分钟"
    }
}

private const val MAX_PAIRED_BLUETOOTH_DEVICES = 6
