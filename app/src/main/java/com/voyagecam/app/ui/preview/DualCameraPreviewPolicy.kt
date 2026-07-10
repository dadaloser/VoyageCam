package com.voyagecam.app.ui.preview

import android.content.Context
import com.voyagecam.app.core.camera.DualCameraSessionStatus
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.data.settings.RecordingMode
import com.voyagecam.app.data.settings.VoyageCamSettings
import com.voyagecam.app.data.settings.resolveRecordingConfig
import com.voyagecam.app.ui.dualCameraTelemetryPresentation as buildDualCameraTelemetryPresentation

data class DualCameraPreviewPresentation(
    val dualPreviewActive: Boolean,
    val sessionToken: Int,
    val mainCameraDirection: CameraDirection,
    val insetCameraDirection: CameraDirection? = null,
    val swapSupported: Boolean = false,
)

val DualCameraPreviewPresentation.frontInsetVisible: Boolean
    get() = insetCameraDirection == CameraDirection.Front

val DualCameraPreviewPresentation.rearInsetVisible: Boolean
    get() = insetCameraDirection == CameraDirection.Rear

data class DualCameraTelemetryPresentation(
    val summary: String,
    val detail: String,
    val diagnostic: String? = null,
)

fun dualCameraPreviewPresentation(
    settings: VoyageCamSettings,
    capability: DualCameraCapability,
    isRecording: Boolean,
    preferredMainCameraDirection: CameraDirection = CameraDirection.Rear,
): DualCameraPreviewPresentation {
    val resolved = settings.resolveRecordingConfig(capability)
    val dualPreviewActive = settings.recordingMode == RecordingMode.Auto && resolved.dualCameraActive
    val mainCameraDirection = when {
        !dualPreviewActive -> resolved.primaryCameraDirection
        preferredMainCameraDirection == CameraDirection.Front -> CameraDirection.Front
        else -> CameraDirection.Rear
    }
    val insetCameraDirection = if (dualPreviewActive) {
        if (mainCameraDirection == CameraDirection.Rear) {
            CameraDirection.Front
        } else {
            CameraDirection.Rear
        }
    } else {
        null
    }
    return DualCameraPreviewPresentation(
        dualPreviewActive = dualPreviewActive,
        sessionToken = when {
            !dualPreviewActive -> SESSION_HIDDEN
            isRecording -> SESSION_RECORDING
            else -> SESSION_PREVIEW
        },
        mainCameraDirection = mainCameraDirection,
        insetCameraDirection = insetCameraDirection,
        swapSupported = dualPreviewActive,
    )
}

fun shouldShowFrontInsetPreview(
    settings: VoyageCamSettings,
    capability: DualCameraCapability,
    isRecording: Boolean,
): Boolean {
    return dualCameraPreviewPresentation(
        settings = settings,
        capability = capability,
        isRecording = isRecording,
    ).frontInsetVisible
}

fun shouldFallbackToRearPreview(
    dualPreviewActive: Boolean,
    sessionToken: Int,
    sessionStatus: DualCameraSessionStatus,
): Boolean {
    if (!dualPreviewActive) return false
    if (sessionStatus.previewSessionToken != sessionToken) return false
    if (sessionStatus.concurrentCameraActive) return false
    return sessionStatus.lastDiagnostic != null
}

fun dualCameraTelemetryPresentation(
    dualPreviewActive: Boolean,
    sessionToken: Int,
    sessionStatus: DualCameraSessionStatus,
): DualCameraTelemetryPresentation? {
    if (!dualPreviewActive) return null
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
    dualPreviewActive: Boolean,
    sessionToken: Int,
    sessionStatus: DualCameraSessionStatus,
): DualCameraTelemetryPresentation? {
    return context.buildDualCameraTelemetryPresentation(dualPreviewActive, sessionToken, sessionStatus)
}

private const val SESSION_HIDDEN = 0
private const val SESSION_PREVIEW = 1
private const val SESSION_RECORDING = 2
