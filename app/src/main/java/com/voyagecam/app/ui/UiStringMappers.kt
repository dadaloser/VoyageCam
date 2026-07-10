package com.voyagecam.app.ui

import android.content.Context
import androidx.annotation.StringRes
import com.voyagecam.app.R
import com.voyagecam.app.core.camera.DualCameraSessionStatus
import com.voyagecam.app.core.model.AutoStartResult
import com.voyagecam.app.core.model.AutoStartSource
import com.voyagecam.app.core.model.CameraDirection
import com.voyagecam.app.core.model.CollisionSensitivity
import com.voyagecam.app.core.model.DualCameraDiagnostic
import com.voyagecam.app.core.model.DualCameraDiagnosticStage
import com.voyagecam.app.core.model.DualCameraFailureSource
import com.voyagecam.app.core.model.DualCameraSwitchState
import com.voyagecam.app.core.model.EmergencyTrigger
import com.voyagecam.app.core.model.StructuredLogLevel
import com.voyagecam.app.ui.history.SegmentCameraFilter
import com.voyagecam.app.ui.history.SegmentLockFilter
import com.voyagecam.app.ui.preview.DualCameraTelemetryPresentation

@StringRes
fun CollisionSensitivity.labelRes(): Int = when (this) {
    CollisionSensitivity.Low -> R.string.label_collision_low
    CollisionSensitivity.Medium -> R.string.label_collision_medium
    CollisionSensitivity.High -> R.string.label_collision_high
}

@StringRes
fun CameraDirection.labelRes(): Int = when (this) {
    CameraDirection.Rear -> R.string.label_camera_rear
    CameraDirection.Front -> R.string.label_camera_front
}

@StringRes
fun EmergencyTrigger.labelRes(): Int = when (this) {
    EmergencyTrigger.Manual -> R.string.label_emergency_trigger_manual
    EmergencyTrigger.Collision -> R.string.label_emergency_trigger_collision
}

@StringRes
fun AutoStartSource.labelRes(): Int = when (this) {
    AutoStartSource.Power -> R.string.label_auto_start_source_power
    AutoStartSource.Bluetooth -> R.string.label_auto_start_source_bluetooth
}

@StringRes
fun AutoStartResult.labelRes(): Int = when (this) {
    AutoStartResult.Started -> R.string.label_auto_start_result_started
    AutoStartResult.Ignored -> R.string.label_auto_start_result_ignored
}

@StringRes
fun DualCameraDiagnosticStage.labelRes(): Int = when (this) {
    DualCameraDiagnosticStage.Preview -> R.string.label_dual_camera_stage_preview
    DualCameraDiagnosticStage.Session -> R.string.label_dual_camera_stage_session
    DualCameraDiagnosticStage.RearRecording -> R.string.label_dual_camera_stage_rear_recording
    DualCameraDiagnosticStage.FrontRecording -> R.string.label_dual_camera_stage_front_recording
    DualCameraDiagnosticStage.ConcurrentRecording -> R.string.label_dual_camera_stage_concurrent_recording
}

@StringRes
fun DualCameraSwitchState.labelRes(): Int = when (this) {
    DualCameraSwitchState.Checking -> R.string.label_dual_camera_state_checking
    DualCameraSwitchState.AvailableOff -> R.string.label_dual_camera_state_available_off
    DualCameraSwitchState.AvailableOn -> R.string.label_dual_camera_state_available_on
    DualCameraSwitchState.Unavailable -> R.string.label_dual_camera_state_unavailable
    DualCameraSwitchState.CheckFailed -> R.string.label_dual_camera_state_check_failed
}

@StringRes
fun DualCameraFailureSource.labelRes(): Int = when (this) {
    DualCameraFailureSource.SessionCoordinator -> R.string.label_dual_camera_failure_source_session_coordinator
    DualCameraFailureSource.RecordingService -> R.string.label_dual_camera_failure_source_recording_service
    DualCameraFailureSource.PerformanceGuard -> R.string.label_dual_camera_failure_source_performance_guard
}

@StringRes
fun StructuredLogLevel.labelRes(): Int = when (this) {
    StructuredLogLevel.Debug -> R.string.label_log_level_debug
    StructuredLogLevel.Info -> R.string.label_log_level_info
    StructuredLogLevel.Warn -> R.string.label_log_level_warn
    StructuredLogLevel.Error -> R.string.label_log_level_error
    StructuredLogLevel.Fatal -> R.string.label_log_level_fatal
}

@StringRes
fun SegmentCameraFilter.labelRes(): Int = when (this) {
    SegmentCameraFilter.All -> R.string.label_segment_filter_all
    SegmentCameraFilter.Rear -> R.string.label_camera_rear
    SegmentCameraFilter.Front -> R.string.label_camera_front
}

@StringRes
fun SegmentLockFilter.labelRes(): Int = when (this) {
    SegmentLockFilter.All -> R.string.label_segment_filter_all
    SegmentLockFilter.Normal -> R.string.label_segment_filter_normal
    SegmentLockFilter.Locked -> R.string.label_segment_filter_locked
}

fun Context.dualCameraDiagnosticSummary(diagnostic: DualCameraDiagnostic): String {
    return getString(
        R.string.preview_dual_camera_diagnostic_summary,
        getString(diagnostic.stage.labelRes()),
        diagnostic.detail,
    )
}

fun Context.dualCameraTelemetryPresentation(
    frontInsetEnabled: Boolean,
    sessionToken: Int,
    sessionStatus: DualCameraSessionStatus,
): DualCameraTelemetryPresentation? {
    if (!frontInsetEnabled) return null
    if (sessionStatus.previewSessionToken != sessionToken) return null

    val stateLabel = getString(
        when {
            sessionStatus.concurrentCameraActive && sessionStatus.recordingActive -> {
                R.string.preview_telemetry_state_bound_recording
            }

            sessionStatus.concurrentCameraActive -> R.string.preview_telemetry_state_bound_preview
            sessionStatus.recordingActive -> R.string.preview_telemetry_state_recording_switch
            sessionStatus.lastDiagnostic != null -> R.string.preview_telemetry_state_rear_fallback
            else -> R.string.preview_telemetry_state_preparing
        },
    )
    val summary = getString(R.string.preview_telemetry_summary, sessionToken, stateLabel)
    val detail = getString(
        R.string.preview_telemetry_detail,
        getString(R.string.preview_telemetry_detail_rear),
        getString(if (sessionStatus.rearPreviewAttached) R.string.preview_telemetry_connected else R.string.preview_telemetry_disconnected),
        getString(R.string.preview_telemetry_detail_front),
        getString(if (sessionStatus.frontPreviewAttached) R.string.preview_telemetry_connected else R.string.preview_telemetry_disconnected),
    )

    return DualCameraTelemetryPresentation(
        summary = summary,
        detail = detail,
        diagnostic = sessionStatus.lastDiagnostic?.let(::dualCameraDiagnosticSummary),
    )
}
