package com.voyagecam.app.data.settings

import android.content.Context
import com.voyagecam.app.R
import com.voyagecam.app.core.model.CameraDirection

fun recordingModeLabelRes(
    requestedMode: RecordingMode,
    dualCameraActive: Boolean,
    primaryCameraDirection: CameraDirection = CameraDirection.Rear,
): Int {
    return when {
        requestedMode == RecordingMode.Auto && dualCameraActive -> R.string.recording_mode_auto_dual_active
        requestedMode == RecordingMode.Auto -> R.string.recording_mode_auto_rear_active
        primaryCameraDirection == CameraDirection.Front -> R.string.recording_mode_front_only
        else -> R.string.recording_mode_rear_only
    }
}

fun Context.recordingModeLabel(
    requestedMode: RecordingMode,
    dualCameraActive: Boolean,
    primaryCameraDirection: CameraDirection = CameraDirection.Rear,
): String {
    return getString(
        recordingModeLabelRes(
            requestedMode = requestedMode,
            dualCameraActive = dualCameraActive,
            primaryCameraDirection = primaryCameraDirection,
        ),
    )
}

fun recordingModeDescriptionRes(
    settings: VoyageCamSettings,
    dualCameraSupported: Boolean,
    hasFrontCamera: Boolean,
): Int {
    val resolved = settings.resolveRecordingConfig(
        capability = DualCameraCapabilityShim(
            dualCameraSupported = dualCameraSupported,
            hasFrontCamera = hasFrontCamera,
        ),
    )
    return when {
        settings.recordingMode == RecordingMode.Auto && resolved.dualCameraActive ->
            R.string.recording_mode_auto_supported_description
        settings.recordingMode == RecordingMode.Auto &&
            resolved.downgradeReason == RecordingConfigDowngradeReason.DualCameraProfileUnsupported ->
            R.string.recording_mode_auto_profile_fallback_description
        settings.recordingMode == RecordingMode.Auto ->
            R.string.recording_mode_auto_rear_only_description
        settings.recordingMode == RecordingMode.FrontOnly && hasFrontCamera ->
            R.string.recording_mode_front_only_description
        settings.recordingMode == RecordingMode.FrontOnly ->
            R.string.recording_mode_front_only_unavailable_description
        else -> R.string.recording_mode_rear_only_description
    }
}

fun Context.recordingModeDescription(
    settings: VoyageCamSettings,
    dualCameraSupported: Boolean,
    hasFrontCamera: Boolean,
): String {
    return getString(
        recordingModeDescriptionRes(
            settings = settings,
            dualCameraSupported = dualCameraSupported,
            hasFrontCamera = hasFrontCamera,
        ),
    )
}

private data class DualCameraCapabilityShim(
    val dualCameraSupported: Boolean,
    val hasFrontCamera: Boolean,
)

private fun VoyageCamSettings.resolveRecordingConfig(capability: DualCameraCapabilityShim): ResolvedRecordingConfig {
    return resolveRecordingConfig(
        capability = com.voyagecam.app.core.model.DualCameraCapability(
            state = if (capability.dualCameraSupported) {
                com.voyagecam.app.core.model.DualCameraSwitchState.AvailableOn
            } else {
                com.voyagecam.app.core.model.DualCameraSwitchState.Unavailable
            },
            rearCameraId = "rear",
            frontCameraId = if (capability.hasFrontCamera) "front" else null,
            reason = "",
        ),
    )
}
