package com.voyagecam.app.core.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
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
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.data.settings.RecordingOrientationStrategy
import com.voyagecam.app.data.settings.RecordingVideoProfile
import java.io.File

object RearCameraCameraXPipeline : LifecycleOwner {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lifecycleRegistry = LifecycleRegistry(this)
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null
    private var activeRecording = false
    private var currentVideoProfile: RecordingVideoProfile? = null
    private var currentCameraDirection: CameraDirection? = null
    private var currentFrontMirrorEnabled: Boolean = false
    private var currentOrientationStrategy: RecordingOrientationStrategy? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    fun setPreviewSurfaceProvider(
        context: Context,
        cameraDirection: CameraDirection,
        frontMirrorEnabled: Boolean,
        orientationStrategy: RecordingOrientationStrategy,
        provider: Preview.SurfaceProvider,
        onError: (String) -> Unit,
    ) {
        runOnMain {
            previewSurfaceProvider = provider
            ensureLifecycleStarted()
            bindUseCases(
                context = context.applicationContext,
                cameraDirection = cameraDirection,
                frontMirrorEnabled = frontMirrorEnabled,
                orientationStrategy = orientationStrategy,
                videoProfile = currentVideoProfile ?: DEFAULT_VIDEO_PROFILE,
                onError = onError,
            )
        }
    }

    fun clearPreviewSurfaceProvider(provider: Preview.SurfaceProvider) {
        runOnMain {
            if (previewSurfaceProvider === provider) {
                previewSurfaceProvider = null
                preview?.setSurfaceProvider(null)
                unbindIfIdle()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(
        context: Context,
        file: File,
        cameraDirection: CameraDirection,
        frontMirrorEnabled: Boolean,
        orientationStrategy: RecordingOrientationStrategy,
        audioEnabled: Boolean,
        videoProfile: RecordingVideoProfile,
        onReady: (Recording) -> Unit,
        onEvent: (VideoRecordEvent) -> Unit,
        onError: (String) -> Unit,
    ) {
        runOnMain {
            ensureLifecycleStarted()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                onError(context.getString(R.string.camera_error_permission_recording))
                return@runOnMain
            }

            val boundVideoCapture = bindUseCases(
                context = context.applicationContext,
                cameraDirection = cameraDirection,
                frontMirrorEnabled = frontMirrorEnabled,
                orientationStrategy = orientationStrategy,
                videoProfile = videoProfile,
                onError = onError,
            ) ?: return@runOnMain
            val outputOptions = FileOutputOptions.Builder(file).build()
            val pendingRecording = boundVideoCapture.output
                .prepareRecording(context.applicationContext, outputOptions)
                .let { recording ->
                    if (
                        audioEnabled &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        recording.withAudioEnabled()
                    } else {
                        recording
                    }
                }

            val recording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                onEvent(event)
                if (event is VideoRecordEvent.Finalize) {
                    activeRecording = false
                    mainHandler.postDelayed({ unbindIfIdle() }, IDLE_UNBIND_DELAY_MILLIS)
                }
            }
            activeRecording = true
            onReady(recording)
        }
    }

    private fun bindUseCases(
        context: Context,
        cameraDirection: CameraDirection,
        frontMirrorEnabled: Boolean,
        orientationStrategy: RecordingOrientationStrategy,
        videoProfile: RecordingVideoProfile,
        onError: (String) -> Unit,
    ): VideoCapture<Recorder>? {
        return runCatching {
            val provider = cameraProvider ?: ProcessCameraProvider.getInstance(context).get().also {
                cameraProvider = it
            }
            if (
                videoCapture == null ||
                preview == null ||
                currentVideoProfile != videoProfile ||
                currentCameraDirection != cameraDirection ||
                currentFrontMirrorEnabled != frontMirrorEnabled ||
                currentOrientationStrategy != orientationStrategy
            ) {
                val targetRotation = orientationStrategy.targetRotation(context)
                val nextPreview = Preview.Builder()
                    .setTargetRotation(targetRotation)
                    .build().apply {
                    previewSurfaceProvider?.let(::setSurfaceProvider)
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(videoProfile.qualitySelector())
                    .setTargetVideoEncodingBitRate(videoProfile.bitrate.bitsPerSecond)
                    .build()
                val nextVideoCapture = VideoCapture.Builder(recorder)
                    .setTargetFrameRate(videoProfile.targetFrameRateRange())
                    .setTargetRotation(targetRotation)
                    .setMirrorMode(
                        if (cameraDirection == CameraDirection.Front && frontMirrorEnabled) {
                            MirrorMode.MIRROR_MODE_ON
                        } else {
                            MirrorMode.MIRROR_MODE_OFF
                        },
                    )
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    cameraDirection.toCameraSelector(),
                    nextPreview,
                    nextVideoCapture,
                )
                preview = nextPreview
                videoCapture = nextVideoCapture
                currentVideoProfile = videoProfile
                currentCameraDirection = cameraDirection
                currentFrontMirrorEnabled = frontMirrorEnabled
                currentOrientationStrategy = orientationStrategy
            } else {
                previewSurfaceProvider?.let { surfaceProvider ->
                    preview?.setSurfaceProvider(surfaceProvider)
                }
            }

            videoCapture
        }.getOrElse { error ->
            onError(error.message ?: context.getString(R.string.camera_error_rear_pipeline_failed))
            null
        }
    }

    private fun ensureLifecycleStarted() {
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
        if (!lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }
    }

    private fun unbindIfIdle() {
        if (activeRecording || previewSurfaceProvider != null) return
        cameraProvider?.unbindAll()
        preview = null
        videoCapture = null
        currentVideoProfile = null
        currentCameraDirection = null
        currentFrontMirrorEnabled = false
        currentOrientationStrategy = null
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private const val IDLE_UNBIND_DELAY_MILLIS = 1_000L
}

private fun CameraDirection.toCameraSelector(): CameraSelector {
    return when (this) {
        CameraDirection.Rear -> CameraSelector.DEFAULT_BACK_CAMERA
        CameraDirection.Front -> CameraSelector.DEFAULT_FRONT_CAMERA
    }
}

private fun RecordingOrientationStrategy.targetRotation(context: Context): Int {
    return when (this) {
        RecordingOrientationStrategy.FollowSystem -> context.display?.rotation ?: Surface.ROTATION_0
        RecordingOrientationStrategy.FixedLandscapeDriving -> Surface.ROTATION_90
    }
}

private val DEFAULT_VIDEO_PROFILE = RecordingVideoProfile(
    resolution = com.voyagecam.app.data.settings.RecordingResolutionPreset.FHD_1080P,
    frameRate = com.voyagecam.app.data.settings.RecordingFrameRatePreset.FPS_30,
    bitrate = com.voyagecam.app.data.settings.RecordingBitratePreset.MBPS_12,
)
