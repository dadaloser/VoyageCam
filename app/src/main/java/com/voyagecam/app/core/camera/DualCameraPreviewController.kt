package com.voyagecam.app.core.camera

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner

class DualCameraPreviewController(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") private val lifecycleOwner: LifecycleOwner,
) {
    private var rearSurfaceProvider: androidx.camera.core.Preview.SurfaceProvider? = null
    private var frontSurfaceProvider: androidx.camera.core.Preview.SurfaceProvider? = null

    fun start(
        rearPreviewView: PreviewView,
        frontPreviewView: PreviewView,
        onError: (String) -> Unit,
    ) {
        val rearProvider = rearPreviewView.surfaceProvider
        val frontProvider = frontPreviewView.surfaceProvider
        rearSurfaceProvider = rearProvider
        frontSurfaceProvider = frontProvider
        DualCameraSessionCoordinator.setPreviewSurfaceProviders(
            context = context,
            rearProvider = rearProvider,
            frontProvider = frontProvider,
            onError = onError,
        )
    }

    fun stop() {
        val rear = rearSurfaceProvider
        val front = frontSurfaceProvider
        if (rear != null && front != null) {
            DualCameraSessionCoordinator.clearPreviewSurfaceProviders(rear, front)
        }
        rearSurfaceProvider = null
        frontSurfaceProvider = null
    }

    fun destroy() {
        stop()
    }
}
