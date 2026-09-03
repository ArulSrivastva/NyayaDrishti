package com.sih.model

enum class PackageType {
    BOX_CARTON,
    POUCH,
    BOTTLE,
    CAN,
    BLISTER_PACK,
    CYLINDER,
    OTHER
}

data class PdpEstimate(
    val normalizedBounds: List<Float>?,  // [x, y, width, height] in 0.0-1.0
    val confidence: Float,
    val isAiEstimated: Boolean = true,
    val disclaimer: String = "AI-estimated PDP region; physical measurement required for definitive verification."
)

data class PackageGeometryResult(
    val packageType: PackageType,
    val confidence: Float,
    val estimatedPdpBounds: List<Float>?,  // normalized [x, y, w, h]
    val pdpConfidence: Float,
    val pdpEstimate: PdpEstimate?,
    val surfaceAssociations: Map<String, String>,  // evidenceId -> "FRONT"/"BACK"/"SIDE"
    val isMultiImageRequired: Boolean,
    val warnings: List<String>
)
