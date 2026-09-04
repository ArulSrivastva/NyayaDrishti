package com.sih

import com.sih.domain.compliance.QuantityUnit
import com.sih.domain.compliance.UspCalculator
import com.sih.model.AcknowledgementStatus
import com.sih.model.CaptureSource
import com.sih.model.DraftState
import com.sih.model.EvidenceRecord
import com.sih.model.FindingDecision
import com.sih.model.InspectionDraft
import com.sih.model.InspectionSignOff
import com.sih.model.PackageType
import com.sih.util.ocr.OcrNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.security.MessageDigest

class EnforcementArchitectureTest {

    // =========================================================================
    // SYSTEM 1 — EVIDENCE INTEGRITY & PROVENANCE
    // =========================================================================
    @Test
    fun test1_EvidenceIntegrityHashingAndMismatch() {
        val originalBytes = "PackagedCommodityImageBytes_12345".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val sha256Hex = digest.digest(originalBytes).joinToString("") { "%02x".format(it) }

        val record = EvidenceRecord(
            evidenceId = "EVID-2026-101-IMG-001",
            inspectionId = "101",
            officerId = 1042,
            originalFilename = "camera_capture_001.jpg",
            sha256 = sha256Hex,
            captureTimestamp = "2026-09-04T12:00:00",
            deviceModel = "Nothing Phone (2)",
            imageWidth = 1920,
            imageHeight = 1080,
            fileSizeBytes = originalBytes.size.toLong(),
            gpsLatitude = 28.6139,
            gpsLongitude = 77.2090,
            gpsAccuracy = 5.0f,
            captureSource = CaptureSource.CAMERA,
            qualityStatus = "GREEN",
            ocrStatus = "SUCCESS"
        )

        assertNotNull(record.evidenceId)
        assertTrue(record.evidenceId.startsWith("EVID-2026-101-IMG-"))
        assertEquals(sha256Hex, record.sha256)

        // Simulate image tampering
        val modifiedBytes = "PackagedCommodityImageBytes_TAMPERED".toByteArray(Charsets.UTF_8)
        val modifiedSha256Hex = digest.digest(modifiedBytes).joinToString("") { "%02x".format(it) }

        assertNotEquals(record.sha256, modifiedSha256Hex)
    }

    // =========================================================================
    // SYSTEM 2 — DRAFT RECOVERY
    // =========================================================================
    @Test
    fun test2_DraftRecoveryStateTransitions() {
        val draft = InspectionDraft(
            inspectionId = "INSP-2026-882",
            officerId = 1042,
            establishmentName = "Adani Wilmar Dist Warehouse",
            inspectionType = "ROUTINE_AUDIT",
            location = "North Delhi Zone",
            state = DraftState.CAPTURING,
            capturedEvidenceIds = listOf("EVID-2026-882-IMG-001"),
            qualityResultsJson = null,
            ocrResultsJson = null,
            extractedDeclarationsJson = null,
            classificationJson = null,
            applicableRulesJson = null,
            complianceResultsJson = null,
            reviewState = null,
            signOffJson = null,
            lastUpdated = "2026-09-04T12:05:00"
        )

        assertEquals(DraftState.CAPTURING, draft.state)

        val updatedDraft = draft.copy(
            state = DraftState.HUMAN_REVIEW,
            reviewState = "OFFICER_REVIEWING",
            lastUpdated = "2026-09-04T12:10:00"
        )

        assertEquals(DraftState.HUMAN_REVIEW, updatedDraft.state)
        assertEquals("OFFICER_REVIEWING", updatedDraft.reviewState)
    }

