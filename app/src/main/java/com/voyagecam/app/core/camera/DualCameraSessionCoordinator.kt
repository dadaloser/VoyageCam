package com.voyagecam.app.core.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.voyagecam.app.R
import com.voyagecam.app.core.model.DualCameraDiagnostic
import com.voyagecam.app.core.model.DualCameraFailureSource
import com.voyagecam.app.core.model.DualCameraDiagnosticStage
import com.voyagecam.app.data.settings.RecordingOrientationStrategy
import com.voyagecam.app.data.settings.RecordingVideoProfile
import com.voyagecam.app.data.telemetry.VoyageCamRuntimeTelemetry
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DualCameraSessionCoordinator : LifecycleOwner {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lifecycleRegistry = LifecycleRegistry(this)
    private var cameraProvider: ProcessCameraProvider? = null
    private var concurrentCamera: ConcurrentCamera? = null
    private var rearPreviewProvider: Preview.SurfaceProvider? = null
    private var frontPreviewProvider: Preview.SurfaceProvider? = null
    private var rearRecording: Recording? = null
    private var frontRecording: Recording? = null
    private val _sessionStatus = MutableStateFlow(DualCameraSessionStatus())

    val sessionStatus: StateFlow<DualCameraSessionStatus> = _sessionStatus

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    fun setPreviewSurfaceProviders(
        context: Context,
        sessionToken: Int,
        rearProvider: Preview.SurfaceProvider,
        frontProvider: Preview.SurfaceProvider,
        onError: (DualCameraDiagnostic) -> Unit,
    ) {
        runOnMain {
            rearPreviewProvider = rearProvider
            frontPreviewProvider = frontProvider
            publishSessionStatus(
                previewSessionToken = sessionToken,
                lastDiagnostic = null,
            )
            bind(
                context = context.applicationContext,
                rearVideoCapture = null,
                frontVideoCapture = null,
                onError = onError,
            )
        }
    }

    fun clearPreviewSurfaceProviders(
        rearProvider: Preview.SurfaceProvider,
        frontProvider: Preview.SurfaceProvider,
    ) {
        runOnMain {
            if (rearPreviewProvider === rearProvider) rearPreviewProvider = null
            if (frontPreviewProvider === frontProvider) frontPreviewProvider = null
            if (rearPreviewProvider == null && frontPreviewProvider == null && rearRecording == null && frontRecording == null) {
                stop()
            } else {
                publishSessionStatus()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(
        context: Context,
        rearFile: File,
        frontFile: File,
        audioEnabled: Boolean,
        frontMirrorEnabled: Boolean,
        orientationStrategy: RecordingOrientationStrategy,
        videoProfile: RecordingVideoProfile,
        onReady: (DualCameraRecordingSession) -> Unit,
        onEvent: (DualCameraRecordEvent) -> Unit,
        onError: (DualCameraDiagnostic) -> Unit,
    ) {
        runOnMain {
            if (!hasCameraPermission(context)) {
                reportDiagnostic(
                    diagnostic = DualCameraDiagnostic(
                        stage = DualCameraDiagnosticStage.ConcurrentRecording,
                        detail = context.getString(R.string.camera_error_dual_permission_recording),
                    ),
                    onError = onError,
                )
                return@runOnMain
            }

            runCatching {
                val targetRotation = orientationStrategy.targetRotation(context)
                val rearVideoCapture = videoCapture(
                    videoProfile = videoProfile,
                    targetRotation = targetRotation,
                    mirrorMode = MirrorMode.MIRROR_MODE_OFF,
                )
                val frontVideoCapture = videoCapture(
                    videoProfile = videoProfile,
                    targetRotation = targetRotation,
                    mirrorMode = if (frontMirrorEnabled) {
                        MirrorMode.MIRROR_MODE_ON
                    } else {
                        MirrorMode.MIRROR_MODE_OFF
                    },
                )
                bind(
                    context = context.applicationContext,
                    targetRotation = targetRotation,
                    rearVideoCapture = rearVideoCapture,
                    frontVideoCapture = frontVideoCapture,
                    onError = onError,
                )

                val nextRearRecording = rearVideoCapture.output
                    .prepareRecording(context.applicationContext, FileOutputOptions.Builder(rearFile).build())
                    .let { pending ->
                        if (audioEnabled && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            pending.withAudioEnabled()
                        } else {
                            pending
                        }
                    }
                    .start(ContextCompat.getMainExecutor(context)) { event ->
                        onEvent(DualCameraRecordEvent(camera = CameraSelector.LENS_FACING_BACK, event = event))
                    }
                val nextFrontRecording = frontVideoCapture.output
                    .prepareRecording(context.applicationContext, FileOutputOptions.Builder(frontFile).build())
                    .start(ContextCompat.getMainExecutor(context)) { event ->
                        onEvent(DualCameraRecordEvent(camera = CameraSelector.LENS_FACING_FRONT, event = event))
                    }

                rearRecording = nextRearRecording
                frontRecording = nextFrontRecording
                publishSessionStatus(lastDiagnostic = null)
                onReady(
                    DualCameraRecordingSession(
                        rearRecording = nextRearRecording,
                        frontRecording = nextFrontRecording,
                        onStopped = {
                            rearRecording = null
                            frontRecording = null
                            publishSessionStatus()
                            rebindPreviewIfNeeded(context.applicationContext, onError)
                        },
                    ),
                )
            }.onFailure { error ->
                rearRecording = null
                frontRecording = null
                unbindIfIdle()
                reportDiagnostic(
                    diagnostic = DualCameraDiagnostic(
                        stage = DualCameraDiagnosticStage.ConcurrentRecording,
                        detail = error.message ?: context.getString(R.string.camera_error_dual_init_failed),
                    ),
                    onError = onError,
                )
            }
        }
    }

    fun stop() {
        runOnMain {
            runCatching { rearRecording?.stop() }
            runCatching { frontRecording?.stop() }
            rearRecording = null
            frontRecording = null
            runCatching { cameraProvider?.unbindAll() }
            concurrentCamera = null
            publishSessionStatus()
        }
    }

    private fun bind(
        context: Context,
        targetRotation: Int = orientationTargetRotation(context),
        rearVideoCapture: VideoCapture<Recorder>?,
        frontVideoCapture: VideoCapture<Recorder>?,
        onError: (DualCameraDiagnostic) -> Unit,
    ) {
        if (!hasCameraPermission(context)) {
            reportDiagnostic(
                diagnostic = DualCameraDiagnostic(
                    stage = if (rearVideoCapture == null && frontVideoCapture == null) {
                        DualCameraDiagnosticStage.Preview
                    } else {
                        DualCameraDiagnosticStage.Session
                    },
                    detail = context.getString(R.string.camera_error_dual_permission_preview),
                ),
                onError = onError,
            )
            return
        }
        ensureLifecycleStarted()
        runCatching {
            val provider = cameraProvider ?: ProcessCameraProvider.getInstance(context).get().also {
                cameraProvider = it
            }
            provider.unbindAll()

            val rearPreview = Preview.Builder().setTargetRotation(targetRotation).build().apply {
                rearPreviewProvider?.let(::setSurfaceProvider)
            }
            val frontPreview = Preview.Builder().setTargetRotation(targetRotation).build().apply {
                frontPreviewProvider?.let(::setSurfaceProvider)
            }

            val rearUseCases = buildList<UseCase> {
                add(rearPreview)
                rearVideoCapture?.let(::add)
            }
            val frontUseCases = buildList<UseCase> {
                add(frontPreview)
                frontVideoCapture?.let(::add)
            }

            val rearConfig = ConcurrentCamera.SingleCameraConfig(
                CameraSelector.DEFAULT_BACK_CAMERA,
                UseCaseGroup.Builder().apply { rearUseCases.forEach(::addUseCase) }.build(),
                this,
            )
            val frontConfig = ConcurrentCamera.SingleCameraConfig(
                CameraSelector.DEFAULT_FRONT_CAMERA,
                UseCaseGroup.Builder().apply { frontUseCases.forEach(::addUseCase) }.build(),
                this,
            )
            concurrentCamera = provider.bindToLifecycle(listOf(rearConfig, frontConfig))
            publishSessionStatus(lastDiagnostic = null)
        }.onFailure { error ->
            concurrentCamera = null
            reportDiagnostic(
                diagnostic = DualCameraDiagnostic(
                    stage = if (rearVideoCapture == null && frontVideoCapture == null) {
                        DualCameraDiagnosticStage.Preview
                    } else {
                        DualCameraDiagnosticStage.Session
                    },
                    detail = error.message ?: context.getString(R.string.camera_error_dual_session_failed),
                ),
                onError = onError,
            )
        }
    }

    private fun unbindIfIdle() {
        if (rearPreviewProvider != null || frontPreviewProvider != null || rearRecording != null || frontRecording != null) return
        runCatching { cameraProvider?.unbindAll() }
        concurrentCamera = null
        publishSessionStatus()
    }

    private fun rebindPreviewIfNeeded(context: Context, onError: (DualCameraDiagnostic) -> Unit) {
        if (rearPreviewProvider != null || frontPreviewProvider != null) {
            bind(
                context = context,
                rearVideoCapture = null,
                frontVideoCapture = null,
                onError = onError,
            )
        } else {
            unbindIfIdle()
        }
    }

    private fun videoCapture(
        videoProfile: RecordingVideoProfile,
        targetRotation: Int,
        mirrorMode: Int,
    ): VideoCapture<Recorder> {
        val recorder = Recorder.Builder()
            .setQualitySelector(videoProfile.qualitySelector())
            .setTargetVideoEncodingBitRate(videoProfile.bitrate.bitsPerSecond)
            .build()
        return VideoCapture.Builder(recorder)
            .setTargetFrameRate(videoProfile.targetFrameRateRange())
            .setTargetRotation(targetRotation)
            .setMirrorMode(mirrorMode)
            .build()
    }

    private fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureLifecycleStarted() {
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
        if (!lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun publishSessionStatus(
        previewSessionToken: Int? = _sessionStatus.value.previewSessionToken,
        lastDiagnostic: DualCameraDiagnostic? = _sessionStatus.value.lastDiagnostic,
    ) {
        _sessionStatus.value = DualCameraSessionStatus(
            previewSessionToken = previewSessionToken,
            concurrentCameraActive = concurrentCamera != null,
            recordingActive = rearRecording != null || frontRecording != null,
            rearPreviewAttached = rearPreviewProvider != null,
            frontPreviewAttached = frontPreviewProvider != null,
            lastDiagnostic = lastDiagnostic,
        )
    }

    private fun reportDiagnostic(
        diagnostic: DualCameraDiagnostic,
        onError: (DualCameraDiagnostic) -> Unit,
    ) {
        publishSessionStatus(lastDiagnostic = diagnostic)
        val sessionStatus = _sessionStatus.value
        VoyageCamRuntimeTelemetry.archiveDualCameraFailure(
            source = DualCameraFailureSource.SessionCoordinator,
            diagnostic = diagnostic,
            summary = "Dual-camera session diagnostic",
            attributes = mapOf(
                "previewSessionToken" to sessionStatus.previewSessionToken.toString(),
                "concurrentCameraActive" to sessionStatus.concurrentCameraActive.toString(),
                "recordingActive" to sessionStatus.recordingActive.toString(),
                "rearPreviewAttached" to sessionStatus.rearPreviewAttached.toString(),
                "frontPreviewAttached" to sessionStatus.frontPreviewAttached.toString(),
            ),
        )
        onError(diagnostic)
    }
}

private fun RecordingOrientationStrategy.targetRotation(context: Context): Int {
    return when (this) {
        RecordingOrientationStrategy.FollowSystem -> orientationTargetRotation(context)
        RecordingOrientationStrategy.FixedLandscapeDriving -> Surface.ROTATION_90
    }
}

private fun orientationTargetRotation(context: Context): Int {
    return context.display?.rotation ?: Surface.ROTATION_0
}
