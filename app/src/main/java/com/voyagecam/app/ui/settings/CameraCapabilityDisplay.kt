package com.voyagecam.app.ui.settings

import com.voyagecam.app.core.model.DualCameraSwitchState

fun DualCameraSwitchState.asLabel(): String {
    return when (this) {
        DualCameraSwitchState.Checking -> "检测中"
        DualCameraSwitchState.AvailableOff -> "可用未开启"
        DualCameraSwitchState.AvailableOn -> "可用已开启"
        DualCameraSwitchState.Unavailable -> "不可用"
        DualCameraSwitchState.CheckFailed -> "检测失败"
    }
}