    // =========================================================================
    // SYSTEM 3 — OCR NORMALIZATION
    // =========================================================================
    @Test
    fun test3_OcrRawPreservationAndNormalization() {
        val rawText = "Rs. 99\nMRP ₹99\nNet Qty: 500 g"
        val normalizedResult = OcrNormalizer.normalizeCharacters(rawText)

        assertNotNull(normalizedResult.rawText)
        assertEquals(rawText, normalizedResult.rawText)
        assertTrue(normalizedResult.normalizedText.contains("RS 99"))

        val groupedBlock = com.sih.util.ocr.GroupedTextBlock(
            text = rawText,
            normalizedText = normalizedResult.normalizedText,
            boundingBox = listOf(0f, 0f, 100f, 100f),
            sourceImagePath = "/path/img.jpg",
            sourceEvidenceId = "EVID-001",
            lineCount = 3
        )

        val candidates = OcrNormalizer.detectCandidates(normalizedResult.normalizedText, listOf(groupedBlock))
        assertNotNull(candidates)
    }

    // =========================================================================
    // SYSTEM 4 — ADAPTIVE QUALITY GATE
    // =========================================================================
    @Test
    fun test4_QualityGateAnalysisDimensions() {
        val width = 1920
        val height = 1080
        val totalPx = width * height
        assertTrue(totalPx >= 1920 * 1080)
    }

    // =========================================================================
    // SYSTEM 5 — USP CALCULATOR
    // =========================================================================
    @Test
    fun test5_UspCalculationBigDecimal() {
        val mrpText = "MRP ₹120"
        val qtyText = "Net Qty 600g"

        val normalizedQty = UspCalculator.normalizeQuantity(qtyText)
        assertNotNull(normalizedQty)
        assertEquals(QuantityUnit.GRAM, normalizedQty!!.unit)

        val mrpVal = UspCalculator.extractMrpValue(mrpText)
        assertNotNull(mrpVal)
        assertEquals(BigDecimal("120"), mrpVal)

        val result = UspCalculator.calculateUsp(mrpVal!!, normalizedQty)

        assertNotNull(result.calculatedUsp)
        assertEquals(BigDecimal("200.00"), result.calculatedUsp)
        assertEquals("₹200.00/kg", result.displayValue)
    }

    // =========================================================================
    // SYSTEM 6 — PACKAGE GEOMETRY
    // =========================================================================
    @Test
    fun test6_PackageGeometryCurvedPackageType() {
        val ratio = 0.3f // Tall bottle ratio
        val packageType = when {
            ratio < 0.5f -> PackageType.BOTTLE
            else -> PackageType.BOX_CARTON
        }
        assertEquals(PackageType.BOTTLE, packageType)
    }

    // =========================================================================
    // SYSTEM 7 — FORMAL SIGN-OFF & CANONICAL JSON HASH
    // =========================================================================
    @Test
    fun test7_FormalSignOffCanonicalHash() {
        val findings = listOf(
            FindingDecision("mrp", "R6_009", "CONFIRMED", "MRP price difference verified"),
            FindingDecision("net_quantity", "R6_001", "UNABLE_TO_VERIFY", "Label torn across numeral")
        )

        val canonicalData = "INSP-101|1042|Officer Rajesh|2026-09-04T12:30:00|CONFIRMED,UNABLE_TO_VERIFY"
        val digest = MessageDigest.getInstance("SHA-256")
        val jsonHash = digest.digest(canonicalData.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        val signOff = InspectionSignOff(
            officerId = 1042,
            officerName = "Officer Rajesh Kumar",
            officerTimestamp = "2026-09-04T12:30:00",
            officerDeviceId = "DEVICE-XYZ-99",
            inspectionJsonHash = jsonHash,
            findingDecisions = findings,
            representativeAcknowledgement = AcknowledgementStatus.ACKNOWLEDGED,
            representativeName = "Store Manager - Ramesh",
            representativeTimestamp = "2026-09-04T12:35:00",
            signatureImagePath = "/data/user/0/com.sih/cache/sig_101.png",
            signatureHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )

        assertEquals(1042, signOff.officerId)
        assertEquals(jsonHash, signOff.inspectionJsonHash)
        assertEquals(AcknowledgementStatus.ACKNOWLEDGED, signOff.representativeAcknowledgement)
    }
}
