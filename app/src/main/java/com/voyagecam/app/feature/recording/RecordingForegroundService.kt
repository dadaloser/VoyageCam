package com.voyagecam.app.feature.recording

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.voyagecam.app.R
import com.voyagecam.app.core.camera.RearCameraRecorder
import com.voyagecam.app.core.camera.RecordingSegmentFileSet
import com.voyagecam.app.core.camera.RecordingSegmentTransitionStats
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.core.model.DualCameraDiagnostic
import com.voyagecam.app.core.model.EmergencyLocationSnapshot
import com.voyagecam.app.core.model.EmergencyTrigger
import com.voyagecam.app.core.model.GpsTrackPoint
import com.voyagecam.app.data.camera.DualCameraDiagnosticsStore
import com.voyagecam.app.data.emergency.EmergencyEventStore
import com.voyagecam.app.data.location.EmergencyLocationProvider
import com.voyagecam.app.data.location.hasAnyLocationPermission
import com.voyagecam.app.data.settings.VoyageCamSettingsStore
import com.voyagecam.app.data.settings.recordingFallbackSummary
import com.voyagecam.app.data.settings.resolveRecordingConfig
import com.voyagecam.app.data.settings.recordingVideoProfile
import com.voyagecam.app.data.storage.RecordingStorageManager
import com.voyagecam.app.data.telemetry.VoyageCamRuntimeTelemetry
import com.voyagecam.app.feature.collision.CollisionDetector
import com.voyagecam.app.core.model.DualCameraFailureSource
import com.voyagecam.app.core.model.StructuredLogLevel
import com.voyagecam.app.ui.dualCameraDiagnosticSummary
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

    private val performanceGuardTask = object : Runnable {
        override fun run() {
            evaluatePerformanceGuard()
            if (state.startedAtMillis > 0L) {
                mainHandler.postDelayed(this, PERFORMANCE_GUARD_INTERVAL_MS)
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
        VoyageCamRuntimeTelemetry.log(
            level = StructuredLogLevel.Info,
            category = VoyageCamRuntimeTelemetry.CATEGORY_RECORDING,
            event = "service_created",
            message = "Recording foreground service created",
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            VoyageCamRuntimeTelemetry.log(
                level = StructuredLogLevel.Info,
                category = VoyageCamRuntimeTelemetry.CATEGORY_RECORDING,
                event = "service_stop_requested",
                message = "Stop action received",
            )
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_LOCK_CURRENT) {
            VoyageCamRuntimeTelemetry.log(
                level = StructuredLogLevel.Info,
                category = VoyageCamRuntimeTelemetry.CATEGORY_RECORDING,
                event = "emergency_lock_requested",
                message = "Emergency lock action received",
            )
            requestEmergencyLock()
            return START_STICKY
        }
        if (intent?.action == ACTION_SET_GPS_METADATA) {
            setGpsMetadataEnabled(intent.getBooleanExtra(EXTRA_GPS_METADATA_ENABLED, true))
            return if (state.startedAtMillis > 0L) START_STICKY else START_NOT_STICKY
        }
        if (intent?.action == ACTION_REFRESH_PERFORMANCE_GUARD) {
            return if (state.startedAtMillis > 0L) {
                evaluatePerformanceGuard()
                notifyRecordingState()
                START_STICKY
            } else {
                START_NOT_STICKY
            }
        }
        if (state.startedAtMillis > 0L && recorder != null) {
            notifyRecordingState()
            return START_STICKY
        }

        val settings = settingsStore.load()
        val resolvedConfig = settings.resolveRecordingConfig(settingsStore.loadCapability())
        state.resetForStart(
            context = this,
            startedAtMillis = System.currentTimeMillis(),
            requestedMode = settings.recordingMode,
            primaryCameraDirection = resolvedConfig.primaryCameraDirection,
            dualCamera = resolvedConfig.dualCameraActive,
            ambientAudio = resolvedConfig.ambientAudioActive,
            recordingResolutionLabel = settings.recordingResolution.label,
            recordingFrameRateLabel = settings.recordingFrameRate.label,
            recordingBitrateLabel = settings.recordingBitrate.label,
            gpsMetadataEnabled = settings.gpsMetadataEnabled,
            storageCapacityGb = settings.storageCapacityGb,
            segmentDurationMinutes = settings.segmentDurationMinutes,
            collisionSensitivity = settings.collisionSensitivity,
        )
        state.fallbackSummary = resolvedConfig.downgradeReason?.let(::recordingFallbackSummary)
        gpsTrackBuffer.clear()
        VoyageCamRuntimeTelemetry.log(
            level = StructuredLogLevel.Info,
            category = VoyageCamRuntimeTelemetry.CATEGORY_RECORDING,
            event = "recording_start_requested",
            message = "Preparing recording session",
            attributes = mapOf(
                "requestedMode" to settings.recordingMode.name,
                "effectivePrimaryCamera" to resolvedConfig.primaryCameraDirection.name,
                "dualCamera" to resolvedConfig.dualCameraActive.toString(),
                "ambientAudio" to resolvedConfig.ambientAudioActive.toString(),
                "frontMirror" to resolvedConfig.frontCameraMirrorActive.toString(),
                "orientationStrategy" to resolvedConfig.orientationStrategy.name,
                "downgradeReason" to (resolvedConfig.downgradeReason?.name ?: ""),
                "resolution" to settings.recordingResolution.label,
                "frameRate" to settings.recordingFrameRate.label,
                "bitrate" to settings.recordingBitrate.label,
                "segmentMinutes" to settings.segmentDurationMinutes.toString(),
            ),
        )

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
                primaryCameraDirection = state.primaryCameraDirection,
                frontMirrorEnabled = resolvedConfig.frontCameraMirrorActive,
                orientationStrategy = resolvedConfig.orientationStrategy,
                videoProfile = settings.recordingVideoProfile(),
            )
        }
        startCollisionDetection()
        startGpsTrackSampling()

        mainHandler.removeCallbacks(updateNotificationTask)
        mainHandler.post(updateNotificationTask)
        mainHandler.removeCallbacks(performanceGuardTask)
        mainHandler.postDelayed(performanceGuardTask, PERFORMANCE_GUARD_INTERVAL_MS)
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(updateNotificationTask)
        mainHandler.removeCallbacks(gpsTrackSampleTask)
        mainHandler.removeCallbacks(performanceGuardTask)
        emergencyLocationProvider.stopUpdates()
        collisionDetector?.stop()
        collisionDetector = null
        recorder?.stop()
        recorder = null
        serviceScope.cancel()
        state.clearAfterStop()
        cameraThread.quitSafely()
        VoyageCamRuntimeTelemetry.log(
            level = StructuredLogLevel.Info,
            category = VoyageCamRuntimeTelemetry.CATEGORY_RECORDING,
            event = "service_destroyed",
            message = "Recording foreground service destroyed",
        )
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
            state.fallbackSummary = null
            state.dualCameraDiagnostic = getString(R.string.recording_service_dual_ok)
            serviceScope.launch(Dispatchers.IO) {
                dualCameraDiagnosticsStore.clear()
            }
        }
        state.status = if (state.dualCamera) {
            if (files.front != null) {
                getString(R.string.recording_service_segment_dual_writing, segmentIndex)
            } else {
                getString(R.string.recording_service_segment_rear_fallback, segmentIndex)
            }
        } else if (state.primaryCameraDirection == CameraDirection.Front) {
            getString(R.string.recording_service_segment_front_writing, segmentIndex)
        } else {
            getString(R.string.recording_service_segment_rear_writing, segmentIndex)
        }
        VoyageCamRuntimeTelemetry.log(
            level = StructuredLogLevel.Info,
            category = VoyageCamRuntimeTelemetry.CATEGORY_RECORDING,
            event = "segment_started",
            message = "Recording segment started",
            attributes = mapOf(
                "segmentIndex" to segmentIndex.toString(),
                "dualCamera" to state.dualCamera.toString(),
                "frontFilePresent" to (files.front != null).toString(),
                "rearFile" to (files.rear?.name ?: ""),
                "frontFile" to (files.front?.name ?: ""),
            ),
        )
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
            append(
                getString(
                    if (finalizedFiles.files.any { it.name.contains("_locked") }) {
                        R.string.recording_service_adjacent_locked
                    } else {
                        R.string.recording_service_segment_done
                    },
                ),
            )
            if (cleanup != null && cleanup.deletedFiles > 0) {
                append(getString(R.string.recording_service_cleanup_old_normal, cleanup.deletedFiles))
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
                state.status = getString(R.string.recording_service_lock_triggered)
                notifyRecordingState()
            }
        }
    }

    override fun onRecordingStopped(files: RecordingSegmentFileSet) {
        VoyageCamRuntimeTelemetry.log(
            level = StructuredLogLevel.Info,
            category = VoyageCamRuntimeTelemetry.CATEGORY_RECORDING,
            event = "recording_stopped",
            message = "Recording session stopped",
            attributes = mapOf(
                "rearFile" to (files.rear?.name ?: ""),
                "frontFile" to (files.front?.name ?: ""),
            ),
        )
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
        state.status = getString(R.string.recording_service_stopped)
        notifyRecordingState()
    }

    override fun onRecordingError(message: String) {
        state.status = message
        VoyageCamRuntimeTelemetry.log(
            level = StructuredLogLevel.Error,
            category = VoyageCamRuntimeTelemetry.CATEGORY_RECORDING,
            event = "recording_error",
            message = message,
            attributes = mapOf(
                "dualCameraActive" to state.dualCamera.toString(),
                "currentSegmentIndex" to state.currentSegmentIndex.toString(),
            ),
        )
        notifyRecordingState()
    }

    override fun onDualCameraFallback(diagnostic: DualCameraDiagnostic) {
        state.dualCamera = false
        state.fallbackSummary = getString(R.string.recording_fallback_dual_session_failed)
        state.dualCameraDiagnostic = dualCameraDiagnosticSummary(diagnostic)
        serviceScope.launch(Dispatchers.IO) {
            dualCameraDiagnosticsStore.record(diagnostic)
        }
        VoyageCamRuntimeTelemetry.archiveDualCameraFailure(
            source = DualCameraFailureSource.RecordingService,
            diagnostic = diagnostic,
            summary = "Dual-camera recording fell back to rear-only",
            attributes = mapOf(
                "segmentIndex" to state.currentSegmentIndex.toString(),
                "currentFile" to (state.currentFileName ?: ""),
            ),
        )
        VoyageCamRuntimeTelemetry.log(
            level = StructuredLogLevel.Warn,
            category = VoyageCamRuntimeTelemetry.CATEGORY_DUAL_CAMERA,
            event = "dual_camera_fallback",
            message = diagnostic.detail,
            attributes = mapOf(
                "stage" to diagnostic.stage.name,
                "segmentIndex" to state.currentSegmentIndex.toString(),
            ),
        )
        state.status = getString(R.string.recording_service_dual_fallback, dualCameraDiagnosticSummary(diagnostic))
        notifyRecordingState()
    }

    private fun requestEmergencyLock(collisionEvent: CollisionDetector.CollisionEvent? = null) {
        val activeRecorder = recorder
        if (activeRecorder == null || state.startedAtMillis <= 0L) {
            state.status = getString(R.string.recording_service_no_active_segment)
            notifyRecordingState()
            return
        }

        state.status = collisionEvent?.let {
            val acceleration = String.format(Locale.getDefault(), "%.1f", it.accelerationG)
            getString(R.string.recording_service_collision_creating, acceleration)
        } ?: getString(R.string.recording_service_creating_event)
        VoyageCamRuntimeTelemetry.log(
            level = StructuredLogLevel.Info,
            category = VoyageCamRuntimeTelemetry.CATEGORY_RECORDING,
            event = "emergency_event_creating",
            message = state.status,
            attributes = mapOf(
                "trigger" to (if (collisionEvent == null) EmergencyTrigger.Manual.name else EmergencyTrigger.Collision.name),
            ),
        )
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
                        getString(R.string.recording_service_collision_locking, acceleration)
                    } ?: getString(R.string.recording_service_locking_event)
                    VoyageCamRuntimeTelemetry.log(
                        level = StructuredLogLevel.Warn,
                        category = VoyageCamRuntimeTelemetry.CATEGORY_RECORDING,
                        event = "emergency_event_locking",
                        message = state.status,
                        attributes = mapOf(
                            "eventId" to event.id,
                            "trigger" to event.trigger.name,
                        ),
                    )
                    notifyRecordingState()
                    activeRecorder.lockCurrentSegment()
                }
                .onFailure { error ->
                    state.pendingLockNextEventId = null
                    state.status = getString(
                        R.string.recording_service_event_failed,
                        error.message ?: getString(R.string.common_unknown_error),
                    )
                    VoyageCamRuntimeTelemetry.log(
                        level = StructuredLogLevel.Error,
                        category = VoyageCamRuntimeTelemetry.CATEGORY_RECORDING,
                        event = "emergency_event_failed",
                        message = state.status,
                        throwable = error,
                    )
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
            state.status = getString(R.string.recording_service_collision_unavailable)
            VoyageCamRuntimeTelemetry.log(
                level = StructuredLogLevel.Warn,
                category = VoyageCamRuntimeTelemetry.CATEGORY_RECORDING,
                event = "collision_detector_unavailable",
                message = state.status,
            )
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
            state.status = getString(R.string.recording_service_gps_on)
        } else {
            mainHandler.removeCallbacks(gpsTrackSampleTask)
            emergencyLocationProvider.stopUpdates()
            gpsTrackBuffer.clear()
            state.status = getString(R.string.recording_service_gps_off)
        }
        VoyageCamRuntimeTelemetry.log(
            level = StructuredLogLevel.Info,
            category = VoyageCamRuntimeTelemetry.CATEGORY_RECORDING,
            event = "gps_metadata_changed",
            message = state.status,
            attributes = mapOf("enabled" to enabled.toString()),
        )
        if (state.startedAtMillis > 0L) {
            notifyRecordingState()
        }
    }

    override fun onSegmentTransitionMeasured(stats: RecordingSegmentTransitionStats) {
        val stopText = stats.stopToFinalizeMillis?.let { getString(R.string.common_ms, it) } ?: getString(R.string.common_unknown)
        state.segmentTransitionSummary = getString(
            R.string.recording_service_transition_summary,
            stats.finalizeToNextStartMillis,
            stopText,
        )
        state.status = getString(
            R.string.recording_service_transition_done,
            stats.completedSegmentIndex,
            state.segmentTransitionSummary,
        )
        evaluatePerformanceGuard(stats)
        notifyRecordingState()
    }

    private fun evaluatePerformanceGuard(transitionStats: RecordingSegmentTransitionStats? = null) {
        if (state.startedAtMillis <= 0L) return
        val settings = settingsStore.load()
        val previousSummary = state.performanceGuardSummary
        val decision = RecordingPerformanceGuard.evaluate(
            sample = performanceSample(transitionStats),
            dualCameraActive = state.dualCamera,
            policy = RecordingPerformancePolicy(
                thermalGuardEnabled = settings.thermalGuardEnabled,
                lowBatteryGuardEnabled = settings.lowBatteryGuardEnabled,
                slowSegmentGuardEnabled = settings.slowSegmentGuardEnabled,
            ),
        )
        val summary = RecordingPerformanceGuard.summary(this, decision)
        state.performanceGuardSummary = summary
        if (decision.shouldDowngradeDualCamera && summary != null) {
            downgradeDualCameraForPerformance(summary)
        } else if (summary != null || previousSummary != summary) {
            notifyRecordingState()
        }
    }

    private fun downgradeDualCameraForPerformance(reason: String) {
        if (!state.dualCamera) return
        state.dualCamera = false
        state.fallbackSummary = getString(R.string.recording_fallback_performance_guard)
        state.dualCameraDiagnostic = getString(R.string.recording_service_guard_prefix, reason)
        state.status = getString(R.string.recording_service_guard_triggered, reason)
        VoyageCamRuntimeTelemetry.archiveDualCameraFailure(
            source = DualCameraFailureSource.PerformanceGuard,
            stage = null,
            summary = "Dual-camera downgraded by performance guard",
            detail = reason,
            attributes = mapOf(
                "segmentIndex" to state.currentSegmentIndex.toString(),
                "storageCapacityGb" to state.storageCapacityGb.toString(),
            ),
        )
        recorder?.downgradeToRearOnly(state.status)
        notifyRecordingState()
    }

    private fun performanceSample(transitionStats: RecordingSegmentTransitionStats?): RecordingPerformanceSample {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt()
        } else {
            null
        }
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = plugged != 0 ||
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        return RecordingPerformanceSample(
            batteryPercent = batteryPercent,
            charging = charging,
            thermalSeverity = currentThermalSeverity(),
            transitionStats = transitionStats,
        )
    }

    private fun currentThermalSeverity(): ThermalSeverity {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalSeverity.None
        val status = getSystemService(PowerManager::class.java)?.currentThermalStatus
            ?: return ThermalSeverity.None
        return when {
            status >= PowerManager.THERMAL_STATUS_CRITICAL -> ThermalSeverity.Critical
            status >= PowerManager.THERMAL_STATUS_SEVERE -> ThermalSeverity.Severe
            status >= PowerManager.THERMAL_STATUS_MODERATE -> ThermalSeverity.Moderate
            status >= PowerManager.THERMAL_STATUS_LIGHT -> ThermalSeverity.Light
            else -> ThermalSeverity.None
        }
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
        private const val PERFORMANCE_GUARD_INTERVAL_MS = 30_000L
        private const val GPS_TRACK_RETENTION_MS = 5 * 60 * 1000L
        private const val MAX_GPS_TRACK_POINTS = 60
        const val ACTION_STOP = "com.voyagecam.app.action.STOP_RECORDING"
        const val ACTION_LOCK_CURRENT = "com.voyagecam.app.action.LOCK_CURRENT"
        private const val ACTION_SET_GPS_METADATA = "com.voyagecam.app.action.SET_GPS_METADATA"
        private const val ACTION_REFRESH_PERFORMANCE_GUARD = "com.voyagecam.app.action.REFRESH_PERFORMANCE_GUARD"
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

        fun refreshPerformanceGuard(context: Context) {
            val intent = Intent(context, RecordingForegroundService::class.java)
                .setAction(ACTION_REFRESH_PERFORMANCE_GUARD)
            context.startService(intent)
        }
    }
}
