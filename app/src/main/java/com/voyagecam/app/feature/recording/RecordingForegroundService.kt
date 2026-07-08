package com.voyagecam.app.feature.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.voyagecam.app.MainActivity
import com.voyagecam.app.R
import com.voyagecam.app.core.camera.RearCameraRecorder
import com.voyagecam.app.core.model.CollisionSensitivity
import com.voyagecam.app.core.model.EmergencyLocationSnapshot
import com.voyagecam.app.core.model.EmergencyTrigger
import com.voyagecam.app.core.model.GpsTrackPoint
import com.voyagecam.app.data.emergency.EmergencyEventStore
import com.voyagecam.app.data.location.EmergencyLocationProvider
import com.voyagecam.app.data.location.hasAnyLocationPermission
import com.voyagecam.app.data.settings.VoyageCamSettingsStore
import com.voyagecam.app.data.storage.RecordingStorageManager
import com.voyagecam.app.feature.collision.CollisionDetector
import java.io.File
import java.util.Locale

class RecordingForegroundService : Service(), RearCameraRecorder.Callbacks {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var cameraThread: HandlerThread
    private lateinit var cameraHandler: Handler
    private lateinit var settingsStore: VoyageCamSettingsStore
    private lateinit var storageManager: RecordingStorageManager
    private lateinit var emergencyEventStore: EmergencyEventStore
    private lateinit var emergencyLocationProvider: EmergencyLocationProvider
    private var collisionDetector: CollisionDetector? = null
    private var recorder: RearCameraRecorder? = null
    private var startedAtMillis = 0L
    private var dualCamera = false
    private var ambientAudio = false
    private var gpsMetadataEnabled = true
    private var storageCapacityGb = VoyageCamSettingsStore.MIN_STORAGE_GB
    private var segmentDurationMinutes = 3
    private var collisionSensitivity = CollisionSensitivity.Medium
    private var currentSegmentIndex = 0
    private var recordingStatus = "正在准备后置摄像头"
    private var currentFileName: String? = null
    private var previousSegmentFile: File? = null
    private var currentSegmentFile: File? = null
    private var pendingLockNextSegment = false
    private var pendingLockNextEventId: String? = null
    private var lockedSegmentCount = 0
    private val gpsTrackBuffer = mutableListOf<GpsTrackPoint>()

