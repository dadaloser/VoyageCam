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

object DualCameraRecordingPipeline : LifecycleOwner {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lifecycleRegistry = LifecycleRegistry(this)
    private var cameraProvider: ProcessCameraProvider? = null
    private var concurrentCamera: ConcurrentCamera? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

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
            ensureLifecycleStarted()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                onError("相机权限未授权，无法启动双摄录制")
                return@runOnMain
            }

            runCatching {
                val provider = cameraProvider ?: ProcessCameraProvider.getInstance(context.applicationContext).get().also {
                    cameraProvider = it
                }
                provider.unbindAll()

                val rearVideoCapture = VideoCapture.withOutput(recorder(Quality.HD))
                val frontVideoCapture = VideoCapture.withOutput(recorder(Quality.HD))
                val rearConfig = ConcurrentCamera.SingleCameraConfig(
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    UseCaseGroup.Builder()
                        .addUseCase(Preview.Builder().build())
                        .addUseCase(rearVideoCapture)
                        .build(),
                    this,
                )
                val frontConfig = ConcurrentCamera.SingleCameraConfig(
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    UseCaseGroup.Builder()
                        .addUseCase(Preview.Builder().build())
                        .addUseCase(frontVideoCapture)
                        .build(),
                    this,
                )
                concurrentCamera = provider.bindToLifecycle(listOf(rearConfig, frontConfig))

                val rearRecording = rearVideoCapture.output
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
                val frontRecording = frontVideoCapture.output
                    .prepareRecording(context.applicationContext, FileOutputOptions.Builder(frontFile).build())
                    .start(ContextCompat.getMainExecutor(context)) { event ->
                        onEvent(DualCameraRecordEvent(camera = CameraSelector.LENS_FACING_FRONT, event = event))
                    }

                onReady(
                    DualCameraRecordingSession(
                        rearRecording = rearRecording,
                        frontRecording = frontRecording,
                    ),
                )
            }.onFailure { error ->
                stop()
                onError(error.message ?: "双摄录制初始化失败")
            }
        }
    }

    fun stop() {
        runOnMain {
            runCatching { cameraProvider?.unbindAll() }
            concurrentCamera = null
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

data class DualCameraRecordingSession(
    val rearRecording: Recording,
    val frontRecording: Recording,
) {
    fun stop() {
        runCatching { rearRecording.stop() }
        runCatching { frontRecording.stop() }
    }
}

data class DualCameraRecordEvent(
    val camera: Int,
    val event: VideoRecordEvent,
)
