package com.voyagecam.app.feature.recording

import com.voyagecam.app.core.camera.RecordingSegmentTransitionStats

data class RecordingPerformanceSample(
    val batteryPercent: Int?,
    val charging: Boolean,
    val thermalSeverity: ThermalSeverity,
    val transitionStats: RecordingSegmentTransitionStats? = null,
)

data class RecordingPerformanceDecision(
    val shouldDowngradeDualCamera: Boolean,
    val summary: String?,
)

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
                sample.thermalSeverity >= ThermalSeverity.Severe -> "设备过热，需要关闭前摄以降低发热"
            policy.lowBatteryGuardEnabled &&
                sample.batteryPercent != null &&
                !sample.charging &&
                sample.batteryPercent <= LOW_BATTERY_DOWNGRADE_PERCENT ->
                "电量低于 $LOW_BATTERY_DOWNGRADE_PERCENT%，需要关闭前摄以延长后摄录制"
            policy.slowSegmentGuardEnabled &&
                sample.transitionStats?.isSlowTransition() == true ->
                "分段切换耗时过高，需要关闭前摄以降低编码压力"
            else -> null
        }
        if (downgradeReason != null) {
            return RecordingPerformanceDecision(
                shouldDowngradeDualCamera = dualCameraActive,
                summary = downgradeReason,
            )
        }

        val warning = when {
            policy.thermalGuardEnabled &&
                sample.thermalSeverity >= ThermalSeverity.Moderate -> "设备温度升高，继续双摄可能触发自动降级"
            policy.lowBatteryGuardEnabled &&
                sample.batteryPercent != null &&
                !sample.charging &&
                sample.batteryPercent <= LOW_BATTERY_WARNING_PERCENT ->
                "电量低于 $LOW_BATTERY_WARNING_PERCENT%，建议连接电源"
            else -> null
        }
        return RecordingPerformanceDecision(
            shouldDowngradeDualCamera = false,
            summary = warning,
        )
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
