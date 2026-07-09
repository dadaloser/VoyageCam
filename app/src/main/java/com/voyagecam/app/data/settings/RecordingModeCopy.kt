package com.voyagecam.app.data.settings

import android.content.Context
import com.voyagecam.app.R

fun recordingModeLabelRes(
    recordingModeAuto: Boolean,
    dualCameraActive: Boolean,
): Int {
    return when {
        recordingModeAuto && dualCameraActive -> R.string.recording_mode_auto_dual_active
        recordingModeAuto -> R.string.recording_mode_auto_rear_active
        else -> R.string.recording_mode_rear_only
    }
}

fun Context.recordingModeLabel(
    recordingModeAuto: Boolean,
    dualCameraActive: Boolean,
): String {
    return getString(recordingModeLabelRes(recordingModeAuto, dualCameraActive))
}

fun recordingModeDescriptionRes(
    recordingModeAuto: Boolean,
    dualCameraSupported: Boolean,
): Int {
    return when {
        recordingModeAuto && dualCameraSupported -> R.string.recording_mode_auto_supported_description
        recordingModeAuto -> R.string.recording_mode_auto_rear_only_description
        else -> R.string.recording_mode_rear_only_description
    }
}

fun Context.recordingModeDescription(
    recordingModeAuto: Boolean,
    dualCameraSupported: Boolean,
): String {
    return getString(recordingModeDescriptionRes(recordingModeAuto, dualCameraSupported))
}
