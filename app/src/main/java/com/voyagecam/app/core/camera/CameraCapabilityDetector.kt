package com.voyagecam.app.core.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.os.Build
import android.util.Range
import android.util.Size
import androidx.core.content.ContextCompat
import com.voyagecam.app.R
import com.voyagecam.app.core.model.DeviceCapabilityGrade
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.DualCameraFailureReason
import com.voyagecam.app.core.model.DualCameraProbeResult
import com.voyagecam.app.core.model.DualCameraProbeStatus
import com.voyagecam.app.core.model.DualCameraSwitchState
import com.voyagecam.app.data.settings.RecordingBitratePreset
import com.voyagecam.app.data.settings.RecordingFrameRatePreset
import com.voyagecam.app.data.settings.RecordingResolutionPreset
import com.voyagecam.app.data.settings.RecordingVideoProfile
import com.voyagecam.app.data.settings.supportsDualCamera

class CameraCapabilityDetector(private val context: Context) {
    fun detect(
        currentlyEnabled: Boolean,
        currentProfile: RecordingVideoProfile,
    ): DualCameraCapability {
        return try {
            if (!hasCameraPermission()) {
                return DualCameraCapability(
                    state = DualCameraSwitchState.Unavailable,
                    grade = DeviceCapabilityGrade.D,
                    reason = context.getString(R.string.camera_capability_permission_missing),
                    failureReason = DualCameraFailureReason.PermissionMissing,
                    previewProbe = probeResult(
                        DualCameraProbeStatus.Failed,
                        R.string.camera_capability_preview_permission_missing,
                    ),
                    recordingProbe = probeResult(
                        DualCameraProbeStatus.Failed,
                        R.string.camera_capability_recording_permission_missing,
                    ),
                    encodingProbe = probeResult(
                        DualCameraProbeStatus.NotChecked,
                        R.string.camera_capability_encoding_not_checked,
                    ),
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

            val assessment = assessDualCameraCapability(
                DualCameraCapabilityAssessmentInput(
                    hasRearCamera = rearCameraId != null,
                    hasFrontCamera = frontCameraId != null,
                    apiLevelSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                    frontCameraUsable = frontCharacteristics?.hasUsablePreviewOutput() == true,
                    concurrentPreviewSupported = if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        rearCameraId != null &&
                        frontCameraId != null
                    ) {
                        cameraManager.getConcurrentCameraIds().any { set ->
                            set.contains(rearCameraId) && set.contains(frontCameraId)
                        }
                    } else {
                        false
                    },
                    preferredConcurrentRecordingSupported = if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        rearCameraId != null &&
                        frontCameraId != null &&
                        rearCharacteristics != null &&
                        frontCharacteristics != null
                    ) {
                        cameraManager.supportsConcurrentRecording(
                            rearCameraId = rearCameraId,
                            frontCameraId = frontCameraId,
                            rearCharacteristics = rearCharacteristics,
                            frontCharacteristics = frontCharacteristics,
                            profile = DUAL_FULL_PROFILE,
                        )
                    } else {
                        false
                    },
                    safeConcurrentRecordingSupported = if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        rearCameraId != null &&
                        frontCameraId != null &&
                        rearCharacteristics != null &&
                        frontCharacteristics != null
                    ) {
                        cameraManager.supportsConcurrentRecording(
                            rearCameraId = rearCameraId,
                            frontCameraId = frontCameraId,
                            rearCharacteristics = rearCharacteristics,
                            frontCharacteristics = frontCharacteristics,
                            profile = DUAL_SAFE_PROFILE,
                        )
                    } else {
                        false
                    },
                    currentProfileConcurrentRecordingSupported = if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        rearCameraId != null &&
                        frontCameraId != null &&
                        rearCharacteristics != null &&
                        frontCharacteristics != null
                    ) {
                        cameraManager.supportsConcurrentRecording(
                            rearCameraId = rearCameraId,
                            frontCameraId = frontCameraId,
                            rearCharacteristics = rearCharacteristics,
                            frontCharacteristics = frontCharacteristics,
                            profile = currentProfile,
                        )
                    } else {
                        false
                    },
                    currentProfileDualSafe = currentProfile.isWithinSafeDualTier(),
                ),
            )

