package com.voyagecam.app.feature.recording

import com.voyagecam.app.core.camera.RecordingSegmentTransitionStats
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingPerformanceGuardTest {
    @Test
    fun downgradesDualCameraWhenThermalSeverityIsSevere() {
        val decision = RecordingPerformanceGuard.evaluate(
            sample = RecordingPerformanceSample(
                batteryPercent = 80,
                charging = true,
                thermalSeverity = ThermalSeverity.Severe,
            ),
            dualCameraActive = true,
        )

        assertTrue(decision.shouldDowngradeDualCamera)
        assertTrue(decision.summary?.contains("过热") == true)
    }

    @Test
    fun ignoresThermalDowngradeWhenThermalGuardDisabled() {
        val decision = RecordingPerformanceGuard.evaluate(
            sample = RecordingPerformanceSample(
                batteryPercent = 80,
                charging = true,
                thermalSeverity = ThermalSeverity.Severe,
            ),
            dualCameraActive = true,
            policy = RecordingPerformancePolicy(thermalGuardEnabled = false),
        )

        assertFalse(decision.shouldDowngradeDualCamera)
        assertNull(decision.summary)
    }

    @Test
    fun downgradesDualCameraWhenBatteryIsLowAndNotCharging() {
        val decision = RecordingPerformanceGuard.evaluate(
            sample = RecordingPerformanceSample(
                batteryPercent = 12,
                charging = false,
                thermalSeverity = ThermalSeverity.None,
            ),
            dualCameraActive = true,
        )

        assertTrue(decision.shouldDowngradeDualCamera)
        assertTrue(decision.summary?.contains("电量") == true)
    }

    @Test
    fun ignoresLowBatteryDowngradeWhenBatteryGuardDisabled() {
        val decision = RecordingPerformanceGuard.evaluate(
            sample = RecordingPerformanceSample(
                batteryPercent = 12,
                charging = false,
                thermalSeverity = ThermalSeverity.None,
            ),
            dualCameraActive = true,
            policy = RecordingPerformancePolicy(lowBatteryGuardEnabled = false),
        )

        assertFalse(decision.shouldDowngradeDualCamera)
        assertNull(decision.summary)
    }

    @Test
    fun keepsDualCameraWhenLowBatteryDeviceIsCharging() {
        val decision = RecordingPerformanceGuard.evaluate(
            sample = RecordingPerformanceSample(
                batteryPercent = 12,
                charging = true,
                thermalSeverity = ThermalSeverity.None,
            ),
            dualCameraActive = true,
        )

        assertFalse(decision.shouldDowngradeDualCamera)
    }

    @Test
    fun downgradesDualCameraWhenSegmentTransitionIsSlow() {
        val decision = RecordingPerformanceGuard.evaluate(
            sample = RecordingPerformanceSample(
                batteryPercent = 80,
                charging = true,
                thermalSeverity = ThermalSeverity.None,
                transitionStats = RecordingSegmentTransitionStats(
                    completedSegmentIndex = 3,
                    stopToFinalizeMillis = 3_200L,
                    finalizeToNextStartMillis = 300L,
                ),
            ),
            dualCameraActive = true,
        )

        assertTrue(decision.shouldDowngradeDualCamera)
        assertTrue(decision.summary?.contains("分段") == true)
    }

    @Test
    fun ignoresSlowSegmentDowngradeWhenSegmentGuardDisabled() {
        val decision = RecordingPerformanceGuard.evaluate(
            sample = RecordingPerformanceSample(
                batteryPercent = 80,
                charging = true,
                thermalSeverity = ThermalSeverity.None,
                transitionStats = RecordingSegmentTransitionStats(
                    completedSegmentIndex = 3,
                    stopToFinalizeMillis = 3_200L,
                    finalizeToNextStartMillis = 300L,
                ),
            ),
            dualCameraActive = true,
            policy = RecordingPerformancePolicy(slowSegmentGuardEnabled = false),
        )

        assertFalse(decision.shouldDowngradeDualCamera)
        assertNull(decision.summary)
    }

    @Test
    fun reportsWarningWithoutDowngradeWhenDualCameraAlreadyInactive() {
        val decision = RecordingPerformanceGuard.evaluate(
            sample = RecordingPerformanceSample(
                batteryPercent = 12,
                charging = false,
                thermalSeverity = ThermalSeverity.None,
            ),
            dualCameraActive = false,
        )

        assertFalse(decision.shouldDowngradeDualCamera)
        assertTrue(decision.summary?.contains("电量") == true)
    }
}
