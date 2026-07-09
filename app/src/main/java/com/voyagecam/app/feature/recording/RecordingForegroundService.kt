package com.voyagecam.app.feature.recording

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.voyagecam.app.core.camera.RearCameraRecorder
import com.voyagecam.app.core.camera.RecordingSegmentFileSet
import com.voyagecam.app.core.camera.RecordingSegmentTransitionStats
import com.voyagecam.app.core.model.DualCameraDiagnostic
import com.voyagecam.app.core.model.EmergencyLocationSnapshot
import com.voyagecam.app.core.model.EmergencyTrigger
import com.voyagecam.app.core.model.GpsTrackPoint
import com.voyagecam.app.data.camera.DualCameraDiagnosticsStore
import com.voyagecam.app.data.emergency.EmergencyEventStore
import com.voyagecam.app.data.location.EmergencyLocationProvider
import com.voyagecam.app.data.location.hasAnyLocationPermission
import com.voyagecam.app.data.settings.VoyageCamSettingsStore
import com.voyagecam.app.data.storage.RecordingStorageManager
import com.voyagecam.app.feature.collision.CollisionDetector
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordingForegroundService : Service(), RearCameraRecorder.Callbacks {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var cameraThread: HandlerThread
    private lateinit var cameraHandler: Handler
    private lateinit var notificationController: RecordingNotificationController
    private lateinit var settingsStore: VoyageCamSettingsStore
    private lateinit var dualCameraDiagnosticsStore: DualCameraDiagnosticsStore
    private lateinit var storageManager: RecordingStorageManager
    private lateinit var emergencyEventStore: EmergencyEventStore
    private lateinit var emergencyLocationProvider: EmergencyLocationProvider
    private var collisionDetector: CollisionDetector? = null
    private var recorder: RearCameraRecorder? = null
    private val state = RecordingServiceState()
    private val gpsTrackBuffer = mutableListOf<GpsTrackPoint>()

    private val updateNotificationTask = object : Runnable {
        override fun run() {
            if (state.startedAtMillis > 0L) {
                notifyRecordingState()
                mainHandler.postDelayed(this, NOTIFICATION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private val gpsTrackSampleTask = object : Runnable {
        override fun run() {
            sampleGpsTrackPoint()
            if (state.startedAtMillis > 0L) {
                mainHandler.postDelayed(this, GPS_TRACK_SAMPLE_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        cameraThread = HandlerThread("VoyageCamRearRecorder").apply { start() }
        cameraHandler = Handler(cameraThread.looper)
        notificationController = RecordingNotificationController(this)
        settingsStore = VoyageCamSettingsStore(this)
        dualCameraDiagnosticsStore = DualCameraDiagnosticsStore(this)
        storageManager = RecordingStorageManager(this)
        emergencyEventStore = EmergencyEventStore(this)
        emergencyLocationProvider = EmergencyLocationProvider(this)
        notificationController.ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_LOCK_CURRENT) {
            requestEmergencyLock()
            return START_STICKY
        }
        if (intent?.action == ACTION_SET_GPS_METADATA) {
            setGpsMetadataEnabled(intent.getBooleanExtra(EXTRA_GPS_METADATA_ENABLED, true))
            return if (state.startedAtMillis > 0L) START_STICKY else START_NOT_STICKY
        }
        if (state.startedAtMillis > 0L && recorder != null) {
            notifyRecordingState()
            return START_STICKY
        }

        val dualCamera = intent?.getBooleanExtra(EXTRA_DUAL_CAMERA, false) == true
        val ambientAudio = intent?.getBooleanExtra(EXTRA_AMBIENT_AUDIO, false) == true
        val settings = settingsStore.load()
        state.resetForStart(
            startedAtMillis = System.currentTimeMillis(),
            dualCamera = dualCamera,
            ambientAudio = ambientAudio,
            gpsMetadataEnabled = settings.gpsMetadataEnabled,
            storageCapacityGb = settings.storageCapacityGb,
            segmentDurationMinutes = settings.segmentDurationMinutes,
            collisionSensitivity = settings.collisionSensitivity,
        )
        gpsTrackBuffer.clear()

        serviceScope.launch(Dispatchers.IO) {
            storageManager.cleanupNormalSegments(state.storageCapacityGb)
        }
        val notification = notificationController.build(notificationState())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                RecordingNotificationController.NOTIFICATION_ID,
                notification,
                foregroundServiceType(),
            )
        } else {
            startForeground(RecordingNotificationController.NOTIFICATION_ID, notification)
        }

        recorder?.stop()
        collisionDetector?.stop()
        recorder = RearCameraRecorder(
            context = this,
            cameraHandler = cameraHandler,
            storageManager = storageManager,
            callbacks = this,
        ).also { recorder ->
            recorder.start(
                ambientAudioRequested = state.ambientAudio,
                segmentDurationMinutes = state.segmentDurationMinutes,
                dualCameraRequested = state.dualCamera,
            )
        }
        startCollisionDetection()
        startGpsTrackSampling()

        mainHandler.removeCallbacks(updateNotificationTask)
        mainHandler.post(updateNotificationTask)
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(updateNotificationTask)
        mainHandler.removeCallbacks(gpsTrackSampleTask)
        emergencyLocationProvider.stopUpdates()
        collisionDetector?.stop()
        collisionDetector = null
        recorder?.stop()
        recorder = null
        serviceScope.cancel()
        state.clearAfterStop()
        cameraThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onRecordingStarted(files: RecordingSegmentFileSet, segmentIndex: Int) {
        state.currentSegmentIndex = segmentIndex
        state.currentSegmentFiles = files
        state.currentFileName = files.primary?.name
        if (!state.dualCamera) {
            state.dualCameraDiagnostic = null
        } else if (files.front != null) {
            state.dualCameraDiagnostic = "双摄并发录制正常"
            serviceScope.launch(Dispatchers.IO) {
                dualCameraDiagnosticsStore.clear()
            }
        }
        state.status = if (state.dualCamera) {
            if (files.front != null) {
                "第 $segmentIndex 段前后双摄正在写入"
            } else {
                "第 $segmentIndex 段正在写入；本次已降级为后摄单录"
            }
        } else {
            "第 $segmentIndex 段后摄单录正在写入"
        }
        notifyRecordingState()
    }

    override fun onSegmentFinalized(files: RecordingSegmentFileSet) {
        if (state.pendingLockNextSegment) {
            val eventId = state.pendingLockNextEventId
            state.pendingLockNextSegment = false
            state.pendingLockNextEventId = null
            lockSegmentsAsync(files, eventId) { finalizedFiles ->
                onFinalizedFilesReady(finalizedFiles)
            }
        } else {
            serviceScope.launch(Dispatchers.IO) {
                files.files.forEach { file -> storageManager.indexSegmentFile(file, locked = false) }
                val cleanup = storageManager.cleanupNormalSegments(state.storageCapacityGb)
                withContext(Dispatchers.Main) {
                    onFinalizedFilesReady(files, cleanup)
                }
            }
        }
    }

    private fun onFinalizedFilesReady(
        finalizedFiles: RecordingSegmentFileSet,
        cleanup: RecordingStorageManager.CleanupResult? = null,
    ) {
        state.previousSegmentFiles = finalizedFiles
        state.currentSegmentFiles = RecordingSegmentFileSet(rear = null)
        state.currentFileName = finalizedFiles.primary?.name ?: state.currentFileName
        state.status = buildString {
            append(if (finalizedFiles.files.any { it.name.contains("_locked") }) "相邻片段已锁定" else "片段已完成")
            if (cleanup != null && cleanup.deletedFiles > 0) {
                append("，已清理 ${cleanup.deletedFiles} 个旧普通片段")
            }
        }
        notifyRecordingState()
    }

    override fun onSegmentLockRequested(files: RecordingSegmentFileSet) {
        val eventId = state.pendingLockNextEventId
        lockSegmentsAsync(files, eventId) { lockedCurrent ->
            lockSegmentsAsync(state.previousSegmentFiles, eventId) { lockedPrevious ->
                state.previousSegmentFiles = lockedCurrent.takeIf { it.primary != null }
                    ?: lockedPrevious.takeIf { it.primary != null }
                    ?: state.previousSegmentFiles
                state.currentSegmentFiles = RecordingSegmentFileSet(rear = null)
                state.currentFileName = lockedCurrent.primary?.name ?: lockedPrevious.primary?.name ?: state.currentFileName
                state.pendingLockNextSegment = true
                state.status = "紧急锁定已触发，当前/上一片段已保护，下一片段完成后也会锁定"
                notifyRecordingState()
            }
        }
    }

    override fun onRecordingStopped(files: RecordingSegmentFileSet) {
        if (state.pendingLockNextSegment) {
            val eventId = state.pendingLockNextEventId
            state.pendingLockNextSegment = false
            state.pendingLockNextEventId = null
            lockSegmentsAsync(files, eventId) { finalFiles ->
                onRecordingStoppedFilesReady(finalFiles)
            }
        } else {
            serviceScope.launch(Dispatchers.IO) {
                files.files.forEach { file -> storageManager.indexSegmentFile(file, locked = false) }
                withContext(Dispatchers.Main) {
                    onRecordingStoppedFilesReady(files)
                }
            }
        }
    }

    private fun onRecordingStoppedFilesReady(finalFiles: RecordingSegmentFileSet) {
        state.previousSegmentFiles = finalFiles.takeIf { it.primary != null } ?: state.previousSegmentFiles
        state.currentSegmentFiles = RecordingSegmentFileSet(rear = null)
        state.currentFileName = finalFiles.primary?.name ?: state.currentFileName
        state.status = "录制已停止"
        notifyRecordingState()
    }

    override fun onRecordingError(message: String) {
        state.status = message
        notifyRecordingState()
    }

    override fun onDualCameraFallback(diagnostic: DualCameraDiagnostic) {
        state.dualCamera = false
        state.dualCameraDiagnostic = diagnostic.summary()
        serviceScope.launch(Dispatchers.IO) {
            dualCameraDiagnosticsStore.record(diagnostic)
        }
        state.status = "双摄录制启动失败，已回落后摄单录：${diagnostic.summary()}"
        notifyRecordingState()
    }

    private fun requestEmergencyLock(collisionEvent: CollisionDetector.CollisionEvent? = null) {
        val activeRecorder = recorder
        if (activeRecorder == null || state.startedAtMillis <= 0L) {
            state.status = "当前没有正在录制的片段可锁定"
            notifyRecordingState()
            return
        }

        state.status = collisionEvent?.let {
            val acceleration = String.format(Locale.getDefault(), "%.1f", it.accelerationG)
            "检测到疑似碰撞 ${acceleration}g，正在创建紧急事件"
        } ?: "正在创建紧急事件"
        notifyRecordingState()

        val triggeredAtMillis = collisionEvent?.triggeredAtMillis ?: System.currentTimeMillis()
        val location = if (state.gpsMetadataEnabled) emergencyLocationProvider.currentSnapshot() else null
        val gpsTrackPoints = if (state.gpsMetadataEnabled) {
            recentGpsTrackPoints(triggeredAtMillis)
        } else {
            emptyList()
        }
        serviceScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    emergencyEventStore.createEvent(
                        trigger = if (collisionEvent == null) EmergencyTrigger.Manual else EmergencyTrigger.Collision,
                        triggeredAtMillis = triggeredAtMillis,
                        accelerationG = collisionEvent?.accelerationG,
                        thresholdG = collisionEvent?.thresholdG,
                        location = location,
                        gpsTrackPoints = gpsTrackPoints,
                    )
                }
            }
            result
                .onSuccess { event ->
                    state.pendingLockNextEventId = event.id
                    state.status = collisionEvent?.let {
                        val acceleration = String.format(Locale.getDefault(), "%.1f", it.accelerationG)
                        "检测到疑似碰撞 ${acceleration}g，正在执行紧急锁定"
                    } ?: "正在执行紧急锁定"
                    notifyRecordingState()
                    activeRecorder.lockCurrentSegment()
                }
                .onFailure { error ->
                    state.pendingLockNextEventId = null
                    state.status = "紧急事件创建失败：${error.message ?: "未知错误"}"
                    notifyRecordingState()
                }
        }
    }

    private fun startCollisionDetection() {
        collisionDetector?.stop()
        collisionDetector = CollisionDetector(
            context = this,
            sensitivity = state.collisionSensitivity,
        ) { event ->
            mainHandler.post {
                requestEmergencyLock(event)
            }
        }

        val started = collisionDetector?.start() == true
        if (!started) {
            collisionDetector = null
            state.status = "设备未提供可用加速度传感器，自动碰撞锁定不可用"
            notifyRecordingState()
        }
    }

    private fun startGpsTrackSampling() {
        mainHandler.removeCallbacks(gpsTrackSampleTask)
        if (!state.gpsMetadataEnabled || !hasAnyLocationPermission()) return
        emergencyLocationProvider.startUpdates()
        emergencyLocationProvider.currentSnapshot()?.toGpsTrackPoint()?.let { point ->
            if (gpsTrackBuffer.lastOrNull()?.capturedAtMillis != point.capturedAtMillis) {
                gpsTrackBuffer += point
            }
        }
        mainHandler.postDelayed(gpsTrackSampleTask, GPS_TRACK_SAMPLE_INTERVAL_MS)
    }

    private fun sampleGpsTrackPoint() {
        if (!state.gpsMetadataEnabled) return
        val point = emergencyLocationProvider.currentSnapshot()?.toGpsTrackPoint() ?: return
        if (gpsTrackBuffer.lastOrNull()?.capturedAtMillis == point.capturedAtMillis) return
        gpsTrackBuffer += point
        pruneGpsTrackBuffer(System.currentTimeMillis())
    }

    private fun recentGpsTrackPoints(triggeredAtMillis: Long): List<GpsTrackPoint> {
        if (!state.gpsMetadataEnabled) return emptyList()
        val continuousPoints = emergencyLocationProvider
            .recentSnapshots(
                triggeredAtMillis = triggeredAtMillis,
                retentionMillis = GPS_TRACK_RETENTION_MS,
                limit = MAX_GPS_TRACK_POINTS,
            )
            .map { it.toGpsTrackPoint() }
        if (continuousPoints.isNotEmpty()) return continuousPoints

        sampleGpsTrackPoint()
        pruneGpsTrackBuffer(triggeredAtMillis)
        val startMillis = triggeredAtMillis - GPS_TRACK_RETENTION_MS
        val endMillis = triggeredAtMillis + GPS_TRACK_SAMPLE_INTERVAL_MS
        return gpsTrackBuffer
            .filter { point -> point.capturedAtMillis in startMillis..endMillis }
            .takeLast(MAX_GPS_TRACK_POINTS)
    }

    private fun pruneGpsTrackBuffer(nowMillis: Long) {
        val cutoff = nowMillis - GPS_TRACK_RETENTION_MS
        gpsTrackBuffer.removeAll { it.capturedAtMillis < cutoff }
        if (gpsTrackBuffer.size > MAX_GPS_TRACK_POINTS) {
            val overflow = gpsTrackBuffer.size - MAX_GPS_TRACK_POINTS
            repeat(overflow) { gpsTrackBuffer.removeAt(0) }
        }
    }

    private fun EmergencyLocationSnapshot.toGpsTrackPoint(): GpsTrackPoint {
        return GpsTrackPoint(
            latitude = latitude,
            longitude = longitude,
            speedMetersPerSecond = speedMetersPerSecond,
            bearingDegrees = bearingDegrees,
            capturedAtMillis = capturedAtMillis,
        )
    }

    private fun setGpsMetadataEnabled(enabled: Boolean) {
        state.gpsMetadataEnabled = enabled
        if (enabled) {
            startGpsTrackSampling()
            state.status = "GPS位置与轨迹记录已开启"
        } else {
            mainHandler.removeCallbacks(gpsTrackSampleTask)
            emergencyLocationProvider.stopUpdates()
            gpsTrackBuffer.clear()
            state.status = "GPS位置与轨迹记录已关闭"
        }
        if (state.startedAtMillis > 0L) {
            notifyRecordingState()
        }
    }

    override fun onSegmentTransitionMeasured(stats: RecordingSegmentTransitionStats) {
        val stopText = stats.stopToFinalizeMillis?.let { "${it}ms" } ?: "未知"
        state.segmentTransitionSummary = "上次分段间隙 finalize→start ${stats.finalizeToNextStartMillis}ms，stop→finalize $stopText"
        state.status = "第 ${stats.completedSegmentIndex} 段切换完成，${state.segmentTransitionSummary}"
        notifyRecordingState()
    }

    private fun notifyRecordingState() {
        notificationController.notify(notificationState())
    }

    private fun notificationState(): RecordingNotificationState {
        return state.notificationState()
    }

    private fun lockSegmentsAsync(
        files: RecordingSegmentFileSet,
        eventId: String?,
        onComplete: (RecordingSegmentFileSet) -> Unit,
    ) {
        serviceScope.launch(Dispatchers.IO) {
            val lockedFiles = RecordingSegmentFileSet(
                rear = lockSegment(files.rear, eventId),
                front = lockSegment(files.front, eventId),
            )
            withContext(Dispatchers.Main) {
                onComplete(lockedFiles)
            }
        }
    }

    private suspend fun lockSegment(file: File?, eventId: String?): File? {
        val lockedFile = storageManager.lockNormalSegment(file)
        if (lockedFile != null && lockedFile != file) {
            withContext(Dispatchers.Main) {
                state.lockedSegmentCount++
            }
        }
        val segmentPath = storageManager.dashcamRelativePath(lockedFile)
        if (!eventId.isNullOrBlank() && !segmentPath.isNullOrBlank()) {
            emergencyEventStore.addLockedSegment(
                eventId = eventId,
                segmentPath = segmentPath,
            )
        }
        return lockedFile
    }

    private fun foregroundServiceType(): Int {
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (state.ambientAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (state.gpsMetadataEnabled && hasAnyLocationPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        return type
    }

    companion object {
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1_000L
        private const val GPS_TRACK_SAMPLE_INTERVAL_MS = 10_000L
        private const val GPS_TRACK_RETENTION_MS = 5 * 60 * 1000L
        private const val MAX_GPS_TRACK_POINTS = 60
        const val ACTION_STOP = "com.voyagecam.app.action.STOP_RECORDING"
        const val ACTION_LOCK_CURRENT = "com.voyagecam.app.action.LOCK_CURRENT"
        private const val ACTION_SET_GPS_METADATA = "com.voyagecam.app.action.SET_GPS_METADATA"
        private const val EXTRA_DUAL_CAMERA = "extra_dual_camera"
        private const val EXTRA_AMBIENT_AUDIO = "extra_ambient_audio"
        private const val EXTRA_GPS_METADATA_ENABLED = "extra_gps_metadata_enabled"

        fun start(context: Context, dualCamera: Boolean, ambientAudio: Boolean) {
            ContextCompat.startForegroundService(
                context,
                startIntent(
                    context = context,
                    dualCamera = dualCamera,
                    ambientAudio = ambientAudio,
                ),
            )
        }

        fun startPendingIntent(
            context: Context,
            requestCode: Int,
            dualCamera: Boolean,
            ambientAudio: Boolean,
        ): PendingIntent {
            return PendingIntent.getForegroundService(
                context,
                requestCode,
                startIntent(
                    context = context,
                    dualCamera = dualCamera,
                    ambientAudio = ambientAudio,
                ),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun startIntent(context: Context, dualCamera: Boolean, ambientAudio: Boolean): Intent {
            return Intent(context, RecordingForegroundService::class.java)
                .putExtra(EXTRA_DUAL_CAMERA, dualCamera)
                .putExtra(EXTRA_AMBIENT_AUDIO, ambientAudio)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun lockCurrent(context: Context) {
            val intent = Intent(context, RecordingForegroundService::class.java).setAction(ACTION_LOCK_CURRENT)
            context.startService(intent)
        }

        fun setGpsMetadataEnabled(context: Context, enabled: Boolean) {
            val intent = Intent(context, RecordingForegroundService::class.java)
                .setAction(ACTION_SET_GPS_METADATA)
                .putExtra(EXTRA_GPS_METADATA_ENABLED, enabled)
            context.startService(intent)
        }
    }
}
