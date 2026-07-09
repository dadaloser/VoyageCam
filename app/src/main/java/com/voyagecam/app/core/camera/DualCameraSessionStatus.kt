package com.voyagecam.app.core.camera

import com.voyagecam.app.core.model.DualCameraDiagnostic

data class DualCameraSessionStatus(
    val previewSessionToken: Int? = null,
    val concurrentCameraActive: Boolean = false,
    val recordingActive: Boolean = false,
    val rearPreviewAttached: Boolean = false,
    val frontPreviewAttached: Boolean = false,
    val lastDiagnostic: DualCameraDiagnostic? = null,
)
