package com.sih.model

enum class CaptureSource {
    CAMERA,
    GALLERY,
    DOCUMENT
}

data class EvidenceRecord(
    val evidenceId: String,          // EVID-{year}-{inspId}-IMG-{seq}
    val inspectionId: String,
    val officerId: Int,
    val originalFilename: String?,
    val sha256: String,
    val captureTimestamp: String,     // ISO 8601
    val deviceModel: String?,
    val imageWidth: Int?,
    val imageHeight: Int?,
    val fileSizeBytes: Long?,
    val gpsLatitude: Double?,
    val gpsLongitude: Double?,
    val gpsAccuracy: Float?,
    val captureSource: CaptureSource,
    val qualityStatus: String?,      // GREEN/AMBER/RED or null
    val ocrStatus: String?           // PENDING/COMPLETED/FAILED or null
) {
    companion object {
        fun generateId(year: Int, inspectionId: String, sequence: Int): String {
            return "EVID-$year-$inspectionId-IMG-%03d".format(sequence)
        }
    }
}

data class ViolationEvidence(
    val evidenceId: String,          // References EvidenceRecord.evidenceId
    val originalHash: String,        // SHA-256 of original image
    val cropHash: String?,           // SHA-256 of violation crop
    val boundingBox: List<Float>?,   // [left, top, right, bottom]
    val ocrText: String?,            // Raw OCR text in the violation region
    val normalizedText: String?,     // Normalized OCR text
    val ruleId: String?,             // e.g. R6_007
    val violationReason: String?,
    val confidence: Float
)
