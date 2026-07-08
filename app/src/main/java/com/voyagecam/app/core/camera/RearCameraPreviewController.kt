package com.voyagecam.app.core.camera

import android.content.Context
import androidx.camera.view.PreviewView

class RearCameraPreviewController(private val context: Context) {
    private var surfaceProvider: androidx.camera.core.Preview.SurfaceProvider? = null

    fun start(previewView: PreviewView, onError: (String) -> Unit) {
        val provider = previewView.surfaceProvider
        surfaceProvider = provider
        RearCameraCameraXPipeline.setPreviewSurfaceProvider(
            context = context,
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
