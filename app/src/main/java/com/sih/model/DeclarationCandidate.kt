package com.sih.model

enum class DeclarationType {
    MRP,
    NET_QUANTITY,
    MANUFACTURER,
    PACKER,
    IMPORTER,
    DATE,
    BATCH,
    CUSTOMER_CARE,
    COUNTRY_OF_ORIGIN,
    EXPIRY,
    BEST_BEFORE,
    COMMODITY_NAME,
    UNIT_SALE_PRICE,
    UNKNOWN
}

enum class ConfidenceLevel {
    HIGH,    // > 0.90
    MEDIUM,  // 0.70 - 0.89
    REVIEW;  // < 0.70

    companion object {
        fun from(confidence: Float): ConfidenceLevel = when {
            confidence > 0.90f -> HIGH
            confidence >= 0.70f -> MEDIUM
            else -> REVIEW
        }
    }
}

data class DeclarationCandidate(
    val type: DeclarationType,
    val rawText: String,
    val normalizedText: String,
    val confidence: Float,
    val confidenceLevel: ConfidenceLevel,
    val boundingBox: List<Float>?,
    val sourceEvidenceId: String?,
    val sourceImagePath: String?,
    val rawOcr: String? = null,
    val enhancedOcr: String? = null,
    val ambiguityReason: String? = null
)
