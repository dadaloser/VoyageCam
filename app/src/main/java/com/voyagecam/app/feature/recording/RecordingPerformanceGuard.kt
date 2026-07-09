package com.voyagecam.app.feature.recording

import android.content.Context
import com.voyagecam.app.R
import com.voyagecam.app.core.camera.RecordingSegmentTransitionStats

data class RecordingPerformanceSample(
    val batteryPercent: Int?,
    val charging: Boolean,
    val thermalSeverity: ThermalSeverity,
    val transitionStats: RecordingSegmentTransitionStats? = null,
)

data class RecordingPerformanceDecision(
    val shouldDowngradeDualCamera: Boolean,
    val message: RecordingPerformanceMessage?,
)

enum class RecordingPerformanceMessage {
    ThermalDowngrade,
    LowBatteryDowngrade,
    SlowSegmentDowngrade,
    ThermalWarning,
    LowBatteryWarning,
}

data class RecordingPerformancePolicy(
    val thermalGuardEnabled: Boolean = true,
    val lowBatteryGuardEnabled: Boolean = true,
    val slowSegmentGuardEnabled: Boolean = true,
)

enum class ThermalSeverity {
    None,
    Light,
    Moderate,
    Severe,
    Critical,
}

object RecordingPerformanceGuard {
    fun evaluate(
        sample: RecordingPerformanceSample,
        dualCameraActive: Boolean,
        policy: RecordingPerformancePolicy = RecordingPerformancePolicy(),
    ): RecordingPerformanceDecision {
        val downgradeReason = when {
            policy.thermalGuardEnabled &&
                sample.thermalSeverity >= ThermalSeverity.Severe -> RecordingPerformanceMessage.ThermalDowngrade
            policy.lowBatteryGuardEnabled &&
                sample.batteryPercent != null &&
                !sample.charging &&
                sample.batteryPercent <= LOW_BATTERY_DOWNGRADE_PERCENT ->
                RecordingPerformanceMessage.LowBatteryDowngrade
            policy.slowSegmentGuardEnabled &&
                sample.transitionStats?.isSlowTransition() == true ->
                RecordingPerformanceMessage.SlowSegmentDowngrade
            else -> null
        }
        if (downgradeReason != null) {
            return RecordingPerformanceDecision(
                shouldDowngradeDualCamera = dualCameraActive,
                message = downgradeReason,
            )
        }

        val warning = when {
            policy.thermalGuardEnabled &&
                sample.thermalSeverity >= ThermalSeverity.Moderate -> RecordingPerformanceMessage.ThermalWarning
            policy.lowBatteryGuardEnabled &&
                sample.batteryPercent != null &&
                !sample.charging &&
                sample.batteryPercent <= LOW_BATTERY_WARNING_PERCENT ->
                RecordingPerformanceMessage.LowBatteryWarning
            else -> null
        }
        return RecordingPerformanceDecision(
            shouldDowngradeDualCamera = false,
            message = warning,
        )
    }

    fun summary(context: Context, decision: RecordingPerformanceDecision): String? {
        return decision.message?.let { message ->
            when (message) {
                RecordingPerformanceMessage.ThermalDowngrade -> context.getString(R.string.recording_guard_thermal_downgrade)
                RecordingPerformanceMessage.LowBatteryDowngrade -> {
                    context.getString(R.string.recording_guard_battery_downgrade, LOW_BATTERY_DOWNGRADE_PERCENT)
                }

                RecordingPerformanceMessage.SlowSegmentDowngrade -> context.getString(R.string.recording_guard_segment_downgrade)
                RecordingPerformanceMessage.ThermalWarning -> context.getString(R.string.recording_guard_thermal_warning)
                RecordingPerformanceMessage.LowBatteryWarning -> {
                    context.getString(R.string.recording_guard_battery_warning, LOW_BATTERY_WARNING_PERCENT)
                }
            }
        }
    }

    private fun RecordingSegmentTransitionStats.isSlowTransition(): Boolean {
        return finalizeToNextStartMillis >= SLOW_FINALIZE_TO_START_MS ||
            (stopToFinalizeMillis ?: 0L) >= SLOW_STOP_TO_FINALIZE_MS
    }

    private const val LOW_BATTERY_DOWNGRADE_PERCENT = 15
    private const val LOW_BATTERY_WARNING_PERCENT = 25
    private const val SLOW_FINALIZE_TO_START_MS = 1_500L
    private const val SLOW_STOP_TO_FINALIZE_MS = 3_000L
}
