package com.voyagecam.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.voyagecam.app.core.camera.CameraCapabilityDetector
import com.voyagecam.app.core.common.toContentUri
import com.voyagecam.app.core.model.AutoStartDiagnostic
import com.voyagecam.app.core.model.AutoStartResult
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.core.model.CollisionSensitivity
import com.voyagecam.app.core.model.DeviceCapabilityGrade
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.DualCameraSwitchState
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.EmergencyTrigger
import com.voyagecam.app.core.model.RecordingSegment
import com.voyagecam.app.data.autostart.AutoStartDiagnosticsStore
import com.voyagecam.app.data.emergency.EmergencyEventStore
import com.voyagecam.app.data.location.hasAnyLocationPermission
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.data.settings.VoyageCamSettingsStore
import com.voyagecam.app.data.settings.VoyageCamSettingsStore.Companion.coerceToAllowedSegmentDuration
import com.voyagecam.app.data.storage.RecordingStorageManager
import com.voyagecam.app.feature.evidence.EvidencePackageFile
import com.voyagecam.app.feature.evidence.EmergencyEvidenceExporter
import com.voyagecam.app.feature.recording.RecordingForegroundService
import com.voyagecam.app.ui.playback.PlaybackItem
import com.voyagecam.app.ui.playback.PlaybackPanel
import com.voyagecam.app.ui.events.EvidenceExportState
import com.voyagecam.app.ui.events.EmergencyEventPanel
import com.voyagecam.app.ui.history.SegmentCameraFilter
import com.voyagecam.app.ui.history.SegmentLockFilter
import com.voyagecam.app.ui.history.filterSegments
import com.voyagecam.app.ui.preview.RearCameraPreview
import com.voyagecam.app.ui.theme.SectionCard
import com.voyagecam.app.ui.theme.VoyageCamTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoyageCamApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoyageCamApp() {
    val context = LocalContext.current
    val settingsStore = remember { VoyageCamSettingsStore(context) }
    val detector = remember { CameraCapabilityDetector(context) }
    val storageLimit = remember { StorageCapacityLimit.from(context) }
    val storageManager = remember { RecordingStorageManager(context) }
    val emergencyEventStore = remember { EmergencyEventStore(context) }
    val autoStartDiagnosticsStore = remember { AutoStartDiagnosticsStore(context) }
    val evidenceExporter = remember { EmergencyEvidenceExporter(context, storageManager) }

    var settings by remember { mutableStateOf(settingsStore.load().coerceTo(storageLimit)) }
    var capability by remember {
        mutableStateOf(
            settingsStore.loadCapability() ?: DualCameraCapability(
                state = DualCameraSwitchState.Checking,
                reason = "正在检测前后摄像头并发能力",
            ),
        )
    }
    var isRecording by rememberSaveable { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf("已支持后摄单录到本地 MP4；双摄录制仍在下一阶段接入。") }
    var allSegments by remember { mutableStateOf(storageManager.listRecentSegments()) }
    var emergencyEvents by remember { mutableStateOf(emergencyEventStore.listRecentEvents()) }
    var autoStartDiagnostic by remember { mutableStateOf(autoStartDiagnosticsStore.load()) }
    var pairedBluetoothDevices by remember { mutableStateOf(context.pairedBluetoothDevices()) }
    var playbackItem by remember { mutableStateOf<PlaybackItem?>(null) }
    var evidenceExportState by remember { mutableStateOf<EvidenceExportState?>(null) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCameraFilter by rememberSaveable { mutableStateOf(SegmentCameraFilter.All) }
    var selectedLockFilter by rememberSaveable { mutableStateOf(SegmentLockFilter.All) }
    var pendingStart by rememberSaveable { mutableStateOf(false) }
    var permissionRefreshKey by remember { mutableIntStateOf(0) }
    val availableDays = allSegments.map { it.day }.distinct()
    val filteredSegments = allSegments.filterSegments(
        selectedDay = selectedDay,
        cameraFilter = selectedCameraFilter,
        lockFilter = selectedLockFilter,
    )

    fun persist(next: VoyageCamSettings) {
        settings = next.coerceTo(storageLimit)
        settingsStore.save(settings)
    }

    fun persistCapability(next: DualCameraCapability) {
        capability = next
        settingsStore.saveCapability(next)
    }

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

    fun beginRecordingService() {
        isRecording = true
        statusMessage = "正在从预览切换到录制..."
        Handler(Looper.getMainLooper()).postDelayed(
            {
                RecordingForegroundService.start(
                    context = context,
                    dualCamera = settings.dualCameraEnabled && capability.isAvailable,
                    ambientAudio = settings.ambientAudioEnabled,
                )
                statusMessage = "后摄录制服务已启动；每 ${settings.segmentDurationMinutes} 分钟分段，按 ${settings.storageCapacityGb}GB 循环空间清理普通片段。"
            },
            PREVIEW_RELEASE_DELAY_MILLIS,
        )
    }

    fun redetect() {
        persistCapability(
            DualCameraCapability(
                state = DualCameraSwitchState.Checking,
                reason = "正在检测前后摄像头并发能力",
            ),
        )
        val result = detector.detect(settings.dualCameraEnabled)
        persistCapability(result)
        if (!result.isAvailable && settings.dualCameraEnabled) {
            persist(settings.copy(dualCameraEnabled = false))
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRefreshKey++
        if (granted) {
            redetect()
            if (pendingStart && hasNotificationPermission()) {
                pendingStart = false
                beginRecordingService()
            }
        } else {
            pendingStart = false
            statusMessage = "相机权限未授权，无法开始行车记录。"
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRefreshKey++
        if (granted && pendingStart && hasPermission(Manifest.permission.CAMERA)) {
            pendingStart = false
            beginRecordingService()
        } else if (!granted) {
            pendingStart = false
            statusMessage = "通知权限未授权，后台录制状态无法可靠展示。"
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRefreshKey++
        persist(settings.copy(ambientAudioEnabled = granted))
        statusMessage = if (granted) {
            "行车环境声已开启；下一次录制会带音频。"
        } else {
            "麦克风权限未授权，已保持静音录制。"
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        permissionRefreshKey++
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        statusMessage = if (granted) {
            "定位权限已授权；紧急事件会记录最近可用坐标。"
        } else {
            "定位权限未授权；紧急事件仍会记录时间、触发类型和片段。"
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRefreshKey++
        statusMessage = if (granted) {
            pairedBluetoothDevices = context.pairedBluetoothDevices()
            "蓝牙权限已授权；可信蓝牙连接后可自动开始录制。"
        } else {
            "蓝牙权限未授权，可信蓝牙自动启动不可用。"
        }
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

        beginRecordingService()
    }

    fun stopRecording() {
        RecordingForegroundService.stop(context)
        isRecording = false
        statusMessage = "录制服务已停止。"
        allSegments = storageManager.listRecentSegments()
        emergencyEvents = emergencyEventStore.listRecentEvents()
    }

    fun openSegment(segment: RecordingSegment) {
        playbackItem = PlaybackItem(
            title = segment.name,
            subtitle = "${segment.cameraDirection.label} · ${if (segment.locked) "已锁定" else "普通"}",
            file = File(segment.absolutePath),
        )
    }

    fun shareSegment(segment: RecordingSegment) {
        runCatching {
            val uri = segment.toContentUri(context)
            val intent = Intent(Intent.ACTION_SEND)
                .setType(VIDEO_MIME_TYPE)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "分享录像片段"))
        }.onFailure { error ->
            statusMessage = "无法分享片段：${error.message ?: segment.name}"
        }
    }

    fun openEmergencyEvent(event: EmergencyEvent) {
        runCatching {
            val file = event.existingSegmentFiles(storageManager).firstOrNull()
                ?: error("关联片段文件不存在")
            playbackItem = PlaybackItem(
                title = file.name,
                subtitle = "${event.trigger.label} · ${event.triggeredAtMillis.asTime()}",
                file = file,
            )
        }.onFailure { error ->
            statusMessage = "无法打开紧急事件片段：${error.message ?: event.trigger.label}"
        }
    }

    fun shareEmergencyEvent(event: EmergencyEvent) {
        runCatching {
            val files = event.existingSegmentFiles(storageManager)
            if (files.isEmpty()) error("关联片段文件不存在")

            val uris = ArrayList<Uri>(files.map { it.toContentUri(context) })
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND)
                    .setType(VIDEO_MIME_TYPE)
                    .putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType(VIDEO_MIME_TYPE)
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            context.startActivity(Intent.createChooser(intent, "分享紧急事件片段"))
        }.onFailure { error ->
            statusMessage = "无法分享紧急事件：${error.message ?: event.trigger.label}"
        }
    }

    fun exportEmergencyEvent(event: EmergencyEvent) {
        if (evidenceExportState is EvidenceExportState.Running) {
            statusMessage = "证据包正在导出，请稍候。"
            return
        }

        evidenceExportState = EvidenceExportState.Running(
            eventId = event.id,
            title = "${event.trigger.label} · ${event.triggeredAtMillis.asTime()}",
        )
        statusMessage = "正在导出紧急事件证据包..."

        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            val result = runCatching {
                evidenceExporter.export(event)
            }
            mainHandler.post {
                result
                    .onSuccess { packageFile ->
                        evidenceExportState = EvidenceExportState.Ready(
                            eventId = event.id,
                            file = packageFile.file,
                            clipCount = packageFile.clipCount,
                        )
                        statusMessage = "证据包已导出：${packageFile.file.name}"
                    }
                    .onFailure { error ->
                        evidenceExportState = EvidenceExportState.Failed(
                            eventId = event.id,
                            message = error.message ?: "导出失败",
                        )
                        statusMessage = "证据包导出失败：${error.message ?: event.trigger.label}"
                    }
            }
        }.start()
    }

    fun shareEvidencePackage(file: File) {
        runCatching {
            val uri = file.toContentUri(context)
            val intent = Intent(Intent.ACTION_SEND)
                .setType(ZIP_MIME_TYPE)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "分享证据包"))
        }.onFailure { error ->
            statusMessage = "无法分享证据包：${error.message ?: file.name}"
        }
    }

    fun openEmergencyEventMap(event: EmergencyEvent) {
        runCatching {
            val uri = event.toGeoUri() ?: error("该事件没有可用坐标")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        }.onFailure { error ->
            statusMessage = if (error is ActivityNotFoundException) {
                "未找到可打开坐标的地图应用。"
            } else {
                "无法打开事件位置：${error.message ?: event.trigger.label}"
            }
        }
    }

    LaunchedEffect(Unit) {
        if (settings != settingsStore.load()) {
            settingsStore.save(settings)
        }
        redetect()
    }

    VoyageCamTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("VoyageCam") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFFF7FAF9),
                        titleContentColor = Color(0xFF163036),
                    ),
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF7FAF9))
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RecordingPanel(
                    settings = settings,
                    capability = capability,
                    isRecording = isRecording,
                    statusMessage = statusMessage,
                    onToggleRecording = {
                        if (isRecording) stopRecording() else requestStartRecording()
                    },
                    onEmergencyLock = {
                        RecordingForegroundService.lockCurrent(context)
                        statusMessage = "紧急锁定已发送：当前片段、上一片段和下一片段会被保护。"
                        Handler(Looper.getMainLooper()).postDelayed(
                            { emergencyEvents = emergencyEventStore.listRecentEvents() },
                            EVENT_REFRESH_DELAY_MILLIS,
                        )
                    },
                )

                SettingsPanel(
                    settings = settings,
                    capability = capability,
                    isRecording = isRecording,
                    storageLimit = storageLimit,
                    cameraPermissionGranted = hasPermission(Manifest.permission.CAMERA),
                    notificationPermissionGranted = hasNotificationPermission(),
                    audioPermissionGranted = hasPermission(Manifest.permission.RECORD_AUDIO),
                    locationPermissionGranted = hasLocationPermission(),
                    bluetoothPermissionGranted = hasBluetoothConnectPermission(),
                    onRequestCameraPermission = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRequestLocationPermission = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                    onRequestBluetoothPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    },
                    onRedetect = { redetect() },
                    onDualCameraChanged = { enabled ->
                        if (!isRecording) {
                            persist(settings.copy(dualCameraEnabled = enabled))
                            persistCapability(detector.detect(enabled))
                        } else {
                            statusMessage = "录制中不可切换双摄；停止后修改会在下一次录制生效。"
                        }
                    },
                    onStorageChanged = { capacityGb ->
                        persist(settings.copy(storageCapacityGb = capacityGb))
                    },
                    onSegmentDurationChanged = { minutes ->
                        if (isRecording) {
                            statusMessage = "录制中不可修改分段时长；停止后修改会在下一次录制生效。"
                        } else {
                            persist(settings.copy(segmentDurationMinutes = minutes))
                        }
                    },
                    onCollisionSensitivityChanged = { sensitivity ->
                        if (isRecording) {
                            statusMessage = "录制中不可修改碰撞检测灵敏度；停止后修改会在下一次录制生效。"
                        } else {
                            persist(settings.copy(collisionSensitivity = sensitivity))
                        }
                    },
                    onAmbientAudioChanged = { enabled ->
                        if (isRecording) {
                            statusMessage = "录制中不可切换环境声；停止后修改会在下一次录制生效。"
                            return@SettingsPanel
                        }

                        if (!enabled) {
                            persist(settings.copy(ambientAudioEnabled = false))
                            statusMessage = "已切换为静音录制；后续录制不写入音频轨道。"
                            return@SettingsPanel
                        }

                        val granted = hasPermission(Manifest.permission.RECORD_AUDIO)
                        if (granted) {
                            persist(settings.copy(ambientAudioEnabled = true))
                            statusMessage = "行车环境声已开启；音频仅写入本地行车视频。"
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onAutoStartOnPowerChanged = { enabled ->
                        persist(settings.copy(autoStartOnPowerConnected = enabled))
                        statusMessage = if (enabled) {
                            "已开启连接充电器自动开始录制；需提前授权相机和通知权限。"
                        } else {
                            "已关闭连接充电器自动开始录制。"
                        }
                    },
                    onTrustedBluetoothDeviceChanged = { device ->
                        persist(settings.copy(trustedBluetoothDevice = device))
                    },
                    onAutoStartOnTrustedBluetoothChanged = { enabled ->
                        if (enabled && settings.trustedBluetoothDevice.isBlank()) {
                            statusMessage = "请先填写可信蓝牙设备名称或 MAC 地址。"
                            return@SettingsPanel
                        }

                        persist(settings.copy(autoStartOnTrustedBluetooth = enabled))
                        statusMessage = if (enabled) {
                            "已开启可信蓝牙连接自动开始录制；需提前授权相机、通知和蓝牙权限。"
                        } else {
                            "已关闭可信蓝牙连接自动开始录制。"
                        }
                    },
                    autoStartDiagnostic = autoStartDiagnostic,
                    onRefreshAutoStartDiagnostic = {
                        autoStartDiagnostic = autoStartDiagnosticsStore.load()
                    },
                    pairedBluetoothDevices = pairedBluetoothDevices,
                    onRefreshPairedBluetoothDevices = {
                        pairedBluetoothDevices = context.pairedBluetoothDevices()
                    },
                )

                playbackItem?.let { item ->
                    PlaybackPanel(
                        item = item,
                        onClose = { playbackItem = null },
                        onOpenInSystem = {
                            openFileInSystem(
                                context = context,
                                file = item.file,
                                onError = { message -> statusMessage = message },
                            )
                        },
                    )
                }

                EmergencyEventPanel(
                    events = emergencyEvents,
                    exportState = evidenceExportState,
                    onRefresh = {
                        emergencyEvents = emergencyEventStore.listRecentEvents()
                    },
                    onOpen = { event ->
                        openEmergencyEvent(event)
                    },
                    onShare = { event ->
                        shareEmergencyEvent(event)
                    },
                    onExport = { event ->
                        exportEmergencyEvent(event)
                    },
                    onShareExport = { file ->
                        shareEvidencePackage(file)
                    },
                    onDismissExport = {
                        evidenceExportState = null
                    },
                    onOpenMap = { event ->
                        openEmergencyEventMap(event)
                    },
                )

                SegmentHistoryPanel(
                    segments = filteredSegments,
                    totalSegmentCount = allSegments.size,
                    availableDays = availableDays,
                    selectedDay = selectedDay,
                    selectedCameraFilter = selectedCameraFilter,
                    selectedLockFilter = selectedLockFilter,
                    onSelectedDayChanged = { selectedDay = it },
                    onCameraFilterChanged = { selectedCameraFilter = it },
                    onLockFilterChanged = { selectedLockFilter = it },
                    onRefresh = {
                        allSegments = storageManager.listRecentSegments()
                        emergencyEvents = emergencyEventStore.listRecentEvents()
                    },
                    onOpen = { segment ->
                        openSegment(segment)
                    },
                    onShare = { segment ->
                        shareSegment(segment)
                    },
                )
            }
        }
    }
}

