package com.voyagecam.app.core.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat

class RearCameraPreviewController(private val context: Context) {
    private val cameraThread = HandlerThread("VoyageCamRearPreview").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var openingOrOpened = false

    fun start(textureView: TextureView, onError: (String) -> Unit) {
        if (openingOrOpened) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onError("相机权限未授权，无法显示预览")
            return
        }

        if (textureView.isAvailable) {
            openCamera(textureView.surfaceTexture, textureView.width, textureView.height, onError)
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    openCamera(surface, width, height, onError)
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    stop()
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            }
        }
    }

    fun stop() {
        cameraHandler.post {
            openingOrOpened = false
            runCatching {
                captureSession?.stopRepeating()
                captureSession?.abortCaptures()
            }
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            previewSurface?.release()
            previewSurface = null
        }
    }

    fun destroy() {
        stop()
        cameraThread.quitSafely()
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(
        surfaceTexture: SurfaceTexture?,
        width: Int,
        height: Int,
        onError: (String) -> Unit,
    ) {
        val texture = surfaceTexture ?: return
        cameraHandler.post {
            try {
                if (openingOrOpened) return@post
                openingOrOpened = true
                val cameraManager = context.getSystemService(CameraManager::class.java)
                val rearCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                } ?: run {
                    openingOrOpened = false
                    onError("未检测到后置摄像头")
                    return@post
                }

                val previewSize = cameraManager.getCameraCharacteristics(rearCameraId).selectPreviewSize(width, height)
                texture.setDefaultBufferSize(previewSize.width, previewSize.height)
                val surface = Surface(texture)
                previewSurface = surface

                cameraManager.openCamera(
                    rearCameraId,
                    object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            cameraDevice = camera
                            createPreviewSession(camera, surface, onError)
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            openingOrOpened = false
                            onError("后置摄像头预览已断开")
                            stop()
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            openingOrOpened = false
                            onError("后置摄像头预览失败：$error")
                            stop()
                        }
                    },
                    cameraHandler,
                )
            } catch (error: Throwable) {
                openingOrOpened = false
                onError(error.message ?: "后置摄像头预览启动失败")
                stop()
            }
        }
    }

    private fun createPreviewSession(
        camera: CameraDevice,
        surface: Surface,
        onError: (String) -> Unit,
    ) {
        camera.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(surface)
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    }
                    session.setRepeatingRequest(request.build(), null, cameraHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    openingOrOpened = false
                    onError("后置摄像头预览会话创建失败")
                    stop()
                }
            },
            cameraHandler,
        )
    }

    private fun CameraCharacteristics.selectPreviewSize(width: Int, height: Int): android.util.Size {
        val targetPixels = (width.coerceAtLeast(1) * height.coerceAtLeast(1)).coerceAtLeast(1280 * 720)
        val sizes = get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(SurfaceTexture::class.java)
            .orEmpty()
        val preferred = sizes
            .filter { it.width <= 1920 && it.height <= 1080 }
            .minByOrNull { kotlin.math.abs(it.width * it.height - targetPixels) }
        val fallback = sizes.minByOrNull { kotlin.math.abs(it.width * it.height - targetPixels) }

        return preferred ?: fallback ?: android.util.Size(1280, 720)
    }
}
