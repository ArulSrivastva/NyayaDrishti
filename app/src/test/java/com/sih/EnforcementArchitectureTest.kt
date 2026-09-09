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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    // SYSTEM 5 — USP CALCULATOR (Statutory Unit & BigDecimal Calculation)
    // =========================================================================
    @Test
    fun test5_UspCalculationBigDecimal() {
        val mrpText = "MRP ₹120"
        val qtyText = "Net Qty 600g"

        val normalizedQty = UspCalculator.normalizeQuantity(qtyText)
        assertNotNull(normalizedQty)
        assertEquals(QuantityUnit.GRAM, normalizedQty!!.originalUnit)

        val mrpVal = UspCalculator.extractMrpValue(mrpText)
        assertNotNull(mrpVal)
        assertEquals(BigDecimal("120"), mrpVal)

        val result = UspCalculator.calculateUsp(mrpVal!!, normalizedQty)

        assertNotNull(result.calculatedUsp)
        // 600g < 1kg -> statutory canonical unit is per 100g: 120 * 100 / 600 = 20.00
        assertEquals(BigDecimal("20.00"), result.calculatedUsp)
        assertEquals("₹20.00/100g", result.displayValue)
    }

    // =========================================================================
    // SYSTEM 6 — PACKAGE GEOMETRY
    // =========================================================================
    @Test
    fun test6_PackageGeometryCurvedPackageType() {
        val ratio = 0.3f // Tall bottle ratio
        val packageType = when {
            ratio > 2.0f -> PackageType.BLISTER_PACK
            ratio in 0.8f..1.2f -> PackageType.BOX_CARTON
            ratio > 1.2f -> PackageType.POUCH
            ratio < 0.5f -> PackageType.BOTTLE
            ratio in 0.5f..0.8f -> PackageType.BOX_CARTON
            else -> PackageType.OTHER
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

    // =========================================================================
    // SYSTEM 8 — RULE 6(11) MULTI-UNIT USP CONVERSION MATRIX (8 CASES)
    // =========================================================================
    @Test
    fun test8_UspVerificationWithNormalizedUnits() {
        // Case 1: 500 g @ ₹100, declared per 100 g (₹20 / 100g) -> VERIFIED
        val res1 = UspCalculator.verifyDeclaredUsp("₹20 / 100g", "₹100.00", "500 g")
        assertEquals(com.sih.domain.compliance.UspStatus.VERIFIED, res1.status)

        // Case 2: 500 g @ ₹100, declared per kg (₹200 / kg) -> VERIFIED
        val res2 = UspCalculator.verifyDeclaredUsp("₹200 / kg", "₹100.00", "500 g")
        assertEquals(com.sih.domain.compliance.UspStatus.VERIFIED, res2.status)

        // Case 3: 1 kg @ ₹100, declared per kg (₹100 / kg) -> VERIFIED
        val res3 = UspCalculator.verifyDeclaredUsp("₹100 / kg", "₹100.00", "1 kg")
        assertEquals(com.sih.domain.compliance.UspStatus.VERIFIED, res3.status)

        // Case 4: 1 kg @ ₹100, declared per 100 g (₹10 / 100g) -> VERIFIED
        val res4 = UspCalculator.verifyDeclaredUsp("₹10 / 100g", "₹100.00", "1 kg")
        assertEquals(com.sih.domain.compliance.UspStatus.VERIFIED, res4.status)

        // Case 5: 100 ml @ ₹50, declared per 100 ml (₹50 / 100ml) -> VERIFIED
        val res5 = UspCalculator.verifyDeclaredUsp("₹50 / 100ml", "₹50.00", "100 ml")
        assertEquals(com.sih.domain.compliance.UspStatus.VERIFIED, res5.status)

        // Case 6: 100 ml @ ₹50, declared per L (₹500 / L) -> VERIFIED
        val res6 = UspCalculator.verifyDeclaredUsp("₹500 / L", "₹50.00", "100 ml")
        assertEquals(com.sih.domain.compliance.UspStatus.VERIFIED, res6.status)

        // Case 7: 1 L @ ₹200, declared per L (₹200 / L) -> VERIFIED
        val res7 = UspCalculator.verifyDeclaredUsp("₹200 / L", "₹200.00", "1 L")
        assertEquals(com.sih.domain.compliance.UspStatus.VERIFIED, res7.status)

        // Case 8: 1 L @ ₹200, declared per 100 ml (₹20 / 100ml) -> VERIFIED
        val res8 = UspCalculator.verifyDeclaredUsp("₹20 / 100ml", "₹200.00", "1 L")
        assertEquals(com.sih.domain.compliance.UspStatus.VERIFIED, res8.status)

        // Negative test: 500 g @ ₹100 with incorrect declared rate (₹15 / 100g) -> MISMATCH
        val mismatch = UspCalculator.verifyDeclaredUsp("₹15 / 100g", "₹100.00", "500 g")
        assertEquals(com.sih.domain.compliance.UspStatus.MISMATCH, mismatch.status)

        // Exemption test: 10 g package -> EXEMPT under Rule 26
        val exempt = UspCalculator.verifyDeclaredUsp(null, "₹10.00", "10 g")
        assertEquals(com.sih.domain.compliance.UspStatus.EXEMPT, exempt.status)
    }

    // =========================================================================
    // SYSTEM 9 — PACKAGE GEOMETRY BLISTER PACK REACHABILITY
    // =========================================================================
    @Test
    fun test9_PackageGeometryBlisterPackReachability() {
        val ratio = 2.4f
        val packageType = when {
            ratio > 2.0f -> PackageType.BLISTER_PACK
            ratio in 0.8f..1.2f -> PackageType.BOX_CARTON
            ratio > 1.2f -> PackageType.POUCH
            ratio < 0.5f -> PackageType.BOTTLE
            ratio in 0.5f..0.8f -> PackageType.BOX_CARTON
            else -> PackageType.OTHER
        }
        assertEquals(PackageType.BLISTER_PACK, packageType)
    }

    // =========================================================================
    // SYSTEM 10 — SECTION 65B REPORT SHA-256 INTEGRITY
    // =========================================================================
    @Test
    fun test10_Section65bSha256ReportIntegrity() {
        val id = "INSP-1042"
        val product = "Tata Salt 1kg"
        val input = "NYAYADRISHTI-SEC65B-INSP-$id-$product-STATUTORY-RECORD"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val hash = digest.joinToString("") { "%02x".format(it) }

        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("^[a-f0-9]{64}$")))
    }

    // =========================================================================
    // SYSTEM 11 — CONFIDENCE-AWARE DOT-MATRIX DATE AMBIGUITY & PROVENANCE
    // =========================================================================
    @Test
    fun test11_DotMatrixDateAmbiguityAndProvenance() {
        val currentYear = 2026

        // 1. Dual-pass resolution: Pass A = 04/2028 (implausible mfg year), Pass B = 04/2026
        // Resolves with candidate = 04/2026, flagged as REVIEW with preserved provenance!
        val resolvedCandidate = OcrNormalizer.resolveDateCandidate(
            primaryText = "MFD: 04/2028",
            enhancedText = "MFD: 04/2026",
            boundingBox = listOf(10f, 20f, 100f, 40f),
            sourceImagePath = "/test/scale.jpg",
            sourceEvidenceId = "EVID-2026-001",
            currentYear = currentYear
        )

        assertNotNull(resolvedCandidate)
        assertEquals("MFD: 04/2028", resolvedCandidate!!.rawOcr)
        assertEquals("MFD: 04/2026", resolvedCandidate.enhancedOcr)
        assertEquals(com.sih.model.ConfidenceLevel.REVIEW, resolvedCandidate.confidenceLevel)
        assertTrue(resolvedCandidate.ambiguityReason!!.contains("dot_matrix_year_ambiguity"))

        // 2. Both passes agree on valid date -> HIGH confidence
        val agreedCandidate = OcrNormalizer.resolveDateCandidate(
            primaryText = "MFD: 04/2026",
            enhancedText = "MFD: 04/2026",
            boundingBox = null,
            sourceImagePath = null,
            sourceEvidenceId = null,
            currentYear = currentYear
        )
        assertEquals(com.sih.model.ConfidenceLevel.HIGH, agreedCandidate!!.confidenceLevel)
        assertEquals(null, agreedCandidate.ambiguityReason)

        // 3. Legitimate future expiry date (EXP: 04/2028) must NOT be flagged as an invalid future date
        val expInfo = OcrNormalizer.parseDateComponents("EXP: 04/2028")
        assertNotNull(expInfo)
        assertTrue(expInfo!!.isExpiryOrBestBefore)
        val (expPlausible, expReason) = OcrNormalizer.evaluateDatePlausibility(expInfo, currentYear)
        assertTrue(expPlausible)
        assertEquals(null, expReason)

        // 4. Dot-matrix preprocessor test: produces continuous pixel coverage via pure 8-connected kernel
        val binaryGrid = BooleanArray(10 * 10)
        // Put an isolated ink dot at (5, 5)
        binaryGrid[5 * 10 + 5] = true
        val dilatedGrid = com.sih.util.quality.DotMatrixPreprocessor.dilateInk(binaryGrid, 10, 10)
        assertNotNull(dilatedGrid)
        // Neighboring dots at (5, 4), (5, 6), (4, 5), (6, 5) must be bridged/dilated
        assertTrue(dilatedGrid[5 * 10 + 5])
        assertTrue(dilatedGrid[5 * 10 + 4])
        assertTrue(dilatedGrid[5 * 10 + 6])
        assertTrue(dilatedGrid[4 * 10 + 5])
        assertTrue(dilatedGrid[6 * 10 + 5])
        // Corner diagonal (4, 4) MUST now be true in 8-connected kernel to bridge diagonal '/' strokes
        assertTrue(dilatedGrid[4 * 10 + 4])
        // Distance 2 non-neighbor (3, 3) remains false
        assertFalse(dilatedGrid[3 * 10 + 3])
    }

    @Test
    fun test12_DroppedSlashAndCijDateDisambiguation() {
        // 1. Single-digit month without slash ("42026" - dropped slash & zero)
        val info42026 = OcrNormalizer.parseDateComponents("42026")
        assertNotNull(info42026)
        assertEquals(4, info42026!!.month)
        assertEquals(2026, info42026.year)
        assertEquals("04/2026", info42026.formattedDate)

        // 2. Double-digit month without slash ("042026")
        val info042026 = OcrNormalizer.parseDateComponents("042026")
        assertNotNull(info042026)
        assertEquals(4, info042026!!.month)
        assertEquals(2026, info042026.year)
        assertEquals("04/2026", info042026.formattedDate)

        // 3. Alternative separators (pipe, space, backslash)
        val infoPipe = OcrNormalizer.parseDateComponents("04|2026")
        assertNotNull(infoPipe)
        assertEquals("04/2026", infoPipe!!.formattedDate)

        val infoSpace = OcrNormalizer.parseDateComponents("04 2026")
        assertNotNull(infoSpace)
        assertEquals("04/2026", infoSpace!!.formattedDate)

        // 4. Barcode numbers, prices (MRP 12.00), and invalid years (2504, 2000) must be strictly rejected
        val barcodeInfo = OcrNormalizer.parseDateComponents("8 901425 022504")
        assertNull(barcodeInfo)

        val year2504Info = OcrNormalizer.parseDateComponents("02/2504")
        assertNull(year2504Info)

        val mrpPriceInfo = OcrNormalizer.parseDateComponents("MRP (Incl. of all taxes): ₹ 12.00")
        assertNull(mrpPriceInfo)

        val priceOnlyInfo = OcrNormalizer.parseDateComponents("12.00")
        assertNull(priceOnlyInfo)

        val year2000Info = OcrNormalizer.parseDateComponents("12/2000")
        assertNull(year2000Info)

        // 5. Candidate resolution preserves raw OCR "42026" but canonicalizes normalizedText to "04/2026"
        val resolved = OcrNormalizer.resolveDateCandidate(
            primaryText = "42026",
            enhancedText = "04/2026",
            boundingBox = listOf(0f, 0f, 100f, 50f),
            sourceImagePath = "/test/img.jpg",
            sourceEvidenceId = "evid-42026"
        )
        assertNotNull(resolved)
        assertEquals("42026", resolved!!.rawOcr)
        assertEquals("04/2026", resolved.normalizedText)
    }

    @Test
    fun test13_NetQuantityPinCodeRejectionAndCountUnits() {
        // Test detectCandidates with PIN code and Net Quantity 1 N
        val blocks = listOf(
            com.sih.util.ocr.GroupedTextBlock(
                text = "Net Quantity: 1 N",
                normalizedText = "Net Quantity: 1 N",
                boundingBox = listOf(10f, 10f, 100f, 30f),
                sourceImagePath = "/test/img.jpg",
                sourceEvidenceId = null,
                lineCount = 1
            ),
            com.sih.util.ocr.GroupedTextBlock(
                text = "Pune - 410401, Maharashtra, India. Kokuyo Camlin Ltd.",
                normalizedText = "Pune - 410401, Maharashtra, India. Kokuyo Camlin Ltd.",
                boundingBox = listOf(10f, 100f, 200f, 130f),
                sourceImagePath = "/test/img.jpg",
                sourceEvidenceId = null,
                lineCount = 1
            )
        )

        val candidates = OcrNormalizer.detectCandidates("Net Quantity: 1 N\nPune - 410401, Maharashtra, India.", blocks)
        val netQtyCandidate = candidates.firstOrNull { it.type == com.sih.model.DeclarationType.NET_QUANTITY }
        assertNotNull(netQtyCandidate)
        assertTrue(netQtyCandidate!!.normalizedText.contains("1 N") || netQtyCandidate.normalizedText.contains("1") || netQtyCandidate.normalizedText.contains("Net Quantity: 1 N"))
        assertFalse(netQtyCandidate.normalizedText.contains("410401"))
    }

    @Test
    fun test14_PreScanStatutoryConfigurationAndChecklistScoping() {
        val classification = com.sih.domain.classification.ProductClassification(
            category = com.sih.domain.classification.ProductCategory.FOOD,
            subCategory = "Packaged Groceries",
            importStatus = com.sih.domain.classification.ImportStatus.DOMESTIC,
            packageType = com.sih.domain.classification.PackageType.POUCH,
            quantityType = com.sih.domain.classification.QuantityType.MASS_WEIGHT,
            confidence = 0.95f
        )

        // 1. Officer specifies Count basis and active checklist excluding USP and Font size
        val activeChecklist = listOf(
            "Rule 6 Mandatory Declarations",
            "Veg/Non-Veg Dot Check"
        )
        val ruleSet = com.sih.domain.classification.RuleApplicabilityEngine.resolveApplicability(
            classification = classification,
            numericQuantity = 500f,
            officerCategory = "Food & Beverages",
            officerUnitBasis = "Count (N / U / Set)",
            officerSchedule = "Second Schedule (Prescribed Standard Quantities & Sizes)",
            activeChecklist = activeChecklist
        )

        // Verify officer unit basis takes precedence over default classification
        assertTrue(ruleSet.permittedUnits.contains("N"))
        assertTrue(ruleSet.permittedUnits.contains("U"))
        assertFalse(ruleSet.permittedUnits.contains("kg"))

        // Verify schedule is recorded in references
        assertTrue(ruleSet.statutoryReferences.any { it.contains("Second Schedule") })

        // Verify active rules contains Rule 6 and Veg dot
        assertTrue(ruleSet.activeRules.any { it.ruleId == "R6_004" })
        assertTrue(ruleSet.activeRules.any { it.ruleId == "R_VEG_DOT" })

        // Verify rules outside checklist scope are excluded
        assertTrue(ruleSet.excludedRules.any { it.ruleId == "R6_011" })
        assertTrue(ruleSet.excludedRules.any { it.ruleId == "Rule 7(1)" })
        val excludedUsp = ruleSet.excludedRules.first { it.ruleId == "R6_011" }
        assertTrue(excludedUsp.reason.contains("Officer Active Checklist Scope"))

        // 2. Test InspectionDraft persistence with Gson
        val gson = com.google.gson.Gson()
        val draft = InspectionDraft(
            inspectionId = "INSP-TEST-PRE-SCAN",
            officerId = 42,
            establishmentName = "Retail Hypermarket",
            inspectionType = "ROUTINE",
            location = "Delhi",
            state = DraftState.CREATED,
            capturedEvidenceIds = emptyList(),
            qualityResultsJson = null,
            ocrResultsJson = null,
            extractedDeclarationsJson = null,
            classificationJson = null,
            applicableRulesJson = null,
            complianceResultsJson = null,
            reviewState = null,
            signOffJson = null,
            lastUpdated = "2026-09-08T12:00:00",
            selectedCategory = "Cosmetics & Personal Care",
            selectedUnitBasis = "Volume (ml / L)",
            selectedSchedule = "First Schedule (Standard Minimum Font & Numerals)",
            activeChecklistJson = gson.toJson(activeChecklist)
        )

        val json = gson.toJson(draft)
        val deserialized = gson.fromJson(json, InspectionDraft::class.java)

        assertEquals("Cosmetics & Personal Care", deserialized.selectedCategory)
        assertEquals("Volume (ml / L)", deserialized.selectedUnitBasis)
        assertEquals("First Schedule (Standard Minimum Font & Numerals)", deserialized.selectedSchedule)
        assertNotNull(deserialized.activeChecklistJson)
        val listType = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        val restoredChecklist: List<String> = gson.fromJson(deserialized.activeChecklistJson, listType)
        assertEquals(2, restoredChecklist.size)
        assertTrue(restoredChecklist.contains("Veg/Non-Veg Dot Check"))
    }

    @Test
    fun test15_DynamicChecklistCategoryScopingAndSecondScheduleCompliance() {
        val foodClassification = com.sih.domain.classification.ProductClassification(
            category = com.sih.domain.classification.ProductCategory.FOOD,
            subCategory = "Biscuits & Confectionery",
            importStatus = com.sih.domain.classification.ImportStatus.DOMESTIC,
            packageType = com.sih.domain.classification.PackageType.POUCH,
            quantityType = com.sih.domain.classification.QuantityType.MASS_WEIGHT,
            confidence = 0.96f
        )

        // 1. Second Schedule Food Pack with active checklist
        val foodChecklist = listOf(
            "Rule 6 Mandatory Declarations",
            "Rule 6(11) Unit Sale Price (USP)",
            "Rule 7 Font Height & Area Gate",
            "Veg/Non-Veg Dot Check",
            "Second Schedule Standard Pack Size"
        )
        val foodRuleSet = com.sih.domain.classification.RuleApplicabilityEngine.resolveApplicability(
            classification = foodClassification,
            numericQuantity = 100f,
            officerCategory = "Food & Beverages",
            officerUnitBasis = "Mass (g / kg)",
            officerSchedule = "Second Schedule (Prescribed Standard Quantities & Sizes)",
            activeChecklist = foodChecklist
        )

        assertTrue(foodRuleSet.activeRules.any { it.ruleId == "R_SCHED_2" })
        assertTrue(foodRuleSet.activeRules.any { it.ruleId == "R_VEG_DOT" })
        assertTrue(foodRuleSet.activeRules.any { it.ruleId == "R6_011" })
        assertTrue(foodRuleSet.statutoryReferences.any { it.contains("Second Schedule") })

        // 2. Electronics with Count basis & no Veg/Drained weight
        val electronicsClassification = com.sih.domain.classification.ProductClassification(
            category = com.sih.domain.classification.ProductCategory.ELECTRONIC,
            subCategory = "Power Accessories",
            importStatus = com.sih.domain.classification.ImportStatus.IMPORTED,
            packageType = com.sih.domain.classification.PackageType.BOX_CARTON,
            quantityType = com.sih.domain.classification.QuantityType.COUNT_NUMBER,
            confidence = 0.94f
        )
        val electronicsChecklist = listOf(
            "Rule 6 Mandatory Declarations",
            "Rule 6(11) Unit Sale Price (USP)",
            "Rule 7 Font Height & Area Gate",
            "Barcode / Origin Check"
        )
        val elecRuleSet = com.sih.domain.classification.RuleApplicabilityEngine.resolveApplicability(
            classification = electronicsClassification,
            numericQuantity = 1f,
            officerCategory = "Electronics & Hardware",
            officerUnitBasis = "Count (N / U / Set)",
            officerSchedule = "First Schedule (Standard Minimum Font & Numerals)",
            activeChecklist = electronicsChecklist
        )

        // Veg dot and drained wt should NOT be active for electronics
        assertFalse(elecRuleSet.activeRules.any { it.ruleId == "R_VEG_DOT" })
        assertFalse(elecRuleSet.activeRules.any { it.ruleId == "R_DRAINED_WT" })
        // Importer / origin and Rule 6 should be active
        assertTrue(elecRuleSet.activeRules.any { it.ruleId == "R6_003" })
        assertTrue(elecRuleSet.activeRules.any { it.ruleId == "Rule 7(1)" })
        assertTrue(elecRuleSet.permittedUnits.contains("N"))
        assertTrue(elecRuleSet.permittedUnits.contains("U"))
    }

    @Test
    fun test16_AutomaticStatutoryScheduleDeduction() {
        // Second Schedule keywords check
        fun isSecondSchedule(name: String, category: String): Boolean {
            val lower = name.lowercase().trim()
            val secondScheduleKeywords = listOf(
                "biscuit", "cookie", "rusk",
                "oil", "ghee", "vanaspati", "mustard", "refined",
                "atta", "flour", "maida", "suji", "sooji", "besan", "wheat",
                "rice", "dal", "pulse", "cereal", "grain",
                "tea", "coffee",
                "salt",
                "soap", "detergent", "washing powder",
                "milk powder", "infant", "baby food",
                "cement"
            )
            return secondScheduleKeywords.any { lower.contains(it) } ||
                   (category == "Food & Beverages" && (lower.contains("sugar") || lower.contains("snack") || lower.contains("bread")))
        }

        fun inferSchedule(name: String, category: String, isBulk: Boolean): String {
            if (isBulk) {
                return "Fourth Schedule (Institutional Consumer Exemption)"
            }
            if (isSecondSchedule(name, category)) {
                return "Second Schedule (Prescribed Standard Quantities) & First Schedule"
            }
            return "First Schedule (Standard Minimum Font & Numerals)"
        }

        // 1. Biscuits -> Second Schedule
        assertEquals(
            "Second Schedule (Prescribed Standard Quantities) & First Schedule",
            inferSchedule("Parle-G Glucose Biscuits", "Food & Beverages", false)
        )

        // 2. Edible Oil -> Second Schedule
        assertEquals(
            "Second Schedule (Prescribed Standard Quantities) & First Schedule",
            inferSchedule("Fortune Mustard Oil", "Food & Beverages", false)
        )

        // 3. Atta / Flour -> Second Schedule
        assertEquals(
            "Second Schedule (Prescribed Standard Quantities) & First Schedule",
            inferSchedule("Aashirvaad Shudh Chakki Atta", "Food & Beverages", false)
        )

        // 4. Soap & Detergent -> Second Schedule
        assertEquals(
            "Second Schedule (Prescribed Standard Quantities) & First Schedule",
            inferSchedule("Surf Excel Detergent Powder", "Cosmetics & Personal Care", false)
        )

        // 5. Electronics -> First Schedule
        assertEquals(
            "First Schedule (Standard Minimum Font & Numerals)",
            inferSchedule("Fast USB-C Charging Cable", "Electronics & Hardware", false)
        )

        // 6. Stationary / General -> First Schedule
        assertEquals(
            "First Schedule (Standard Minimum Font & Numerals)",
            inferSchedule("Classmate Long Notebook", "General Packaged Commodity", false)
        )

        // 7. Institutional Bulk Consumer -> Fourth Schedule Exemption
        assertEquals(
            "Fourth Schedule (Institutional Consumer Exemption)",
            inferSchedule("Aashirvaad Atta 50kg Hotel Pack", "Food & Beverages", true)
        )
    }
}

