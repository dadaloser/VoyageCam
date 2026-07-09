package com.voyagecam.app.ui.preview

import com.voyagecam.app.core.camera.DualCameraSessionStatus
import com.voyagecam.app.core.model.DualCameraCapability

data class DualCameraPreviewPresentation(
    val showFrontInset: Boolean,
    val sessionToken: Int,
)

data class DualCameraTelemetryPresentation(
    val summary: String,
    val detail: String,
    val diagnostic: String? = null,
)

fun dualCameraPreviewPresentation(
    dualCameraEnabled: Boolean,
    capability: DualCameraCapability,
    isRecording: Boolean,
): DualCameraPreviewPresentation {
    val showFrontInset = dualCameraEnabled && capability.isAvailable
    return DualCameraPreviewPresentation(
        showFrontInset = showFrontInset,
        sessionToken = when {
            !showFrontInset -> SESSION_HIDDEN
            isRecording -> SESSION_RECORDING
            else -> SESSION_PREVIEW
        },
    )
}

fun shouldShowFrontInsetPreview(
    dualCameraEnabled: Boolean,
    capability: DualCameraCapability,
    isRecording: Boolean,
): Boolean {
    return dualCameraPreviewPresentation(
        dualCameraEnabled = dualCameraEnabled,
        capability = capability,
        isRecording = isRecording,
    ).showFrontInset
}

fun shouldFallbackToRearPreview(
    frontInsetEnabled: Boolean,
    sessionToken: Int,
    sessionStatus: DualCameraSessionStatus,
): Boolean {
    if (!frontInsetEnabled) return false
    if (sessionStatus.previewSessionToken != sessionToken) return false
    if (sessionStatus.concurrentCameraActive) return false
    return sessionStatus.lastDiagnostic != null
}

fun dualCameraTelemetryPresentation(
    frontInsetEnabled: Boolean,
    sessionToken: Int,
    sessionStatus: DualCameraSessionStatus,
): DualCameraTelemetryPresentation? {
    if (!frontInsetEnabled) return null
    if (sessionStatus.previewSessionToken != sessionToken) return null

    val summary = buildString {
        append("双摄 Session ")
        append(sessionToken)
        append(" · ")
        append(
            when {
                sessionStatus.concurrentCameraActive && sessionStatus.recordingActive -> "并发预览/录制已绑定"
                sessionStatus.concurrentCameraActive -> "并发预览已绑定"
                sessionStatus.recordingActive -> "录制切换中"
                sessionStatus.lastDiagnostic != null -> "已回落到后摄预览"
                else -> "准备中"
            },
        )
    }
    val detail = buildString {
        append("后摄预览")
        append(if (sessionStatus.rearPreviewAttached) "已连接" else "未连接")
        append(" · 前摄预览")
        append(if (sessionStatus.frontPreviewAttached) "已连接" else "未连接")
    }
    return DualCameraTelemetryPresentation(
        summary = summary,
        detail = detail,
        diagnostic = sessionStatus.lastDiagnostic?.summary(),
    )
}

private const val SESSION_HIDDEN = 0
private const val SESSION_PREVIEW = 1
private const val SESSION_RECORDING = 2
