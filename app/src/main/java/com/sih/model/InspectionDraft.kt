package com.sih.model

enum class DraftState {
    CREATED,
    CAPTURING,
    QUALITY_CHECK,
    OCR_PROCESSING,
    CLASSIFICATION,
    RULE_EVALUATION,
    HUMAN_REVIEW,
    SIGN_OFF,
    COMPLETED,
    ABANDONED
}

data class InspectionDraft(
    val inspectionId: String,
    val officerId: Int,
    val establishmentName: String?,
    val inspectionType: String?,
    val location: String?,
    val state: DraftState,
    val capturedEvidenceIds: List<String>,
    val qualityResultsJson: String?,
    val ocrResultsJson: String?,
    val extractedDeclarationsJson: String?,
    val classificationJson: String?,
    val applicableRulesJson: String?,
    val complianceResultsJson: String?,
    val reviewState: String?,
    val signOffJson: String?,
    val lastUpdated: String    // ISO 8601
)
