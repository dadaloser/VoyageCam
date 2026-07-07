package com.voyagecam.app.core.model

enum class CollisionSensitivity(
    val label: String,
    val thresholdG: Float,
) {
    Low(label = "低", thresholdG = 3.2f),
    Medium(label = "中", thresholdG = 2.4f),
    High(label = "高", thresholdG = 1.8f),
}
