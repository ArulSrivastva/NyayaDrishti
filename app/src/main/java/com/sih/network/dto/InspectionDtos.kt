package com.sih.network.dto

import com.google.gson.annotations.SerializedName
import com.sih.domain.classification.ProductClassification
import com.sih.domain.classification.ApplicableRuleSet

data class ProductDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("manufacturer") val manufacturer: String? = null,
    @SerializedName("packer") val packer: String? = null,
    @SerializedName("importer") val importer: String? = null,
    @SerializedName("net_quantity") val netQuantity: String? = null,
    @SerializedName("mrp") val mrp: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("sub_category") val subCategory: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class DeclarationDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("value") val value: String? = null,
    @SerializedName("confidence") val confidence: Float? = null,
    @SerializedName("bbox") val bbox: List<Float>? = null,
    @SerializedName("present") val present: Boolean? = null,
    @SerializedName("image_path") val imagePath: String? = null
)

data class ViolationDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("rule_id") val ruleId: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("severity") val severity: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("confidence") val confidence: Float? = null
)

data class EvidenceDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("violation_id") val violationId: Int? = null,
    @SerializedName("image_path") val imagePath: String? = null,
    @SerializedName("bbox") val bbox: List<Float>? = null,
    @SerializedName("confidence") val confidence: Float? = null,
    @SerializedName("sha256") val sha256: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("evidence_id") val evidenceId: String? = null,
    @SerializedName("original_hash") val originalHash: String? = null,
    @SerializedName("device_model") val deviceModel: String? = null,
    @SerializedName("gps_latitude") val gpsLatitude: Double? = null,
    @SerializedName("gps_longitude") val gpsLongitude: Double? = null,
    @SerializedName("gps_accuracy") val gpsAccuracy: Float? = null
)

data class RiskDto(
    @SerializedName("level") val level: String? = null,
    @SerializedName("reason") val reason: String? = null
)

data class ConfidenceDto(
    @SerializedName("overall") val overall: Float? = null,
    @SerializedName("verdict") val verdict: String? = null
)

data class ComplianceDto(
    @SerializedName("status") val status: String? = null
)

data class InspectorDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("decision") val decision: String? = null,
    @SerializedName("reason") val reason: String? = null
)

enum class QualityStatus {
    @SerializedName("ACCEPT") ACCEPT,
    @SerializedName("WARNING") WARNING,
    @SerializedName("REJECT") REJECT
}

data class DefectRegion(
    @SerializedName("type") val type: String = "GLARE",
    @SerializedName("bbox") val bbox: List<Float> = emptyList(), // [x1, y1, x2, y2] normalized (0..100) or pixel
    @SerializedName("description") val description: String = ""
)

data class ImageQualityResult(
    @SerializedName("quality_score") val qualityScore: Float = 0.95f,
    @SerializedName("blur_score") val blurScore: Float = 0.05f,
    @SerializedName("glare_score") val glareScore: Float = 0.02f,
    @SerializedName("resolution_score") val resolutionScore: Float = 0.98f,
    @SerializedName("orientation_score") val orientationScore: Float = 1.0f,
    @SerializedName("text_visibility_score") val textVisibilityScore: Float = 0.94f,

    @SerializedName("blur_status") val blurStatus: QualityStatus = QualityStatus.ACCEPT,
    @SerializedName("glare_status") val glareStatus: QualityStatus = QualityStatus.ACCEPT,
    @SerializedName("resolution_status") val resolutionStatus: QualityStatus = QualityStatus.ACCEPT,
    @SerializedName("orientation_status") val orientationStatus: QualityStatus = QualityStatus.ACCEPT,
    @SerializedName("text_visibility_status") val textVisibilityStatus: QualityStatus = QualityStatus.ACCEPT,

    @SerializedName("overall_status") val overallStatus: QualityStatus = QualityStatus.ACCEPT,
    @SerializedName("rejection_reason") val rejectionReason: String? = null,
    @SerializedName("defect_regions") val defectRegions: List<DefectRegion> = emptyList(),

    // Audit Trail for Inspector Override (Section 65B compliance)
    @SerializedName("quality_override") val qualityOverride: Boolean = false,
    @SerializedName("override_reason") val overrideReason: String? = null,
    @SerializedName("override_officer") val overrideOfficer: String? = null,
    @SerializedName("override_timestamp") val overrideTimestamp: String? = null
)

