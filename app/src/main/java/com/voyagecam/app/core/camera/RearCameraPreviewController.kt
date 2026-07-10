package com.voyagecam.app.core.camera

import android.content.Context
import androidx.camera.view.PreviewView
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.data.settings.RecordingOrientationStrategy

class RearCameraPreviewController(private val context: Context) {
    private var surfaceProvider: androidx.camera.core.Preview.SurfaceProvider? = null

    fun start(
        previewView: PreviewView,
        cameraDirection: CameraDirection,
        frontMirrorEnabled: Boolean,
        orientationStrategy: RecordingOrientationStrategy,
        onError: (String) -> Unit,
    ) {
        val provider = previewView.surfaceProvider
        surfaceProvider = provider
        RearCameraCameraXPipeline.setPreviewSurfaceProvider(
            context = context,
            cameraDirection = cameraDirection,
            frontMirrorEnabled = frontMirrorEnabled,
            orientationStrategy = orientationStrategy,
            provider = provider,
            onError = onError,
        )
    }

    fun stop() {
        surfaceProvider?.let(RearCameraCameraXPipeline::clearPreviewSurfaceProvider)
        surfaceProvider = null
    }

    fun destroy() {
        stop()
    }
}