            DualCameraCapability(
                state = when (assessment.grade) {
                    DeviceCapabilityGrade.A -> {
                        if (currentlyEnabled) {
                            DualCameraSwitchState.AvailableOn
                        } else {
                            DualCameraSwitchState.AvailableOff
                        }
                    }

                    else -> assessment.state
                },
                grade = assessment.grade,
                rearCameraId = rearCameraId,
                frontCameraId = frontCameraId,
                reason = assessment.reason(context),
                rearSummary = rearSummary,
                frontSummary = frontSummary,
                systemSummary = context.getString(
                    R.string.camera_capability_system_summary,
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT,
                    currentProfile.label(context),
                ),
                failureReason = assessment.failureReason,
                previewProbe = assessment.previewProbe.toUiProbeResult(context),
                recordingProbe = assessment.recordingProbe.toUiProbeResult(context),
                encodingProbe = assessment.encodingProbe.toUiProbeResult(context),
            )
        } catch (error: Throwable) {
            DualCameraCapability(
                state = DualCameraSwitchState.CheckFailed,
                grade = DeviceCapabilityGrade.D,
                reason = error.message ?: context.getString(R.string.camera_capability_check_failed),
                failureReason = DualCameraFailureReason.Unknown,
                previewProbe = probeResult(
                    DualCameraProbeStatus.Failed,
                    R.string.camera_capability_preview_detection_failed,
                ),
                recordingProbe = probeResult(
                    DualCameraProbeStatus.Failed,
                    R.string.camera_capability_recording_detection_failed,
                ),
                encodingProbe = probeResult(
                    DualCameraProbeStatus.Failed,
                    R.string.camera_capability_encoding_detection_failed,
                ),
            )
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun CameraManager.supportsConcurrentRecording(
        rearCameraId: String,
        frontCameraId: String,
        rearCharacteristics: CameraCharacteristics,
        frontCharacteristics: CameraCharacteristics,
        profile: RecordingVideoProfile,
    ): Boolean {
        if (!rearCharacteristics.supportsProfile(profile) || !frontCharacteristics.supportsProfile(profile)) {
            return false
        }

        val sessionMap = mapOf(
            rearCameraId to SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                listOf(recordOutputFor(profile)),
            ),
            frontCameraId to SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                listOf(recordOutputFor(profile)),
            ),
        )

        return runCatching {
            isConcurrentSessionConfigurationSupported(sessionMap)
        }.getOrDefault(false)
    }

    private fun CameraCharacteristics.hasUsablePreviewOutput(): Boolean {
        val sizes = get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(SurfaceTexture::class.java)
            .orEmpty()
        return sizes.isNotEmpty()
    }

    private fun CameraCharacteristics.supportsProfile(profile: RecordingVideoProfile): Boolean {
        val streamMap = get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return false
        val targetSize = profile.resolution.size()
        val sizes = streamMap.getOutputSizes(MediaRecorder::class.java).orEmpty()
        val supportsSize = sizes.any { size ->
            size.width >= targetSize.width && size.height >= targetSize.height
        }
        if (!supportsSize) return false
        val ranges = get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
        return ranges.any { range -> range.upper >= profile.frameRate.fps }
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
        val sizes = this?.getOutputSizes(MediaRecorder::class.java).orEmpty()
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

    private fun RecordingVideoProfile.label(context: Context): String {
        return context.getString(
            R.string.camera_capability_profile_label,
            resolution.label,
            frameRate.label,
            bitrate.label,
        )
    }

    private fun RecordingVideoProfile.isWithinSafeDualTier(): Boolean {
        return supportsDualCamera()
    }

    private fun RecordingResolutionPreset.size(): Size {
        return when (this) {
            RecordingResolutionPreset.HD_720P -> Size(1280, 720)
            RecordingResolutionPreset.FHD_1080P -> Size(1920, 1080)
            RecordingResolutionPreset.UHD_2160P -> Size(3840, 2160)
        }
    }

    private fun recordOutputFor(profile: RecordingVideoProfile): OutputConfiguration {
        return OutputConfiguration(profile.resolution.size(), MediaRecorder::class.java)
    }

    private fun probeResult(
        status: DualCameraProbeStatus,
        detailRes: Int,
    ): DualCameraProbeResult {
        return DualCameraProbeResult(
            status = status,
            detail = context.getString(detailRes),
        )
    }

    private companion object {
        val DUAL_FULL_PROFILE = RecordingVideoProfile(
            resolution = RecordingResolutionPreset.FHD_1080P,
            frameRate = RecordingFrameRatePreset.FPS_30,
            bitrate = RecordingBitratePreset.MBPS_12,
        )
        val DUAL_SAFE_PROFILE = RecordingVideoProfile(
            resolution = RecordingResolutionPreset.HD_720P,
            frameRate = RecordingFrameRatePreset.FPS_30,
            bitrate = RecordingBitratePreset.MBPS_8,
        )
    }
}

