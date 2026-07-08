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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.voyagecam.app.core.camera.CameraCapabilityDetector
import com.voyagecam.app.core.common.toContentUri
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.core.model.DeviceCapabilityGrade
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.DualCameraSwitchState
import com.voyagecam.app.core.model.EmergencyEvent
import com.voyagecam.app.core.model.EmergencyTrigger
import com.voyagecam.app.core.model.PendingStorageCapacityChange
import com.voyagecam.app.core.model.RecordingSegment
import com.voyagecam.app.core.model.TrustedBluetoothDevice
import com.voyagecam.app.core.model.toStorageBytes
import com.voyagecam.app.data.autostart.AutoStartDiagnosticsStore
import com.voyagecam.app.data.emergency.EmergencyEventStore
import com.voyagecam.app.data.location.hasAnyLocationPermission
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.data.settings.VoyageCamSettingsStore
import com.voyagecam.app.data.settings.VoyageCamSettingsStore.Companion.coerceToAllowedSegmentDuration
import com.voyagecam.app.data.settings.StorageCapacityLimit
import com.voyagecam.app.data.settings.coerceTo
import com.voyagecam.app.data.storage.RecordingStorageManager
import com.voyagecam.app.feature.evidence.EvidenceExportCancelledException
import com.voyagecam.app.feature.evidence.EvidencePackageFile
import com.voyagecam.app.feature.evidence.EmergencyEvidenceExporter
import com.voyagecam.app.feature.recording.RecordingForegroundService
import com.voyagecam.app.ui.playback.PlaybackItem
import com.voyagecam.app.ui.playback.PlaybackPanel
import com.voyagecam.app.ui.events.EvidenceExportState
import com.voyagecam.app.ui.events.EmergencyEventPanel
import com.voyagecam.app.ui.history.SegmentCameraFilter
import com.voyagecam.app.ui.history.SegmentHistoryPanel
import com.voyagecam.app.ui.history.SegmentLockFilter
import com.voyagecam.app.ui.history.filterSegments
import com.voyagecam.app.ui.preview.RearCameraPreview
import com.voyagecam.app.ui.settings.SettingsPanel
import com.voyagecam.app.ui.theme.SectionCard
import com.voyagecam.app.ui.theme.VoyageCamTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

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
    var storageOverview by remember {
        mutableStateOf(
            storageManager.storageOverview(
                settings = settings,
                dualCameraActive = settings.dualCameraEnabled && capability.isAvailable,
            ),
        )
    }
    var emergencyEvents by remember { mutableStateOf(emergencyEventStore.listRecentEvents()) }
    var autoStartDiagnostic by remember { mutableStateOf(autoStartDiagnosticsStore.load()) }
    var pairedBluetoothDevices by remember { mutableStateOf(context.pairedBluetoothDevices()) }
    var playbackItem by remember { mutableStateOf<PlaybackItem?>(null) }
    var evidenceExportState by remember { mutableStateOf<EvidenceExportState?>(null) }
    var evidenceExportCancelFlag by remember { mutableStateOf<AtomicBoolean?>(null) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCameraFilter by rememberSaveable { mutableStateOf(SegmentCameraFilter.All) }
    var selectedLockFilter by rememberSaveable { mutableStateOf(SegmentLockFilter.All) }
    var pendingStorageCapacityChange by remember { mutableStateOf<PendingStorageCapacityChange?>(null) }
    var pendingSegmentDelete by remember { mutableStateOf<RecordingSegment?>(null) }
    var pendingEmergencyEventDelete by remember { mutableStateOf<EmergencyEvent?>(null) }
    var pendingSettingsReset by remember { mutableStateOf(false) }
    var pendingStart by rememberSaveable { mutableStateOf(false) }
    var pendingGpsMetadataEnable by rememberSaveable { mutableStateOf(false) }
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
        storageOverview = storageManager.storageOverview(
            settings = settings,
            dualCameraActive = settings.dualCameraEnabled && capability.isAvailable,
        )
    }

    fun persistCapability(next: DualCameraCapability) {
        capability = next
        settingsStore.saveCapability(next)
        storageOverview = storageManager.storageOverview(
            settings = settings,
            dualCameraActive = settings.dualCameraEnabled && capability.isAvailable,
        )
    }

    fun refreshRecordingData() {
        allSegments = storageManager.listRecentSegments()
        storageOverview = storageManager.storageOverview(
            settings = settings,
            dualCameraActive = settings.dualCameraEnabled && capability.isAvailable,
        )
    }

    fun applyStorageCapacityChange(capacityGb: Int, cleanupNow: Boolean) {
        persist(settings.copy(storageCapacityGb = capacityGb))
        if (cleanupNow) {
            val cleanup = storageManager.cleanupNormalSegments(capacityGb)
            refreshRecordingData()
            statusMessage = if (cleanup.deletedFiles > 0) {
                "已将录像容量调整为 ${capacityGb}GB，并清理 ${cleanup.deletedFiles} 个普通片段、释放 ${cleanup.deletedBytes.asFileSize()}。"
            } else {
                "已将录像容量调整为 ${capacityGb}GB；当前普通片段无需清理。"
            }
        } else {
            statusMessage = "已将录像容量调整为 ${capacityGb}GB。"
        }
    }

    fun requestStorageCapacityChange(capacityGb: Int) {
        if (capacityGb == settings.storageCapacityGb) return
        val nextBytes = capacityGb.toStorageBytes()
        if (storageOverview.requiresCleanupConfirmation(capacityGb)) {
            pendingStorageCapacityChange = PendingStorageCapacityChange(
                nextCapacityGb = capacityGb,
                currentNormalBytes = storageOverview.normalBytes,
                overflowBytes = storageOverview.normalBytes - nextBytes,
            )
            statusMessage = "录像容量低于当前普通片段占用，请确认后再应用。"
        } else {
            pendingStorageCapacityChange = null
            applyStorageCapacityChange(capacityGb = capacityGb, cleanupNow = false)
        }
    }

    fun cleanupStorageNow() {
        val cleanup = storageManager.cleanupNormalSegments(settings.storageCapacityGb)
        refreshRecordingData()
        statusMessage = if (cleanup.deletedFiles > 0) {
            "已清理 ${cleanup.deletedFiles} 个普通片段，释放 ${cleanup.deletedBytes.asFileSize()}；锁定片段未受影响。"
        } else {
            "当前普通片段未超过 ${settings.storageCapacityGb}GB 容量，无需清理。"
        }
    }

    fun resetSettingsToDefaults() {
        pendingSettingsReset = false
        pendingStorageCapacityChange = null
        pendingGpsMetadataEnable = false
        persist(VoyageCamSettings())
        statusMessage = "已恢复默认设置；录像、紧急事件和已导出的证据包均未删除。"
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
        statusMessage = "正在启动后摄录制..."
        RecordingForegroundService.start(
            context = context,
            dualCamera = settings.dualCameraEnabled && capability.isAvailable,
            ambientAudio = settings.ambientAudioEnabled,
        )
        statusMessage = "后摄录制服务已启动；预览与录制共用 CameraX 管线，每 ${settings.segmentDurationMinutes} 分钟分段。"
    }

    fun applyGpsMetadataSetting(enabled: Boolean) {
        pendingGpsMetadataEnable = false
        persist(settings.copy(gpsMetadataEnabled = enabled))
        if (isRecording) {
            RecordingForegroundService.setGpsMetadataEnabled(context, enabled)
        }
        statusMessage = if (enabled) {
            "已开启GPS位置与轨迹记录；后续紧急事件会保存最近位置和轨迹。"
        } else {
            "已关闭GPS位置与轨迹记录；后续紧急事件不记录位置和轨迹。"
        }
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
        if (granted && pendingGpsMetadataEnable) {
            applyGpsMetadataSetting(true)
        } else if (!granted && pendingGpsMetadataEnable) {
            pendingGpsMetadataEnable = false
            persist(settings.copy(gpsMetadataEnabled = false))
            statusMessage = "定位权限未授权；已保持关闭GPS位置与轨迹记录。"
        } else {
            statusMessage = if (granted) {
                if (settings.gpsMetadataEnabled && isRecording) {
                    RecordingForegroundService.setGpsMetadataEnabled(context, true)
                }
                "定位权限已授权；紧急事件可记录最近可用坐标。"
            } else {
                "定位权限未授权；紧急事件仍会记录时间、触发类型和片段。"
            }
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
        refreshRecordingData()
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

    fun unlockSegment(segment: RecordingSegment) {
        if (!segment.locked) {
            statusMessage = "该片段已经是普通片段。"
            return
        }

        runCatching {
            val lockedDashcamPath = storageManager.dashcamRelativePath(File(segment.absolutePath))
            val unlockedFile = storageManager.unlockSegment(segment) ?: error("锁定片段不存在")
            emergencyEventStore.removeSegment(lockedDashcamPath)
            refreshRecordingData()
            emergencyEvents = emergencyEventStore.listRecentEvents()
            statusMessage = "已解除锁定：${unlockedFile.name}。该片段之后会按循环空间策略管理。"
        }.onFailure { error ->
            statusMessage = "解除锁定失败：${error.message ?: segment.name}"
        }
    }

    fun deleteSegment(segment: RecordingSegment) {
        runCatching {
            val dashcamPath = storageManager.dashcamRelativePath(File(segment.absolutePath))
            val result = storageManager.deleteSegment(segment)
            if (!result.deleted) error("片段不存在或不在可管理目录内")

            emergencyEventStore.removeSegment(dashcamPath)
            if (playbackItem?.file?.absolutePath == segment.absolutePath) {
                playbackItem = null
            }
            refreshRecordingData()
            emergencyEvents = emergencyEventStore.listRecentEvents()
            statusMessage = "已删除片段：${segment.name}，释放 ${result.deletedBytes.asFileSize()}。"
        }.onFailure { error ->
            statusMessage = "删除片段失败：${error.message ?: segment.name}"
        }
    }

    fun deleteEmergencyEvent(event: EmergencyEvent) {
        emergencyEventStore.deleteEvent(event.id)
        if (evidenceExportState?.eventId == event.id) {
            evidenceExportState = null
        }
        emergencyEvents = emergencyEventStore.listRecentEvents()
        statusMessage = "已删除紧急事件记录；关联录像仍保留在历史列表中。"
    }

    fun repairEmergencyEvents() {
        val result = emergencyEventStore.repairMissingSegments { path ->
            storageManager.dashcamFile(path)?.let { it.exists() && it.isFile } == true
        }
        emergencyEvents = emergencyEventStore.listRecentEvents()
        statusMessage = if (result.removedSegmentPaths == 0) {
            "紧急事件关联片段正常，无需修复。"
        } else {
            "已修复 ${result.updatedEvents} 个紧急事件，移除 ${result.removedSegmentPaths} 条失效片段引用；${result.emptyEvents} 个事件当前没有可用片段。"
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
            currentItem = "准备导出",
        )
        statusMessage = "正在导出紧急事件证据包..."

        val mainHandler = Handler(Looper.getMainLooper())
        val cancelFlag = AtomicBoolean(false)
        evidenceExportCancelFlag = cancelFlag
        Thread {
            val result = runCatching {
                evidenceExporter.export(
                    event = event,
                    includeWatermarkSubtitles = settings.exportWatermarkSubtitlesEnabled,
                    segmentDurationMinutes = settings.segmentDurationMinutes,
                    onProgress = { progress ->
                        mainHandler.post {
                            if (evidenceExportCancelFlag === cancelFlag) {
                                evidenceExportState = EvidenceExportState.Running(
                                    eventId = event.id,
                                    title = "${event.trigger.label} · ${event.triggeredAtMillis.asTime()}",
                                    progressPercent = progress.percent,
                                    currentItem = progress.currentItem,
                                )
                            }
                        }
                    },
                    isCancelled = { cancelFlag.get() },
                )
            }
            mainHandler.post {
                if (evidenceExportCancelFlag === cancelFlag) {
                    evidenceExportCancelFlag = null
                }
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
                        if (error is EvidenceExportCancelledException) {
                            evidenceExportState = EvidenceExportState.Cancelled(eventId = event.id)
                            statusMessage = "证据包导出已取消，原始录像未受影响。"
                        } else {
                            evidenceExportState = EvidenceExportState.Failed(
                                eventId = event.id,
                                message = error.message ?: "导出失败",
                            )
                            statusMessage = "证据包导出失败：${error.message ?: event.trigger.label}"
                        }
                    }
            }
        }.start()
    }

    fun cancelEvidenceExport() {
        val running = evidenceExportState as? EvidenceExportState.Running
        evidenceExportCancelFlag?.set(true)
        if (running != null) {
            evidenceExportState = running.copy(currentItem = "正在取消...")
            statusMessage = "正在取消证据包导出..."
        }
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
                            {
                                refreshRecordingData()
                                emergencyEvents = emergencyEventStore.listRecentEvents()
                            },
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
                    storageOverview = storageOverview,
                    pendingStorageCapacityGb = pendingStorageCapacityChange?.nextCapacityGb,
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
                        requestStorageCapacityChange(capacityGb)
                    },
                    onCleanupStorage = {
                        cleanupStorageNow()
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
                    onGpsMetadataChanged = { enabled ->
                        if (!enabled) {
                            applyGpsMetadataSetting(false)
                            return@SettingsPanel
                        }

                        if (hasLocationPermission()) {
                            applyGpsMetadataSetting(true)
                        } else {
                            pendingGpsMetadataEnable = true
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        }
                    },
                    onExportWatermarkSubtitlesChanged = { enabled ->
                        persist(settings.copy(exportWatermarkSubtitlesEnabled = enabled))
                        statusMessage = if (enabled) {
                            "证据包导出会附带时间/速度水印字幕；原始视频保持不变。"
                        } else {
                            "已关闭导出水印字幕；证据包只包含元数据、轨迹和原始片段。"
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
                    onRequestResetSettings = {
                        if (isRecording) {
                            statusMessage = "录制中不可恢复默认设置；停止后再操作。"
                        } else {
                            pendingSettingsReset = true
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

                if (pendingSettingsReset) {
                    SettingsResetConfirmationPanel(
                        onConfirm = {
                            resetSettingsToDefaults()
                        },
                        onCancel = {
                            pendingSettingsReset = false
                            statusMessage = "已取消恢复默认设置。"
                        },
                    )
                }

                pendingStorageCapacityChange?.let { pending ->
                    StorageCapacityConfirmationPanel(
                        pending = pending,
                        onConfirm = {
                            pendingStorageCapacityChange = null
                            applyStorageCapacityChange(
                                capacityGb = pending.nextCapacityGb,
                                cleanupNow = true,
                            )
                        },
                        onCancel = {
                            pendingStorageCapacityChange = null
                            statusMessage = "已取消调整录像容量。"
                        },
                    )
                }

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

                pendingEmergencyEventDelete?.let { event ->
                    EmergencyEventDeleteConfirmationPanel(
                        event = event,
                        onConfirm = {
                            pendingEmergencyEventDelete = null
                            deleteEmergencyEvent(event)
                        },
                        onCancel = {
                            pendingEmergencyEventDelete = null
                            statusMessage = "已取消删除紧急事件。"
                        },
                    )
                }

                EmergencyEventPanel(
                    events = emergencyEvents,
                    exportState = evidenceExportState,
                    onRefresh = {
                        emergencyEvents = emergencyEventStore.listRecentEvents()
                    },
                    onRepairMissingSegments = {
                        repairEmergencyEvents()
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
                    onCancelExport = {
                        cancelEvidenceExport()
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
                    onDelete = { event ->
                        pendingEmergencyEventDelete = event
                    },
                )

                pendingSegmentDelete?.let { segment ->
                    SegmentDeleteConfirmationPanel(
                        segment = segment,
                        onConfirm = {
                            pendingSegmentDelete = null
                            deleteSegment(segment)
                        },
                        onCancel = {
                            pendingSegmentDelete = null
                            statusMessage = "已取消删除录像片段。"
                        },
                    )
                }

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
                        refreshRecordingData()
                        emergencyEvents = emergencyEventStore.listRecentEvents()
                    },
                    onOpen = { segment ->
                        openSegment(segment)
                    },
                    onShare = { segment ->
                        shareSegment(segment)
                    },
                    onUnlock = { segment ->
                        unlockSegment(segment)
                    },
                    onDelete = { segment ->
                        pendingSegmentDelete = segment
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
        RearCameraPreview(enabled = true)
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
private fun SettingsResetConfirmationPanel(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    SectionCard {
        Text(
            text = "确认恢复默认设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "将恢复默认录制、存储、音频、GPS、水印字幕和自动启动设置。录像片段、锁定事件和已导出的证据包不会被删除。",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4D6267),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            ) {
                Text("取消")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            ) {
                Text("恢复默认")
            }
        }
    }
}

@Composable
private fun StorageCapacityConfirmationPanel(
    pending: PendingStorageCapacityChange,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    SectionCard {
        Text(
            text = "确认调整录像容量",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "目标容量 ${pending.nextCapacityGb}GB 低于当前普通片段占用 ${pending.currentNormalBytes.asFileSize()}。确认后会立即清理最旧的普通片段，锁定片段不会被删除。",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4D6267),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "预计至少需要释放 ${pending.overflowBytes.asFileSize()}。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9B2C2C),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            ) {
                Text("取消")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            ) {
                Text("确认清理")
            }
        }
    }
}

@Composable
private fun EmergencyEventDeleteConfirmationPanel(
    event: EmergencyEvent,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    SectionCard {
        Text(
            text = "确认删除紧急事件",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${event.trigger.label} · ${event.triggeredAtMillis.asTime()} · ${event.segmentPaths.size} 段关联录像",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4D6267),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "这只会删除事件记录和取证元数据，不会删除关联录像文件。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64777B),
        )
        Spacer(modifier = Modifier.height(12.dp))
        ConfirmationButtonRow(
            confirmLabel = "删除事件",
            onConfirm = onConfirm,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun SegmentDeleteConfirmationPanel(
    segment: RecordingSegment,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    SectionCard {
        Text(
            text = "确认删除录像片段",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163036),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${segment.cameraDirection.label} · ${if (segment.locked) "已锁定" else "普通"} · ${segment.sizeBytes.asFileSize()}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4D6267),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "确认后会删除本地视频文件，并从紧急事件记录中移除该片段引用。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9B2C2C),
        )
        Spacer(modifier = Modifier.height(12.dp))
        ConfirmationButtonRow(
            confirmLabel = "删除片段",
            onConfirm = onConfirm,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun ConfirmationButtonRow(
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        ) {
            Text("取消")
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
        ) {
            Text(confirmLabel)
        }
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
