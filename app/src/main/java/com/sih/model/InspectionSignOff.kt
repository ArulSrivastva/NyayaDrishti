package com.sih.model

enum class AcknowledgementStatus {
    ACKNOWLEDGED,
    REFUSED_TO_SIGN,
    NOT_PRESENT,
    NOT_APPLICABLE
}

data class FindingDecision(
    val declarationType: String,
    val ruleId: String?,
    val decision: String,   // CONFIRMED, REJECTED, UNABLE_TO_VERIFY
    val reason: String?
)

data class InspectionSignOff(
    val officerId: Int,
    val officerName: String,
    val officerTimestamp: String,      // ISO 8601
    val officerDeviceId: String,
    val inspectionJsonHash: String,    // SHA-256 of canonical inspection JSON
    val findingDecisions: List<FindingDecision>,
    val representativeAcknowledgement: AcknowledgementStatus,
    val representativeName: String?,
    val representativeTimestamp: String?,
    val signatureImagePath: String?,   // PNG path of touch signature, nullable
    val signatureHash: String?         // SHA-256 of signature PNG, nullable
)