internal data class DualCameraCapabilityAssessmentInput(
    val hasRearCamera: Boolean,
    val hasFrontCamera: Boolean,
    val apiLevelSupported: Boolean,
    val frontCameraUsable: Boolean,
    val concurrentPreviewSupported: Boolean,
    val preferredConcurrentRecordingSupported: Boolean,
    val safeConcurrentRecordingSupported: Boolean,
    val currentProfileConcurrentRecordingSupported: Boolean,
    val currentProfileDualSafe: Boolean,
)

internal data class DualCameraCapabilityAssessment(
    val grade: DeviceCapabilityGrade,
    val state: DualCameraSwitchState,
    val failureReason: DualCameraFailureReason?,
    val previewProbe: CapabilityProbeTemplate,
    val recordingProbe: CapabilityProbeTemplate,
    val encodingProbe: CapabilityProbeTemplate,
    val reasonResId: Int,
)

internal data class CapabilityProbeTemplate(
    val status: DualCameraProbeStatus,
    val detailResId: Int,
)

internal fun assessDualCameraCapability(
    input: DualCameraCapabilityAssessmentInput,
): DualCameraCapabilityAssessment {
    return when {
        !input.hasRearCamera -> {
            DualCameraCapabilityAssessment(
                grade = DeviceCapabilityGrade.D,
                state = DualCameraSwitchState.Unavailable,
                failureReason = DualCameraFailureReason.Unknown,
                previewProbe = CapabilityProbeTemplate(
                    DualCameraProbeStatus.Failed,
                    R.string.camera_capability_preview_detection_failed,
                ),
                recordingProbe = CapabilityProbeTemplate(
                    DualCameraProbeStatus.Failed,
                    R.string.camera_capability_recording_detection_failed,
                ),
                encodingProbe = CapabilityProbeTemplate(
                    DualCameraProbeStatus.NotChecked,
                    R.string.camera_capability_encoding_not_checked,
                ),
                reasonResId = R.string.camera_capability_no_rear,
            )
        }

        !input.hasFrontCamera || !input.frontCameraUsable -> {
            DualCameraCapabilityAssessment(
                grade = DeviceCapabilityGrade.C,
                state = DualCameraSwitchState.Unavailable,
                failureReason = DualCameraFailureReason.FrontCameraStartupFailed,
                previewProbe = CapabilityProbeTemplate(
                    DualCameraProbeStatus.Unsupported,
                    R.string.camera_capability_preview_front_start_failed,
                ),
                recordingProbe = CapabilityProbeTemplate(
                    DualCameraProbeStatus.Unsupported,
                    R.string.camera_capability_recording_front_start_failed,
                ),
                encodingProbe = CapabilityProbeTemplate(
                    DualCameraProbeStatus.NotChecked,
                    R.string.camera_capability_encoding_not_checked,
                ),
                reasonResId = R.string.camera_capability_front_start_failed,
            )
        }

        !input.apiLevelSupported -> {
            DualCameraCapabilityAssessment(
                grade = DeviceCapabilityGrade.C,
                state = DualCameraSwitchState.Unavailable,
                failureReason = DualCameraFailureReason.SystemVersionTooLow,
                previewProbe = CapabilityProbeTemplate(
                    DualCameraProbeStatus.Unsupported,
                    R.string.camera_capability_preview_system_version_low,
                ),
                recordingProbe = CapabilityProbeTemplate(
                    DualCameraProbeStatus.Unsupported,
                    R.string.camera_capability_recording_system_version_low,
                ),
                encodingProbe = CapabilityProbeTemplate(
                    DualCameraProbeStatus.NotChecked,
                    R.string.camera_capability_encoding_not_checked,
                ),
                reasonResId = R.string.camera_capability_android_version_low,
            )
        }

        !input.concurrentPreviewSupported -> {
            DualCameraCapabilityAssessment(
                grade = DeviceCapabilityGrade.C,
                state = DualCameraSwitchState.Unavailable,
                failureReason = DualCameraFailureReason.HalUnsupported,
                previewProbe = CapabilityProbeTemplate(
                    DualCameraProbeStatus.Unsupported,
                    R.string.camera_capability_preview_hal_unsupported,
                ),
                recordingProbe = CapabilityProbeTemplate(
                    DualCameraProbeStatus.Unsupported,
                    R.string.camera_capability_recording_hal_unsupported,
                ),
                encodingProbe = CapabilityProbeTemplate(
                    DualCameraProbeStatus.NotChecked,
                    R.string.camera_capability_encoding_not_checked,
                ),
                reasonResId = R.string.camera_capability_unsupported,
            )
        }

        input.safeConcurrentRecordingSupported -> {
            val recordingStatus = if (input.preferredConcurrentRecordingSupported) {
                DualCameraProbeStatus.Supported
            } else {
                DualCameraProbeStatus.SupportedWithDowngrade
            }
            val recordingDetail = if (input.preferredConcurrentRecordingSupported) {
                R.string.camera_capability_recording_supported_full
            } else {
                R.string.camera_capability_recording_supported_downgrade
            }
            val encodingStatus = when {
                input.currentProfileConcurrentRecordingSupported && input.currentProfileDualSafe -> {
                    DualCameraProbeStatus.Supported
                }

                else -> {
                    DualCameraProbeStatus.SupportedWithDowngrade
                }
            }
            val encodingDetail = if (encodingStatus == DualCameraProbeStatus.Supported) {
                R.string.camera_capability_encoding_supported
            } else {
                R.string.camera_capability_encoding_downgrade_required
            }
            DualCameraCapabilityAssessment(
                grade = DeviceCapabilityGrade.A,
                state = DualCameraSwitchState.AvailableOff,
                failureReason = null,
                previewProbe = CapabilityProbeTemplate(
                    DualCameraProbeStatus.Supported,
                    R.string.camera_capability_preview_supported,
                ),
                recordingProbe = CapabilityProbeTemplate(recordingStatus, recordingDetail),
                encodingProbe = CapabilityProbeTemplate(encodingStatus, encodingDetail),
                reasonResId = if (recordingStatus == DualCameraProbeStatus.Supported) {
                    R.string.camera_capability_supported_full
                } else {
                    R.string.camera_capability_supported_downgrade
                },
            )
        }

        else -> {
            val failureReason = if (input.currentProfileDualSafe) {
                DualCameraFailureReason.ConcurrentRecordingFailed
            } else {
                DualCameraFailureReason.EncodingCapabilityInsufficient
            }
            val reasonResId = if (failureReason == DualCameraFailureReason.ConcurrentRecordingFailed) {
                R.string.camera_capability_recording_failed
            } else {
                R.string.camera_capability_encoding_insufficient
            }
            DualCameraCapabilityAssessment(
                grade = DeviceCapabilityGrade.B,
                state = DualCameraSwitchState.Unavailable,
                failureReason = failureReason,
                previewProbe = CapabilityProbeTemplate(
                    DualCameraProbeStatus.Supported,
                    R.string.camera_capability_preview_supported,
                ),
                recordingProbe = CapabilityProbeTemplate(
                    DualCameraProbeStatus.Unsupported,
                    if (failureReason == DualCameraFailureReason.ConcurrentRecordingFailed) {
                        R.string.camera_capability_recording_failed
                    } else {
                        R.string.camera_capability_recording_encoding_limited
                    },
                ),
                encodingProbe = CapabilityProbeTemplate(
                    DualCameraProbeStatus.Unsupported,
                    if (failureReason == DualCameraFailureReason.ConcurrentRecordingFailed) {
                        R.string.camera_capability_encoding_not_enough_for_concurrent_recording
                    } else {
                        R.string.camera_capability_encoding_insufficient
                    },
                ),
                reasonResId = reasonResId,
            )
        }
    }
}

private fun DualCameraCapabilityAssessment.reason(context: Context): String {
    return context.getString(reasonResId)
}

private fun CapabilityProbeTemplate.toUiProbeResult(context: Context): DualCameraProbeResult {
    return DualCameraProbeResult(
        status = status,
        detail = context.getString(detailResId),
    )
}
