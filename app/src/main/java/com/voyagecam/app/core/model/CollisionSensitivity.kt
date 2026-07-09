package com.voyagecam.app.core.model

enum class CollisionSensitivity(
    val thresholdG: Float,
) {
    Low(thresholdG = 3.2f),
    Medium(thresholdG = 2.4f),
    High(thresholdG = 1.8f),
}
