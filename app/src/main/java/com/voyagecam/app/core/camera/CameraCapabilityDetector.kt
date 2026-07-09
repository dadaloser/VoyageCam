package com.voyagecam.app.core.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Range
import android.util.Size
import androidx.core.content.ContextCompat
import com.voyagecam.app.R
import com.voyagecam.app.core.model.DeviceCapabilityGrade
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.DualCameraSwitchState

class CameraCapabilityDetector(private val context: Context) {
    fun detect(currentlyEnabled: Boolean): DualCameraCapability {
        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return DualCameraCapability(
                    state = DualCameraSwitchState.Unavailable,
                    grade = DeviceCapabilityGrade.D,
                    reason = context.getString(R.string.camera_capability_permission_missing),
                )
            }

            val cameraManager = context.getSystemService(CameraManager::class.java)
            val cameraIds = cameraManager.cameraIdList.toList()

            val rearCameraId = cameraIds.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            val frontCameraId = cameraIds.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            }
            val rearCharacteristics = rearCameraId?.let(cameraManager::getCameraCharacteristics)
            val frontCharacteristics = frontCameraId?.let(cameraManager::getCameraCharacteristics)
            val rearSummary = rearCharacteristics?.toCameraSummary()
                ?: context.getString(R.string.camera_capability_no_rear)
            val frontSummary = frontCharacteristics?.toCameraSummary()
                ?: context.getString(R.string.camera_capability_no_front)

            if (rearCameraId == null || frontCameraId == null) {
                return DualCameraCapability(
                    state = DualCameraSwitchState.Unavailable,
                    grade = DeviceCapabilityGrade.D,
                    rearCameraId = rearCameraId,
                    frontCameraId = frontCameraId,
                    reason = context.getString(R.string.camera_capability_incomplete),
                    rearSummary = rearSummary,
                    frontSummary = frontSummary,
                )
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return DualCameraCapability(
                    state = DualCameraSwitchState.Unavailable,
                    grade = DeviceCapabilityGrade.C,
                    rearCameraId = rearCameraId,
                    frontCameraId = frontCameraId,
                    reason = context.getString(R.string.camera_capability_android_version_low),
                    rearSummary = rearSummary,
                    frontSummary = frontSummary,
                )
            }

            val supported = cameraManager.concurrentCameraIds.any { cameraSet ->
                cameraSet.contains(rearCameraId) && cameraSet.contains(frontCameraId)
            }

            if (supported) {
                DualCameraCapability(
                    state = if (currentlyEnabled) {
                        DualCameraSwitchState.AvailableOn
                    } else {
                        DualCameraSwitchState.AvailableOff
                    },
                    grade = DeviceCapabilityGrade.A,
                    rearCameraId = rearCameraId,
                    frontCameraId = frontCameraId,
                    reason = context.getString(R.string.camera_capability_supported),
                    rearSummary = rearSummary,
                    frontSummary = frontSummary,
                )
            } else {
                DualCameraCapability(
                    state = DualCameraSwitchState.Unavailable,
                    grade = DeviceCapabilityGrade.C,
                    rearCameraId = rearCameraId,
                    frontCameraId = frontCameraId,
                    reason = context.getString(R.string.camera_capability_unsupported),
                    rearSummary = rearSummary,
                    frontSummary = frontSummary,
                )
            }
        } catch (error: Throwable) {
            DualCameraCapability(
                state = DualCameraSwitchState.CheckFailed,
                grade = DeviceCapabilityGrade.D,
                reason = error.message ?: context.getString(R.string.camera_capability_check_failed),
            )
        }
    }

    private fun CameraCharacteristics.toCameraSummary(): String {
        val streamMap = get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = streamMap.videoSizesSummary()
        val fpsRanges = (get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray())
            .toFpsSummary()
        val hardwareLevel = when (get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }

        return context.getString(R.string.camera_capability_summary, hardwareLevel, sizes, fpsRanges)
    }

    private fun StreamConfigurationMap?.videoSizesSummary(): String {
        val sizes = this?.getOutputSizes(android.media.MediaRecorder::class.java).orEmpty()
        val preferred = sizes
            .filter { it.width >= 1280 && it.height >= 720 }
            .sortedWith(compareByDescending<Size> { it.width * it.height }.thenByDescending { it.width })
            .take(3)

        return if (preferred.isEmpty()) {
            context.getString(R.string.camera_capability_video_unreported)
        } else {
            preferred.joinToString { "${it.width}x${it.height}" }
        }
    }

    private fun Array<Range<Int>>.toFpsSummary(): String {
        val preferred = filter { it.upper >= 30 }
            .sortedWith(compareByDescending<Range<Int>> { it.upper }.thenByDescending { it.lower })
            .take(3)

        return if (preferred.isEmpty()) {
            context.getString(R.string.camera_capability_fps_unreported)
        } else {
            preferred.joinToString { "${it.lower}-${it.upper}fps" }
        }
    }
}
