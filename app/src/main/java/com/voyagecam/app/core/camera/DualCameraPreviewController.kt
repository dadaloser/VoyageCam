package com.voyagecam.app.core.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class DualCameraPreviewController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var concurrentCamera: ConcurrentCamera? = null

    fun start(
        rearPreviewView: PreviewView,
        frontPreviewView: PreviewView,
        onError: (String) -> Unit,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onError("相机权限未授权，无法显示双摄预览")
            return
        }

        runCatching {
            val provider = cameraProvider ?: ProcessCameraProvider.getInstance(context).get().also {
                cameraProvider = it
            }
            provider.unbindAll()

            val rearPreview = Preview.Builder().build().apply {
                setSurfaceProvider(rearPreviewView.surfaceProvider)
            }
            val frontPreview = Preview.Builder().build().apply {
                setSurfaceProvider(frontPreviewView.surfaceProvider)
            }

            val rearConfig = ConcurrentCamera.SingleCameraConfig(
                CameraSelector.DEFAULT_BACK_CAMERA,
                UseCaseGroup.Builder().addUseCase(rearPreview).build(),
                lifecycleOwner,
            )
            val frontConfig = ConcurrentCamera.SingleCameraConfig(
                CameraSelector.DEFAULT_FRONT_CAMERA,
                UseCaseGroup.Builder().addUseCase(frontPreview).build(),
                lifecycleOwner,
            )
            concurrentCamera = provider.bindToLifecycle(listOf(rearConfig, frontConfig))
        }.onFailure { error ->
            concurrentCamera = null
            onError(error.message ?: "双摄预览初始化失败")
        }
    }

    fun stop() {
        runCatching {
            cameraProvider?.unbindAll()
        }
        concurrentCamera = null
    }

    fun destroy() {
        stop()
    }
}
