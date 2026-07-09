package com.voyagecam.app.data.settings

fun recordingModeLabel(
    recordingModeAuto: Boolean,
    dualCameraActive: Boolean,
): String {
    return when {
        recordingModeAuto && dualCameraActive -> "自动模式 · 双摄"
        recordingModeAuto -> "自动模式 · 后摄"
        else -> "仅后摄"
    }
}

fun recordingModeDescription(
    recordingModeAuto: Boolean,
    dualCameraSupported: Boolean,
): String {
    return when {
        recordingModeAuto && dualCameraSupported ->
            "支持时使用双摄；不支持或降级时自动切回后摄。"
        recordingModeAuto ->
            "当前设备仅支持后摄录制。"
        else ->
            "始终使用后摄，功耗更低也更稳定。"
    }
}
