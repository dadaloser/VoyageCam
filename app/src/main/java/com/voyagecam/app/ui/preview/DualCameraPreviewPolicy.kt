package com.voyagecam.app.ui.preview

import com.voyagecam.app.core.model.DualCameraCapability

fun shouldShowFrontInsetPreview(
    dualCameraEnabled: Boolean,
    capability: DualCameraCapability,
    @Suppress("UNUSED_PARAMETER")
    isRecording: Boolean,
): Boolean {
    return dualCameraEnabled && capability.isAvailable
}
