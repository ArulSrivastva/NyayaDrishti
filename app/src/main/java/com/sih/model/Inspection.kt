package com.sih.model

import java.time.LocalDateTime

data class Inspection(
    val id: String,
    val productName: String,
    val date: LocalDateTime,
    val status: ComplianceStatus,
    val violationCount: Int,
    val riskLevel: RiskLevel,
    val productId: String
)

enum class ComplianceStatus {
    COMPLIANT,
    NON_COMPLIANT,
    NEEDS_REVIEW
}

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}
