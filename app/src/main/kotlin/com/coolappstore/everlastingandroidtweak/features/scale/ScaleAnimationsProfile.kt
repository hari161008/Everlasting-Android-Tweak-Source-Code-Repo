package com.coolappstore.everlastingandroidtweak.features.scale

data class ScaleAnimationsProfile(
    val fontScale: Float = 1.0f,
    val animatorDurationScale: Float = 1.0f,
    val transitionAnimationScale: Float = 1.0f,
    val windowAnimationScale: Float = 1.0f,
    val smallestWidth: Int = 360,
    val autoRotateEnabled: Boolean = false,
    val screenTimeoutMs: Long = 30000L
)
