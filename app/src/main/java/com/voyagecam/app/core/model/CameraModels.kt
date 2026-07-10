package com.voyagecam.app.core.model

import android.os.Build

data class DualCameraCapability(
    val state: DualCameraSwitchState,
    val grade: DeviceCapabilityGrade = DeviceCapabilityGrade.D,
    val rearCameraId: String? = null,
    val frontCameraId: String? = null,
    val reason: String,
    val rearSummary: String = "",
    val frontSummary: String = "",
    val systemSummary: String = "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}",
    val checkedAtMillis: Long = System.currentTimeMillis(),
    val failureReason: DualCameraFailureReason? = null,
    val previewProbe: DualCameraProbeResult = DualCameraProbeResult(),
    val recordingProbe: DualCameraProbeResult = DualCameraProbeResult(),
    val encodingProbe: DualCameraProbeResult = DualCameraProbeResult(),
) {
    val isAvailable: Boolean
        get() = state == DualCameraSwitchState.AvailableOff || state == DualCameraSwitchState.AvailableOn
}

enum class DualCameraSwitchState {
    Checking,
    AvailableOff,
    AvailableOn,
    Unavailable,
    CheckFailed,
}

enum class DeviceCapabilityGrade {
    A,
    B,
    C,
    D,
}

enum class DualCameraFailureReason {
    PermissionMissing,
    SystemVersionTooLow,
    HalUnsupported,
    ConcurrentRecordingFailed,
    FrontCameraStartupFailed,
    EncodingCapabilityInsufficient,
    Unknown,
}

data class DualCameraProbeResult(
    val status: DualCameraProbeStatus = DualCameraProbeStatus.NotChecked,
    val detail: String = "",
)

enum class DualCameraProbeStatus {
    Supported,
    SupportedWithDowngrade,
    Unsupported,
    Failed,
    NotChecked,
}
