package com.voyagecam.app.core.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.File

object DualCameraSessionCoordinator : LifecycleOwner {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lifecycleRegistry = LifecycleRegistry(this)
    private var cameraProvider: ProcessCameraProvider? = null
    private var concurrentCamera: ConcurrentCamera? = null
    private var rearPreviewProvider: Preview.SurfaceProvider? = null
    private var frontPreviewProvider: Preview.SurfaceProvider? = null
    private var rearRecording: Recording? = null
    private var frontRecording: Recording? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    fun setPreviewSurfaceProviders(
        context: Context,
        rearProvider: Preview.SurfaceProvider,
        frontProvider: Preview.SurfaceProvider,
        onError: (String) -> Unit,
    ) {
        runOnMain {
            rearPreviewProvider = rearProvider
            frontPreviewProvider = frontProvider
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
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(
        context: Context,
        rearFile: File,
        frontFile: File,
        audioEnabled: Boolean,
        onReady: (DualCameraRecordingSession) -> Unit,
        onEvent: (DualCameraRecordEvent) -> Unit,
        onError: (String) -> Unit,
    ) {
        runOnMain {
            if (!hasCameraPermission(context)) {
                onError("相机权限未授权，无法启动双摄录制")
                return@runOnMain
            }

            runCatching {
                val rearVideoCapture = VideoCapture.withOutput(recorder(Quality.HD))
                val frontVideoCapture = VideoCapture.withOutput(recorder(Quality.HD))
                bind(
                    context = context.applicationContext,
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
                onReady(
                    DualCameraRecordingSession(
                        rearRecording = nextRearRecording,
                        frontRecording = nextFrontRecording,
                        onStopped = {
                            rearRecording = null
                            frontRecording = null
                            rebindPreviewIfNeeded(context.applicationContext, onError)
                        },
                    ),
                )
            }.onFailure { error ->
                rearRecording = null
                frontRecording = null
                unbindIfIdle()
                onError(error.message ?: "双摄录制初始化失败")
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
        }
    }

    private fun bind(
        context: Context,
        rearVideoCapture: VideoCapture<Recorder>?,
        frontVideoCapture: VideoCapture<Recorder>?,
        onError: (String) -> Unit,
    ) {
        if (!hasCameraPermission(context)) {
            onError("相机权限未授权，无法显示双摄画面")
            return
        }
        ensureLifecycleStarted()
        runCatching {
            val provider = cameraProvider ?: ProcessCameraProvider.getInstance(context).get().also {
                cameraProvider = it
            }
            provider.unbindAll()

            val rearPreview = Preview.Builder().build().apply {
                rearPreviewProvider?.let(::setSurfaceProvider)
            }
            val frontPreview = Preview.Builder().build().apply {
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
        }.onFailure { error ->
            concurrentCamera = null
            onError(error.message ?: "双摄会话初始化失败")
        }
    }

    private fun unbindIfIdle() {
        if (rearPreviewProvider != null || frontPreviewProvider != null || rearRecording != null || frontRecording != null) return
        runCatching { cameraProvider?.unbindAll() }
        concurrentCamera = null
    }

    private fun rebindPreviewIfNeeded(context: Context, onError: (String) -> Unit) {
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

    private fun recorder(preferredQuality: Quality): Recorder {
        return Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    listOf(preferredQuality, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                ),
            )
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
}
