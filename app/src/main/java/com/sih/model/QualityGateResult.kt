package com.sih.model

enum class QualityTier {
    GREEN,
    AMBER,
    RED
}

data class QualityGateResult(
    val overallScore: Float,
    val blurScore: Float,
    val glareScore: Float,
    val resolutionScore: Float,
    val orientationScore: Float,
    val textVisibilityScore: Float?,
    val status: QualityTier,
    val regionScores: Map<String, Float>,
    val warnings: List<String>,
    val canOverride: Boolean
)
