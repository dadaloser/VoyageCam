package com.voyagecam.app.core.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import com.voyagecam.app.data.storage.RecordingStorageManager
import java.io.File

class RearCameraRecorder(
    private val context: Context,
    private val cameraHandler: Handler,
    private val storageManager: RecordingStorageManager,
    private val callbacks: Callbacks,
) {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var recordingStarted = false
    private var shouldContinueRecording = false
    private var audioEnabled = false
    private var segmentDurationMillis = DEFAULT_SEGMENT_DURATION_MINUTES * 60_000L
    private var segmentIndex = 0

    private val rotateSegmentTask = Runnable {
        rotateSegment()
    }

    fun start(ambientAudioRequested: Boolean, segmentDurationMinutes: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            callbacks.onRecordingError("相机权限未授权，无法启动录制")
            return
        }

        audioEnabled = ambientAudioRequested &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        segmentDurationMillis = segmentDurationMinutes
            .coerceIn(MIN_SEGMENT_DURATION_MINUTES, MAX_SEGMENT_DURATION_MINUTES) * 60_000L
        shouldContinueRecording = true
        segmentIndex = 0

        cameraHandler.post {
            try {
                openRearCamera(audioEnabled)
            } catch (error: Throwable) {
                callbacks.onRecordingError(error.message ?: "后摄录制启动失败")
                release(notifyStopped = true)
            }
        }
    }

    fun stop() {
        cameraHandler.post {
            shouldContinueRecording = false
            cameraHandler.removeCallbacks(rotateSegmentTask)
            release(notifyStopped = true)
        }
    }

    fun lockCurrentSegment() {
        cameraHandler.post {
            if (!recordingStarted) {
                callbacks.onRecordingError("当前没有可锁定的录制片段")
                return@post
            }

            val lockedFile = outputFile
            release(notifyStopped = false)
            callbacks.onSegmentLockRequested(lockedFile)
            if (!shouldContinueRecording) return@post

            try {
                openRearCamera(audioEnabled)
            } catch (error: Throwable) {
                callbacks.onRecordingError(error.message ?: "锁定后切换到下一录制片段失败")
                shouldContinueRecording = false
                release(notifyStopped = true)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openRearCamera(audioEnabled: Boolean) {
        val cameraManager = context.getSystemService(CameraManager::class.java)
        val rearCameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR, "未检测到后置摄像头")

        val characteristics = cameraManager.getCameraCharacteristics(rearCameraId)
        val videoSize = characteristics.selectVideoSize()
        val recorder = buildMediaRecorder(characteristics, videoSize, audioEnabled)
        mediaRecorder = recorder

        cameraManager.openCamera(
            rearCameraId,
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createRecordingSession(camera, recorder.surface)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    callbacks.onRecordingError("后置摄像头连接已断开")
                    release(notifyStopped = true)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    callbacks.onRecordingError("后置摄像头打开失败：$error")
                    release(notifyStopped = true)
                }
            },
            cameraHandler,
        )
    }

    private fun createRecordingSession(camera: CameraDevice, recorderSurface: Surface) {
        camera.createCaptureSession(
            listOf(recorderSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        addTarget(recorderSurface)
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    }

                    session.setRepeatingRequest(request.build(), null, cameraHandler)
                    mediaRecorder?.start()
                    recordingStarted = true
                    segmentIndex++
                    callbacks.onRecordingStarted(outputFile, segmentIndex)
                    cameraHandler.removeCallbacks(rotateSegmentTask)
                    cameraHandler.postDelayed(rotateSegmentTask, segmentDurationMillis)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    callbacks.onRecordingError("后摄录制会话创建失败")
                    release(notifyStopped = true)
                }
            },
            cameraHandler,
        )
    }

    private fun buildMediaRecorder(
        characteristics: CameraCharacteristics,
        videoSize: Size,
        audioEnabled: Boolean,
    ): MediaRecorder {
        val file = storageManager.createNormalSegmentFile(System.currentTimeMillis(), CAMERA_DIRECTION_REAR)
        outputFile = file

        return createMediaRecorder().apply {
            if (audioEnabled) {
                setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(file.absolutePath)
            setVideoEncodingBitRate(DEFAULT_VIDEO_BITRATE)
            setVideoFrameRate(DEFAULT_FRAME_RATE)
            setVideoSize(videoSize.width, videoSize.height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (audioEnabled) {
                setAudioEncodingBitRate(DEFAULT_AUDIO_BITRATE)
                setAudioSamplingRate(DEFAULT_AUDIO_SAMPLE_RATE)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            setOrientationHint(characteristics.rearCameraOrientationHint())
            prepare()
        }
    }

    private fun rotateSegment() {
        if (!shouldContinueRecording) return

        val previousFile = outputFile
        release(notifyStopped = false)
        callbacks.onSegmentFinalized(previousFile)
        if (!shouldContinueRecording) return

        try {
            openRearCamera(audioEnabled)
        } catch (error: Throwable) {
            callbacks.onRecordingError(error.message ?: "切换到下一录制片段失败")
            shouldContinueRecording = false
            release(notifyStopped = true)
        }
    }

    private fun release(notifyStopped: Boolean) {
        cameraHandler.removeCallbacks(rotateSegmentTask)
        runCatching {
            captureSession?.stopRepeating()
            captureSession?.abortCaptures()
        }
        captureSession?.close()
        captureSession = null

        val recorder = mediaRecorder
        if (recorder != null) {
            runCatching {
                if (recordingStarted) {
                    recorder.stop()
                }
            }.onFailure {
                outputFile?.delete()
                callbacks.onRecordingError("录制片段未正常完成，已删除临时文件")
            }
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
        }
        mediaRecorder = null

        cameraDevice?.close()
        cameraDevice = null

        if (recordingStarted && notifyStopped) {
            callbacks.onRecordingStopped(outputFile)
        }
        recordingStarted = false
    }

    private fun CameraCharacteristics.selectVideoSize(): Size {
        val sizes = get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(MediaRecorder::class.java)
            .orEmpty()
        val preferred = sizes
            .filter { it.width <= 1920 && it.height <= 1080 && it.width >= 1280 && it.height >= 720 }
            .maxByOrNull { it.width * it.height }
        val fallback = sizes.maxByOrNull { it.width * it.height }

        return preferred ?: fallback ?: Size(1280, 720)
    }

    private fun CameraCharacteristics.rearCameraOrientationHint(): Int {
        return get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
    }

    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    interface Callbacks {
        fun onRecordingStarted(file: File?, segmentIndex: Int)
        fun onSegmentFinalized(file: File?)
        fun onSegmentLockRequested(file: File?)
        fun onRecordingStopped(file: File?)
        fun onRecordingError(message: String)
    }

    companion object {
        private const val DEFAULT_VIDEO_BITRATE = 8_000_000
        private const val DEFAULT_FRAME_RATE = 30
        private const val DEFAULT_AUDIO_BITRATE = 128_000
        private const val DEFAULT_AUDIO_SAMPLE_RATE = 44_100
        private const val DEFAULT_SEGMENT_DURATION_MINUTES = 3
        private const val MIN_SEGMENT_DURATION_MINUTES = 1
        private const val MAX_SEGMENT_DURATION_MINUTES = 5
        private const val CAMERA_DIRECTION_REAR = "rear"
    }
}
