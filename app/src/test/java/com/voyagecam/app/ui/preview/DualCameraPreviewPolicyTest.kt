package com.voyagecam.app.ui.preview

import com.voyagecam.app.core.camera.DualCameraSessionStatus
import com.voyagecam.app.core.model.DeviceCapabilityGrade
import com.voyagecam.app.core.model.DualCameraDiagnostic
import com.voyagecam.app.core.model.DualCameraCapability
import com.voyagecam.app.core.model.DualCameraDiagnosticStage
import com.voyagecam.app.core.model.DualCameraSwitchState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DualCameraPreviewPolicyTest {
    @Test
    fun keepsFrontInsetVisibleWhileRecordingWhenDualCameraIsAvailable() {
        val capability = DualCameraCapability(
            state = DualCameraSwitchState.AvailableOn,
            grade = DeviceCapabilityGrade.A,
            reason = "supported",
        )

        assertTrue(
            shouldShowFrontInsetPreview(
                dualCameraEnabled = true,
                capability = capability,
                isRecording = true,
            ),
        )
    }

    @Test
    fun hidesFrontInsetWhenDualCameraSwitchIsOff() {
        val capability = DualCameraCapability(
            state = DualCameraSwitchState.AvailableOff,
            grade = DeviceCapabilityGrade.B,
            reason = "supported",
        )

        assertFalse(
            shouldShowFrontInsetPreview(
                dualCameraEnabled = false,
                capability = capability,
                isRecording = false,
            ),
        )
    }

    @Test
    fun hidesFrontInsetWhenDualCameraIsUnavailable() {
        val capability = DualCameraCapability(
            state = DualCameraSwitchState.Unavailable,
            grade = DeviceCapabilityGrade.D,
            reason = "unsupported",
        )

        assertFalse(
            shouldShowFrontInsetPreview(
                dualCameraEnabled = true,
                capability = capability,
                isRecording = true,
            ),
        )
    }

    @Test
    fun changesPreviewSessionTokenWhenEnteringRecording() {
        val capability = DualCameraCapability(
            state = DualCameraSwitchState.AvailableOn,
            grade = DeviceCapabilityGrade.A,
            reason = "supported",
        )

        val previewPresentation = dualCameraPreviewPresentation(
            dualCameraEnabled = true,
            capability = capability,
            isRecording = false,
        )
        val recordingPresentation = dualCameraPreviewPresentation(
            dualCameraEnabled = true,
            capability = capability,
            isRecording = true,
        )

        assertTrue(previewPresentation.showFrontInset)
        assertTrue(recordingPresentation.showFrontInset)
        assertNotEquals(previewPresentation.sessionToken, recordingPresentation.sessionToken)
    }

    @Test
    fun fallsBackToRearPreviewWhenCurrentSessionHasDiagnosticAndNoConcurrentBinding() {
        assertTrue(
            shouldFallbackToRearPreview(
                frontInsetEnabled = true,
                sessionToken = 2,
                sessionStatus = DualCameraSessionStatus(
                    previewSessionToken = 2,
                    concurrentCameraActive = false,
                    lastDiagnostic = DualCameraDiagnostic(
                        stage = DualCameraDiagnosticStage.Session,
                        detail = "bind failed",
                    ),
                ),
            ),
        )
    }

    @Test
    fun doesNotFallbackWhenConcurrentBindingIsActiveForCurrentSession() {
        assertFalse(
            shouldFallbackToRearPreview(
                frontInsetEnabled = true,
                sessionToken = 2,
                sessionStatus = DualCameraSessionStatus(
                    previewSessionToken = 2,
                    concurrentCameraActive = true,
                    lastDiagnostic = DualCameraDiagnostic(
                        stage = DualCameraDiagnosticStage.Session,
                        detail = "old error",
                    ),
                ),
            ),
        )
    }

    @Test
    fun buildsTelemetryForCurrentSession() {
        val telemetry = dualCameraTelemetryPresentation(
            frontInsetEnabled = true,
            sessionToken = 2,
            sessionStatus = DualCameraSessionStatus(
                previewSessionToken = 2,
                concurrentCameraActive = true,
                recordingActive = true,
                rearPreviewAttached = true,
                frontPreviewAttached = true,
            ),
        )

        assertTrue(telemetry?.summary?.contains("2") == true)
        assertTrue(telemetry?.summary?.contains("Concurrent preview/recording attached") == true)
        assertTrue(telemetry?.detail?.contains("Rear preview") == true)
        assertTrue(telemetry?.detail?.contains("Front preview") == true)
        assertTrue(telemetry?.diagnostic == null)
    }

    @Test
    fun hidesTelemetryForStaleSessionToken() {
        val telemetry = dualCameraTelemetryPresentation(
            frontInsetEnabled = true,
            sessionToken = 2,
            sessionStatus = DualCameraSessionStatus(
                previewSessionToken = 1,
                concurrentCameraActive = false,
            ),
        )

        assertFalse(telemetry != null)
    }

    @Test
    fun telemetryShowsFallbackStateWhenDiagnosticExists() {
        val telemetry = dualCameraTelemetryPresentation(
            frontInsetEnabled = true,
            sessionToken = 2,
            sessionStatus = DualCameraSessionStatus(
                previewSessionToken = 2,
                concurrentCameraActive = false,
                recordingActive = false,
                rearPreviewAttached = true,
                frontPreviewAttached = true,
                lastDiagnostic = DualCameraDiagnostic(
                    stage = DualCameraDiagnosticStage.Session,
                    detail = "bind failed",
                ),
            ),
        )

        assertTrue(telemetry?.summary?.contains("Fell back to rear preview") == true)
        assertTrue(telemetry?.diagnostic?.contains("Session") == true)
    }
}
