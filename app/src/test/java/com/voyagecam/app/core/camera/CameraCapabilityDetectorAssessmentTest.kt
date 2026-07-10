package com.voyagecam.app.core.camera

import com.voyagecam.app.core.model.DeviceCapabilityGrade
import com.voyagecam.app.core.model.DualCameraFailureReason
import com.voyagecam.app.core.model.DualCameraProbeStatus
import com.voyagecam.app.core.model.DualCameraSwitchState
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraCapabilityDetectorAssessmentTest {
    @Test
    fun returnsGradeAWhenConcurrentPreviewAndRecordingAreSupported() {
        val assessment = assessDualCameraCapability(
            DualCameraCapabilityAssessmentInput(
                hasRearCamera = true,
                hasFrontCamera = true,
                apiLevelSupported = true,
                frontCameraUsable = true,
                concurrentPreviewSupported = true,
                preferredConcurrentRecordingSupported = true,
                safeConcurrentRecordingSupported = true,
                currentProfileConcurrentRecordingSupported = true,
                currentProfileDualSafe = true,
            ),
        )

        assertEquals(DeviceCapabilityGrade.A, assessment.grade)
        assertEquals(DualCameraSwitchState.AvailableOff, assessment.state)
        assertEquals(null, assessment.failureReason)
        assertEquals(DualCameraProbeStatus.Supported, assessment.previewProbe.status)
        assertEquals(DualCameraProbeStatus.Supported, assessment.recordingProbe.status)
        assertEquals(DualCameraProbeStatus.Supported, assessment.encodingProbe.status)
    }

    @Test
    fun returnsGradeBWhenOnlyConcurrentPreviewIsSupported() {
        val assessment = assessDualCameraCapability(
            DualCameraCapabilityAssessmentInput(
                hasRearCamera = true,
                hasFrontCamera = true,
                apiLevelSupported = true,
                frontCameraUsable = true,
                concurrentPreviewSupported = true,
                preferredConcurrentRecordingSupported = false,
                safeConcurrentRecordingSupported = false,
                currentProfileConcurrentRecordingSupported = false,
                currentProfileDualSafe = true,
            ),
        )

        assertEquals(DeviceCapabilityGrade.B, assessment.grade)
        assertEquals(DualCameraFailureReason.ConcurrentRecordingFailed, assessment.failureReason)
        assertEquals(DualCameraProbeStatus.Supported, assessment.previewProbe.status)
        assertEquals(DualCameraProbeStatus.Unsupported, assessment.recordingProbe.status)
    }

    @Test
    fun returnsGradeCWhenHalDoesNotSupportConcurrentPreview() {
        val assessment = assessDualCameraCapability(
            DualCameraCapabilityAssessmentInput(
                hasRearCamera = true,
                hasFrontCamera = true,
                apiLevelSupported = true,
                frontCameraUsable = true,
                concurrentPreviewSupported = false,
                preferredConcurrentRecordingSupported = false,
                safeConcurrentRecordingSupported = false,
                currentProfileConcurrentRecordingSupported = false,
                currentProfileDualSafe = true,
            ),
        )

        assertEquals(DeviceCapabilityGrade.C, assessment.grade)
        assertEquals(DualCameraFailureReason.HalUnsupported, assessment.failureReason)
        assertEquals(DualCameraProbeStatus.Unsupported, assessment.previewProbe.status)
    }

    @Test
    fun returnsGradeCWhenFrontCameraCannotBeUsed() {
        val assessment = assessDualCameraCapability(
            DualCameraCapabilityAssessmentInput(
                hasRearCamera = true,
                hasFrontCamera = true,
                apiLevelSupported = true,
                frontCameraUsable = false,
                concurrentPreviewSupported = false,
                preferredConcurrentRecordingSupported = false,
                safeConcurrentRecordingSupported = false,
                currentProfileConcurrentRecordingSupported = false,
                currentProfileDualSafe = true,
            ),
        )

        assertEquals(DeviceCapabilityGrade.C, assessment.grade)
        assertEquals(DualCameraFailureReason.FrontCameraStartupFailed, assessment.failureReason)
    }
}