@Composable
private fun RecordingPanel(
    settings: VoyageCamSettings,
    capability: DualCameraCapability,
    isRecording: Boolean,
    statusMessage: String,
    onToggleRecording: () -> Unit,
    onEmergencyLock: () -> Unit,
) {
    SectionCard {
        RearCameraPreview(enabled = !isRecording)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (isRecording) "正在录制" else "准备录制",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = buildString {
                append(if (settings.dualCameraEnabled && capability.isAvailable) "前后双摄" else "后摄单录")
                append(" · ")
                append("${settings.segmentDurationMinutes}分钟分段")
                append(" · ")
                append("${settings.storageCapacityGb}GB 循环空间")
                append(" · ")
                append("碰撞${settings.collisionSensitivity.label}")
                append(" · ")
                append(if (settings.ambientAudioEnabled) "环境声开启" else "静音录制")
            },
            color = Color(0xFF4D6267),
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onToggleRecording,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isRecording) "停止录制" else "开始录制")
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onEmergencyLock,
            enabled = isRecording,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("紧急锁定")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
    }
}

@Composable
private fun SegmentHistoryPanel(
    segments: List<RecordingSegment>,
    totalSegmentCount: Int,
    availableDays: List<String>,
    selectedDay: String?,
    selectedCameraFilter: SegmentCameraFilter,
    selectedLockFilter: SegmentLockFilter,
    onSelectedDayChanged: (String?) -> Unit,
    onCameraFilterChanged: (SegmentCameraFilter) -> Unit,
    onLockFilterChanged: (SegmentLockFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (RecordingSegment) -> Unit,
    onShare: (RecordingSegment) -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "录像片段",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF163036),
            )
            OutlinedButton(onClick = onRefresh) {
                Text("刷新")
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        SegmentFilterPanel(
            availableDays = availableDays,
            selectedDay = selectedDay,
            selectedCameraFilter = selectedCameraFilter,
            selectedLockFilter = selectedLockFilter,
            onSelectedDayChanged = onSelectedDayChanged,
            onCameraFilterChanged = onCameraFilterChanged,
            onLockFilterChanged = onLockFilterChanged,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (totalSegmentCount == 0) {
            Text(
                text = "暂无片段。开始录制后，这里会显示最近生成的普通和锁定视频。",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        } else if (segments.isEmpty()) {
            Text(
                text = "当前筛选条件下没有片段。",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        } else {
            Text(
                text = "显示 ${segments.size}/${totalSegmentCount} 个片段 · 锁定片段不会被循环清理",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
            Spacer(modifier = Modifier.height(12.dp))
            segments.forEachIndexed { index, segment ->
                RecordingSegmentRow(
                    segment = segment,
                    onOpen = onOpen,
                    onShare = onShare,
                )
                if (index != segments.lastIndex) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE1E8EA)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentFilterPanel(
    availableDays: List<String>,
    selectedDay: String?,
    selectedCameraFilter: SegmentCameraFilter,
    selectedLockFilter: SegmentLockFilter,
    onSelectedDayChanged: (String?) -> Unit,
    onCameraFilterChanged: (SegmentCameraFilter) -> Unit,
    onLockFilterChanged: (SegmentLockFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "筛选",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        DayFilterRow(
            availableDays = availableDays,
            selectedDay = selectedDay,
            onSelectedDayChanged = onSelectedDayChanged,
        )
        SegmentCameraFilterRow(
            selected = selectedCameraFilter,
            onSelected = onCameraFilterChanged,
        )
        SegmentLockFilterRow(
            selected = selectedLockFilter,
            onSelected = onLockFilterChanged,
        )
    }
}

@Composable
private fun DayFilterRow(
    availableDays: List<String>,
    selectedDay: String?,
    onSelectedDayChanged: (String?) -> Unit,
) {
    val selectedIndex = selectedDay?.let { availableDays.indexOf(it) } ?: -1
    val canMovePrevious = availableDays.isNotEmpty() && (selectedIndex == -1 || selectedIndex < availableDays.lastIndex)
    val canMoveNext = availableDays.isNotEmpty() && selectedIndex > 0

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "日期：${selectedDay ?: "全部日期"}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val nextIndex = if (selectedIndex == -1) 0 else (selectedIndex + 1).coerceAtMost(availableDays.lastIndex)
                    onSelectedDayChanged(availableDays.getOrNull(nextIndex))
                },
                enabled = canMovePrevious,
                modifier = Modifier.weight(1f),
            ) {
                Text("上一天")
            }
            Button(
                onClick = { onSelectedDayChanged(null) },
                enabled = selectedDay != null,
                modifier = Modifier.weight(1f),
            ) {
                Text("全部")
            }
            OutlinedButton(
                onClick = {
                    val nextIndex = (selectedIndex - 1).coerceAtLeast(0)
                    onSelectedDayChanged(availableDays.getOrNull(nextIndex))
                },
                enabled = canMoveNext,
                modifier = Modifier.weight(1f),
            ) {
                Text("下一天")
            }
        }
    }
}

@Composable
private fun SegmentCameraFilterRow(
    selected: SegmentCameraFilter,
    onSelected: (SegmentCameraFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SegmentCameraFilter.entries.forEach { option ->
            FilterButton(
                label = option.label,
                selected = selected == option,
                onClick = { onSelected(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SegmentLockFilterRow(
    selected: SegmentLockFilter,
    onSelected: (SegmentLockFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SegmentLockFilter.entries.forEach { option ->
            FilterButton(
                label = option.label,
                selected = selected == option,
                onClick = { onSelected(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FilterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(label)
        }
    }
}

@Composable
private fun RecordingSegmentRow(
    segment: RecordingSegment,
    onOpen: (RecordingSegment) -> Unit,
    onShare: (RecordingSegment) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${segment.cameraDirection.label} · ${if (segment.locked) "已锁定" else "普通"}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (segment.locked) Color(0xFF9B2C2C) else Color(0xFF163036),
            )
            Text(
                text = segment.sizeBytes.asFileSize(),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64777B),
            )
        }
        Text(
            text = segment.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4D6267),
        )
        Text(
            text = "${segment.lastModifiedMillis.asTime()} · ${segment.relativePath}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onOpen(segment) },
                modifier = Modifier.weight(1f),
            ) {
                Text("播放")
            }
            OutlinedButton(
                onClick = { onShare(segment) },
                modifier = Modifier.weight(1f),
            ) {
                Text("分享")
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    settings: VoyageCamSettings,
    capability: DualCameraCapability,
    isRecording: Boolean,
    storageLimit: StorageCapacityLimit,
    cameraPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    audioPermissionGranted: Boolean,
    locationPermissionGranted: Boolean,
    bluetoothPermissionGranted: Boolean,
    onRequestCameraPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onRedetect: () -> Unit,
    onDualCameraChanged: (Boolean) -> Unit,
    onStorageChanged: (Int) -> Unit,
    onSegmentDurationChanged: (Int) -> Unit,
    onCollisionSensitivityChanged: (CollisionSensitivity) -> Unit,
    onAmbientAudioChanged: (Boolean) -> Unit,
    onAutoStartOnPowerChanged: (Boolean) -> Unit,
    onTrustedBluetoothDeviceChanged: (String) -> Unit,
    onAutoStartOnTrustedBluetoothChanged: (Boolean) -> Unit,
    autoStartDiagnostic: AutoStartDiagnostic?,
    onRefreshAutoStartDiagnostic: () -> Unit,
    pairedBluetoothDevices: List<TrustedBluetoothDevice>,
    onRefreshPairedBluetoothDevices: () -> Unit,
) {
    var storageInput by remember(settings.storageCapacityGb) {
        mutableStateOf(settings.storageCapacityGb.toString())
    }
    var trustedBluetoothInput by remember(settings.trustedBluetoothDevice) {
        mutableStateOf(settings.trustedBluetoothDevice)
    }

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
        PermissionRow(
            label = "相机权限",
            granted = cameraPermissionGranted,
            actionLabel = "授权相机",
            onRequestPermission = onRequestCameraPermission,
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionRow(
            label = "通知权限",
            granted = notificationPermissionGranted,
            actionLabel = "授权通知",
            onRequestPermission = onRequestNotificationPermission,
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionRow(
            label = "麦克风权限",
            granted = audioPermissionGranted,
            actionLabel = "按需授权",
            onRequestPermission = {},
            enabled = false,
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionRow(
            label = "定位权限",
            granted = locationPermissionGranted,
            actionLabel = "授权定位",
            onRequestPermission = onRequestLocationPermission,
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionRow(
            label = "蓝牙权限",
            granted = bluetoothPermissionGranted,
            actionLabel = "授权蓝牙",
            onRequestPermission = onRequestBluetoothPermission,
        )
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
        SegmentDurationRow(
            currentMinutes = settings.segmentDurationMinutes,
            enabled = !isRecording,
            onSegmentDurationChanged = onSegmentDurationChanged,
        )

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "碰撞检测灵敏度",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        CollisionSensitivityRow(
            current = settings.collisionSensitivity,
            enabled = !isRecording,
            onCollisionSensitivityChanged = onCollisionSensitivityChanged,
        )

        Spacer(modifier = Modifier.height(18.dp))
        PresetStorageRow(
            currentGb = settings.storageCapacityGb,
            maxGb = storageLimit.maxGb,
            onStorageChanged = {
                storageInput = it.toString()
                onStorageChanged(it)
            },
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = storageInput,
            onValueChange = { value: String ->
                storageInput = value.filter { it.isDigit() }.take(3)
                val next = storageInput.toIntOrNull()
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
            title = "连接充电器自动开始录制",
            subtitle = "插入电源时自动启动前台录制；需要已授权相机和通知权限",
            checked = settings.autoStartOnPowerConnected,
            enabled = !isRecording,
            onCheckedChange = onAutoStartOnPowerChanged,
        )

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = trustedBluetoothInput,
            onValueChange = { value ->
                trustedBluetoothInput = value.take(MAX_TRUSTED_BLUETOOTH_LENGTH)
                onTrustedBluetoothDeviceChanged(trustedBluetoothInput)
            },
            label = { Text("可信蓝牙设备") },
            supportingText = { Text("填写车机蓝牙名称或 MAC 地址，完全匹配后自动启动") },
            enabled = !isRecording,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        PairedBluetoothDevicePanel(
            devices = pairedBluetoothDevices,
            bluetoothPermissionGranted = bluetoothPermissionGranted,
            onRefresh = onRefreshPairedBluetoothDevices,
            onSelected = { device ->
                val value = device.preferredMatchValue().take(MAX_TRUSTED_BLUETOOTH_LENGTH)
                trustedBluetoothInput = value
                onTrustedBluetoothDeviceChanged(value)
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            title = "可信蓝牙连接自动开始录制",
            subtitle = "连接指定蓝牙设备时自动启动；需要相机、通知和蓝牙权限",
            checked = settings.autoStartOnTrustedBluetooth,
            enabled = !isRecording && trustedBluetoothInput.isNotBlank(),
            onCheckedChange = onAutoStartOnTrustedBluetoothChanged,
        )

        Spacer(modifier = Modifier.height(16.dp))
        AutoStartDiagnosticPanel(
            diagnostic = autoStartDiagnostic,
            onRefresh = onRefreshAutoStartDiagnostic,
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

private data class StorageCapacityLimit(val maxGb: Int) {
    companion object {
        private const val BYTES_PER_GB = 1024L * 1024L * 1024L

        fun from(context: Context): StorageCapacityLimit {
            val statFs = StatFs(context.filesDir.absolutePath)
            val eightyPercentAvailableGb = (statFs.availableBytes * 0.8 / BYTES_PER_GB).toInt()
            val maxGb = max(
                VoyageCamSettingsStore.MIN_STORAGE_GB,
                min(VoyageCamSettingsStore.MAX_STORAGE_GB, eightyPercentAvailableGb),
            )

            return StorageCapacityLimit(maxGb = maxGb)
        }
    }
}

private fun VoyageCamSettings.coerceTo(limit: StorageCapacityLimit): VoyageCamSettings {
    return copy(
        storageCapacityGb = storageCapacityGb.coerceIn(
            VoyageCamSettingsStore.MIN_STORAGE_GB,
            limit.maxGb,
        ),
        segmentDurationMinutes = with(VoyageCamSettingsStore) {
            segmentDurationMinutes.coerceToAllowedSegmentDuration()
        },
    )
}

private fun DualCameraSwitchState.asLabel(): String {
    return when (this) {
        DualCameraSwitchState.Checking -> "检测中"
        DualCameraSwitchState.AvailableOff -> "可用未开启"
        DualCameraSwitchState.AvailableOn -> "可用已开启"
        DualCameraSwitchState.Unavailable -> "不可用"
        DualCameraSwitchState.CheckFailed -> "检测失败"
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

private fun EmergencyEvent.toGeoUri(): Uri? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    val label = Uri.encode("VoyageCam ${trigger.label} ${triggeredAtMillis.asTime()}")
    return Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
}

private fun Context.pairedBluetoothDevices(): List<TrustedBluetoothDevice> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }

    return runCatching {
        bluetoothAdapter()
            ?.bondedDevices
            .orEmpty()
            .map { device ->
                TrustedBluetoothDevice(
                    name = runCatching { device.name }.getOrNull().orEmpty(),
                    address = runCatching { device.address }.getOrNull().orEmpty(),
                )
            }
            .filter { it.name.isNotBlank() || it.address.isNotBlank() }
            .sortedWith(compareBy<TrustedBluetoothDevice> { it.name.ifBlank { it.address }.lowercase(Locale.getDefault()) })
    }.getOrDefault(emptyList())
}

private fun Context.bluetoothAdapter(): BluetoothAdapter? {
    return getSystemService(BluetoothManager::class.java)?.adapter
}

private data class TrustedBluetoothDevice(
    val name: String,
    val address: String,
) {
    fun preferredMatchValue(): String = name.ifBlank { address }

    fun displayLabel(): String {
        return when {
            name.isNotBlank() && address.isNotBlank() -> "$name · $address"
            name.isNotBlank() -> name
            else -> address
        }
    }
}

private fun openFileInSystem(
    context: Context,
    file: File,
    onError: (String) -> Unit,
) {
    runCatching {
        val uri = file.toContentUri(context)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, VIDEO_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }.onFailure { error ->
        val message = if (error is ActivityNotFoundException) {
            "未找到可播放 MP4 的应用。"
        } else {
            "无法打开片段：${error.message ?: file.name}"
        }
        onError(message)
    }
}

private fun EmergencyEvent.existingSegmentFiles(storageManager: RecordingStorageManager): List<File> {
    return segmentPaths
        .mapNotNull { storageManager.dashcamFile(it) }
        .filter { it.exists() && it.isFile }
}

private const val VIDEO_MIME_TYPE = "video/mp4"
private const val ZIP_MIME_TYPE = "application/zip"
private const val PREVIEW_RELEASE_DELAY_MILLIS = 350L
private const val EVENT_REFRESH_DELAY_MILLIS = 600L
private const val MAX_TRUSTED_BLUETOOTH_LENGTH = 80
private const val MAX_PAIRED_BLUETOOTH_DEVICES = 6