data class FullInspectionResponse(
    @SerializedName("inspection_id") val inspectionId: Int? = null,
    @SerializedName("product") val product: ProductDto? = null,
    @SerializedName("declarations") val declarations: List<DeclarationDto>? = null,
    @SerializedName("violations") val violations: List<ViolationDto>? = null,
    @SerializedName("evidence") val evidence: List<EvidenceDto>? = null,
    @SerializedName("evidences") val evidencesList: List<EvidenceDto>? = null,
    @SerializedName("risk") val risk: RiskDto? = null,
    @SerializedName("confidence") val confidence: ConfidenceDto? = null,
    @SerializedName("compliance") val compliance: ComplianceDto? = null,
    @SerializedName("inspector") val inspector: InspectorDto? = null,
    @SerializedName("image_path") val imagePath: String? = null,
    @SerializedName("image_quality") val imageQuality: ImageQualityResult? = null,
    @SerializedName("classification") val classification: ProductClassification? = null,
    @SerializedName("applicable_rule_set") val applicableRuleSet: ApplicableRuleSet? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("evidence_records") val evidenceRecords: List<com.sih.model.EvidenceRecord>? = null,
    @SerializedName("package_geometry") val packageGeometry: com.sih.model.PackageGeometryResult? = null,
    @SerializedName("sign_off") val signOff: com.sih.model.InspectionSignOff? = null,
    @SerializedName("inspection_state") val inspectionState: String? = null,
    @SerializedName("establishment_name") val establishmentName: String? = null,
    @SerializedName("inspection_type") val inspectionType: String? = null,
    @SerializedName("location") val location: String? = null
)

data class InspectionDetailOutDto(
    @SerializedName("id") val id: Int,
    @SerializedName("product_id") val productId: Int? = null,
    @SerializedName("inspector_id") val inspectorId: Int? = null,
    @SerializedName("image_path") val imagePath: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("compliance_status") val complianceStatus: String? = null,
    @SerializedName("risk_level") val riskLevel: String? = null,
    @SerializedName("risk_reason") val riskReason: String? = null,
    @SerializedName("overall_confidence") val overallConfidence: Float? = null,
    @SerializedName("ai_verdict") val aiVerdict: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("inspector_decision") val inspectorDecision: String? = null,
    @SerializedName("decision_reason") val decisionReason: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null,
    @SerializedName("declarations") val declarations: List<DeclarationDto> = emptyList(),
    @SerializedName("violations") val violations: List<ViolationDto> = emptyList(),
    @SerializedName("evidences") val evidences: List<EvidenceDto> = emptyList(),
    @SerializedName("image_quality") val imageQuality: ImageQualityResult? = null,
    @SerializedName("classification") val classification: ProductClassification? = null,
    @SerializedName("applicable_rule_set") val applicableRuleSet: ApplicableRuleSet? = null
)

data class DecisionRequestDto(
    @SerializedName("decision") val decision: String,
    @SerializedName("reason") val reason: String? = null
)

data class ProductHistoryResponse(
    @SerializedName("product_id") val productId: Int? = null,
    @SerializedName("product_name") val productName: String? = null,
    @SerializedName("total_inspections") val totalInspections: Int? = null,
    @SerializedName("total_violations") val totalViolations: Int? = null,
    @SerializedName("risk_level") val riskLevel: String? = null,
    @SerializedName("risk_reason") val riskReason: String? = null,
    @SerializedName("history") val history: List<FullInspectionResponse>? = null
)

data class ReportResponseDto(
    @SerializedName("report_path") val reportPath: String? = null,
    @SerializedName("report_url") val reportUrl: String? = null
)