    private val updateNotificationTask = object : Runnable {
        override fun run() {
            if (startedAtMillis > 0L) {
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
                mainHandler.postDelayed(this, NOTIFICATION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private val gpsTrackSampleTask = object : Runnable {
        override fun run() {
            sampleGpsTrackPoint()
            if (startedAtMillis > 0L) {
                mainHandler.postDelayed(this, GPS_TRACK_SAMPLE_INTERVAL_MS)
            }
        }
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        cameraThread = HandlerThread("VoyageCamRearRecorder").apply { start() }
        cameraHandler = Handler(cameraThread.looper)
        settingsStore = VoyageCamSettingsStore(this)
        storageManager = RecordingStorageManager(this)
        emergencyEventStore = EmergencyEventStore(this)
        emergencyLocationProvider = EmergencyLocationProvider(this)
        ensureNotificationChannel()
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
            return if (startedAtMillis > 0L) START_STICKY else START_NOT_STICKY
        }
        if (startedAtMillis > 0L && recorder != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
            return START_STICKY
        }

        dualCamera = intent?.getBooleanExtra(EXTRA_DUAL_CAMERA, false) == true
        ambientAudio = intent?.getBooleanExtra(EXTRA_AMBIENT_AUDIO, false) == true
        val settings = settingsStore.load()
        storageCapacityGb = settings.storageCapacityGb
        segmentDurationMinutes = settings.segmentDurationMinutes
        collisionSensitivity = settings.collisionSensitivity
        gpsMetadataEnabled = settings.gpsMetadataEnabled
        startedAtMillis = System.currentTimeMillis()
        currentSegmentIndex = 0
        recordingStatus = "正在准备后置摄像头，每 ${segmentDurationMinutes} 分钟自动分段"
        currentFileName = null
        previousSegmentFile = null
        currentSegmentFile = null
        pendingLockNextSegment = false
        pendingLockNextEventId = null
        lockedSegmentCount = 0
        gpsTrackBuffer.clear()

        storageManager.cleanupNormalSegments(storageCapacityGb)
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                foregroundServiceType(),
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
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
                ambientAudioRequested = ambientAudio,
                segmentDurationMinutes = segmentDurationMinutes,
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
        collisionDetector?.stop()
        collisionDetector = null
        recorder?.stop()
        recorder = null
        startedAtMillis = 0L
        cameraThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onRecordingStarted(file: File?, segmentIndex: Int) {
        currentSegmentIndex = segmentIndex
        currentSegmentFile = file
        currentFileName = file?.name
        recordingStatus = if (dualCamera) {
            "第 $segmentIndex 段正在写入；双摄录制尚未接入，本次降级为后摄单录"
        } else {
            "第 $segmentIndex 段后摄单录正在写入"
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onSegmentFinalized(file: File?) {
        val finalizedFile = if (pendingLockNextSegment) {
            val eventId = pendingLockNextEventId
            pendingLockNextSegment = false
            pendingLockNextEventId = null
            lockSegment(file, eventId)
        } else {
            file
        }
        val cleanup = storageManager.cleanupNormalSegments(storageCapacityGb)
        previousSegmentFile = finalizedFile
        currentSegmentFile = null
        currentFileName = finalizedFile?.name ?: currentFileName
        recordingStatus = buildString {
            append(if (finalizedFile?.name?.contains("_locked") == true) "相邻片段已锁定" else "片段已完成")
            if (cleanup.deletedFiles > 0) {
                append("，已清理 ${cleanup.deletedFiles} 个旧普通片段")
            }
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onSegmentLockRequested(file: File?) {
        val eventId = pendingLockNextEventId
        val lockedCurrent = lockSegment(file, eventId)
        val lockedPrevious = lockSegment(previousSegmentFile, eventId)
        previousSegmentFile = lockedCurrent ?: lockedPrevious ?: previousSegmentFile
        currentSegmentFile = null
        currentFileName = lockedCurrent?.name ?: lockedPrevious?.name ?: currentFileName
        pendingLockNextSegment = true
        recordingStatus = "紧急锁定已触发，当前/上一片段已保护，下一片段完成后也会锁定"
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onRecordingStopped(file: File?) {
        val finalFile = if (pendingLockNextSegment) {
            val eventId = pendingLockNextEventId
            pendingLockNextSegment = false
            pendingLockNextEventId = null
            lockSegment(file, eventId)
        } else {
            file
        }
        previousSegmentFile = finalFile ?: previousSegmentFile
        currentSegmentFile = null
        currentFileName = finalFile?.name ?: currentFileName
        recordingStatus = "录制已停止"
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onRecordingError(message: String) {
        recordingStatus = message
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "VoyageCam 录制状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示行车记录录制状态，并提供停止入口"
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val mode = if (dualCamera) "双摄设置已开" else "后摄单录"
        val audio = if (ambientAudio) "环境声开启" else "静音"
        val segment = "${segmentDurationMinutes}分钟分段"
        val locked = if (lockedSegmentCount > 0) " · 已锁定${lockedSegmentCount}段" else ""
        val elapsed = ((System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)) / 1000L
        val elapsedText = String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            elapsed / 3600,
            elapsed % 3600 / 60,
            elapsed % 60,
        )
        val fileText = currentFileName?.let { " · $it" }.orEmpty()

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val lockIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, RecordingForegroundService::class.java).setAction(ACTION_LOCK_CURRENT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("VoyageCam 正在录制")
            .setContentText("$mode · $segment$locked · $elapsedText$fileText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$recordingStatus · $mode · $audio · $segment$locked · ${storageCapacityGb}GB循环空间 · 已运行 $elapsedText$fileText"),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, "紧急锁定", lockIntent)
            .addAction(0, "停止录制", stopIntent)
            .build()
    }

    private fun requestEmergencyLock(collisionEvent: CollisionDetector.CollisionEvent? = null) {
        val activeRecorder = recorder
        if (activeRecorder == null || startedAtMillis <= 0L) {
            recordingStatus = "当前没有正在录制的片段可锁定"
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
            return
        }

        val event = emergencyEventStore.createEvent(
            trigger = if (collisionEvent == null) EmergencyTrigger.Manual else EmergencyTrigger.Collision,
            triggeredAtMillis = collisionEvent?.triggeredAtMillis ?: System.currentTimeMillis(),
            accelerationG = collisionEvent?.accelerationG,
            thresholdG = collisionEvent?.thresholdG,
            location = if (gpsMetadataEnabled) emergencyLocationProvider.currentSnapshot() else null,
            gpsTrackPoints = if (gpsMetadataEnabled) {
                recentGpsTrackPoints(collisionEvent?.triggeredAtMillis ?: System.currentTimeMillis())
            } else {
                emptyList()
            },
        )
        pendingLockNextEventId = event.id
        recordingStatus = collisionEvent?.let {
            val acceleration = String.format(Locale.getDefault(), "%.1f", it.accelerationG)
            "检测到疑似碰撞 ${acceleration}g，正在执行紧急锁定"
        } ?: "正在执行紧急锁定"
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
        activeRecorder.lockCurrentSegment()
    }

    private fun startCollisionDetection() {
        collisionDetector?.stop()
        collisionDetector = CollisionDetector(
            context = this,
            sensitivity = collisionSensitivity,
        ) { event ->
            mainHandler.post {
                requestEmergencyLock(event)
            }
        }

        val started = collisionDetector?.start() == true
        if (!started) {
            collisionDetector = null
            recordingStatus = "设备未提供可用加速度传感器，自动碰撞锁定不可用"
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun startGpsTrackSampling() {
        mainHandler.removeCallbacks(gpsTrackSampleTask)
        if (!gpsMetadataEnabled || !hasAnyLocationPermission()) return
        mainHandler.post(gpsTrackSampleTask)
    }

    private fun sampleGpsTrackPoint() {
        if (!gpsMetadataEnabled) return
        val point = emergencyLocationProvider.currentSnapshot()?.toGpsTrackPoint() ?: return
        if (gpsTrackBuffer.lastOrNull()?.capturedAtMillis == point.capturedAtMillis) return
        gpsTrackBuffer += point
        pruneGpsTrackBuffer(System.currentTimeMillis())
    }

    private fun recentGpsTrackPoints(triggeredAtMillis: Long): List<GpsTrackPoint> {
        if (!gpsMetadataEnabled) return emptyList()
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
        gpsMetadataEnabled = enabled
        if (enabled) {
            startGpsTrackSampling()
            recordingStatus = "GPS位置与轨迹记录已开启"
        } else {
            mainHandler.removeCallbacks(gpsTrackSampleTask)
            gpsTrackBuffer.clear()
            recordingStatus = "GPS位置与轨迹记录已关闭"
        }
        if (startedAtMillis > 0L) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun lockSegment(file: File?, eventId: String?): File? {
        val lockedFile = storageManager.lockNormalSegment(file)
        if (lockedFile != null && lockedFile != file) {
            lockedSegmentCount++
        }
        emergencyEventStore.addLockedSegment(
            eventId = eventId,
            segmentPath = storageManager.dashcamRelativePath(lockedFile),
        )
        return lockedFile
    }

    private fun foregroundServiceType(): Int {
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (ambientAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (gpsMetadataEnabled && hasAnyLocationPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        return type
    }

    companion object {
        private const val CHANNEL_ID = "voyage_cam_recording"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1_000L
        private const val GPS_TRACK_SAMPLE_INTERVAL_MS = 10_000L
        private const val GPS_TRACK_RETENTION_MS = 5 * 60 * 1000L
        private const val MAX_GPS_TRACK_POINTS = 60
        private const val ACTION_STOP = "com.voyagecam.app.action.STOP_RECORDING"
        private const val ACTION_LOCK_CURRENT = "com.voyagecam.app.action.LOCK_CURRENT"
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
