package com.voyagecam.app.ui.preview

import android.content.Context
import com.voyagecam.app.core.camera.DualCameraSessionStatus
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.ui.dualCameraTelemetryPresentation as buildDualCameraTelemetryPresentation

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
        append("Dual Session ")
        append(sessionToken)
        append(" · ")
        append(
            when {
                sessionStatus.concurrentCameraActive && sessionStatus.recordingActive -> "Concurrent preview/recording attached"
                sessionStatus.concurrentCameraActive -> "Concurrent preview attached"
                sessionStatus.recordingActive -> "Switching recording session"
                sessionStatus.lastDiagnostic != null -> "Fell back to rear preview"
                else -> "Preparing"
            },
        )
    }
    val detail = buildString {
        append("Rear preview ")
        append(if (sessionStatus.rearPreviewAttached) "connected" else "disconnected")
        append(" · Front preview ")
        append(if (sessionStatus.frontPreviewAttached) "connected" else "disconnected")
    }
    return DualCameraTelemetryPresentation(
        summary = summary,
        detail = detail,
        diagnostic = sessionStatus.lastDiagnostic?.summary(),
    )
}

fun dualCameraTelemetryPresentation(
    context: Context,
    frontInsetEnabled: Boolean,
    sessionToken: Int,
    sessionStatus: DualCameraSessionStatus,
): DualCameraTelemetryPresentation? {
    return context.buildDualCameraTelemetryPresentation(frontInsetEnabled, sessionToken, sessionStatus)
}

private const val SESSION_HIDDEN = 0
private const val SESSION_PREVIEW = 1
private const val SESSION_RECORDING = 2
