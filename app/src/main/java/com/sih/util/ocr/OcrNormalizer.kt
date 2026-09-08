package com.sih.util.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.text.Text
import com.sih.model.ConfidenceLevel
import com.sih.model.DeclarationCandidate
import com.sih.model.DeclarationType
import com.sih.util.ScannedBlockInfo
import java.util.regex.Pattern

data class NormalizedTextResult(
    val rawText: String,
    val normalizedText: String,
    val substitutions: List<String>
)

data class GroupedTextBlock(
    val text: String,
    val normalizedText: String,
    val boundingBox: List<Float>?,
    val sourceImagePath: String,
    val sourceEvidenceId: String?,
    val lineCount: Int
)

data class OcrNormalizationResult(
    val normalizedText: NormalizedTextResult,
    val groupedBlocks: List<GroupedTextBlock>,
    val candidates: List<DeclarationCandidate>
)

object OcrNormalizer {

    fun normalizeCharacters(rawText: String): NormalizedTextResult {
        var text = rawText
        val substitutions = mutableListOf<String>()

        // Substitutions to handle
        val rsPattern = "(?i)(?:r\\.s\\.|rs\\.|\\brs\\b)(?=\\s*[0-9₹])".toRegex()
        if (rsPattern.containsMatchIn(text)) {
            text = text.replace(rsPattern, "RS")
            substitutions.add("Rs./R.S./rs. -> RS")
        }

        val m1Pattern = "(?<=\\d)\\s*m1\\b".toRegex()
        if (m1Pattern.containsMatchIn(text)) {
            text = text.replace(m1Pattern, "ml")
            substitutions.add("m1 -> ml")
        }

        val gPattern = "(?i)\\b(gms?)\\b".toRegex() // Assuming quantity context by presence
        if (gPattern.containsMatchIn(text)) {
            text = text.replace(gPattern, "g")
            substitutions.add("gm/gms -> g")
        }

        val ltrPattern = "(?i)\\b(ltr)\\b".toRegex()
        if (ltrPattern.containsMatchIn(text)) {
            text = text.replace(ltrPattern, "L")
            substitutions.add("ltr -> L")
        }

        val curlyQuotes = "[\u2018\u2019\u201C\u201D]".toRegex()
        if (curlyQuotes.containsMatchIn(text)) {
            text = text.replace("[\u2018\u2019]".toRegex(), "'").replace("[\u201C\u201D]".toRegex(), "\"")
            substitutions.add("Curly quotes -> straight quotes")
        }

        // Hindi numerals ०-९ to 0-9
        val hindiNumerals = '०'..'९'
        var hasHindi = false
        val charArray = text.toCharArray()
        for (i in charArray.indices) {
            if (charArray[i] in hindiNumerals) {
                charArray[i] = (charArray[i] - '०' + '0'.code).toChar()
                hasHindi = true
            }
        }
        if (hasHindi) {
            text = String(charArray)
            substitutions.add("Hindi numerals -> Arabic")
        }
        
        // Tamil numerals
        val tamilNumerals = '௦'..'௯'
        var hasTamil = false
        val charArray2 = text.toCharArray()
        for (i in charArray2.indices) {
            if (charArray2[i] in tamilNumerals) {
                charArray2[i] = (charArray2[i] - '௦' + '0'.code).toChar()
                hasTamil = true
            }
        }
        if (hasTamil) {
            text = String(charArray2)
            substitutions.add("Tamil numerals -> Arabic")
        }

        val multiSpace = " {2,}".toRegex()
        if (multiSpace.containsMatchIn(text)) {
            text = text.replace(multiSpace, " ")
            substitutions.add("Multiple spaces -> single space")
        }

        val mrpPattern = "(?i)\\b(m\\.r\\.p\\.?|m\\s*r\\s*p|mrp)\\b".toRegex()
        if (mrpPattern.containsMatchIn(text)) {
            text = text.replace(mrpPattern, "MRP")
            substitutions.add("M.R.P. -> MRP")
        }

        val netQtyPattern = "(?i)\\b(net\\s*q[t7]y\\.?|net\\s*quant[i1]ty|nett\\s*q[t7]y)\\b".toRegex()
        if (netQtyPattern.containsMatchIn(text)) {
            text = text.replace(netQtyPattern, "NET QTY")
            substitutions.add("Net Qty variants -> NET QTY")
        }

        val mfdPattern = "(?i)\\b(m\\.f\\.[dg]\\.?|mfd|mfg|pkd)\\b".toRegex()
        if (mfdPattern.containsMatchIn(text)) {
            text = text.replace(mfdPattern, "MFD")
            substitutions.add("Mfd/Mfg/Pkd -> MFD")
        }

        val inclPattern = "(?i)\\b(incl\\.|incl)\\b".toRegex()
        if (inclPattern.containsMatchIn(text)) {
            text = text.replace(inclPattern, "inclusive")
            substitutions.add("incl. -> inclusive")
        }
        
        // Normalize line endings
        text = text.replace("\r\n", "\n")

        return NormalizedTextResult(rawText, text, substitutions)
    }

    fun groupAdjacentLines(blocks: List<ScannedBlockInfo>): List<GroupedTextBlock> {
        val groupedBlocks = mutableListOf<GroupedTextBlock>()

        for (info in blocks) {
            val lines = info.block.lines
            if (lines.isEmpty()) continue

            var currentGroupLines = mutableListOf(lines.first())
            var currentGroupBbox = lines.first().boundingBox?.let { android.graphics.Rect(it) }
            
            for (i in 1 until lines.size) {
                val currentLine = lines[i]
                val prevLine = lines[i - 1]
                
                val prevBbox = prevLine.boundingBox
                val currentBbox = currentLine.boundingBox
                
                if (prevBbox != null && currentBbox != null) {
                    val prevLineHeight = prevBbox.bottom - prevBbox.top
                    val currentLineHeight = currentBbox.bottom - currentBbox.top
                    val avgHeight = (prevLineHeight + currentLineHeight) / 2.0f
                    
                    val verticalGap = currentBbox.top - prevBbox.bottom
                    
                    if (verticalGap < 1.5 * avgHeight) {
                        currentGroupLines.add(currentLine)
                        
                        // update bounding box union safely without mutating ML Kit's Rect
                        currentGroupBbox?.let { cb ->
                            cb.left = minOf(cb.left, currentBbox.left)
                            cb.top = minOf(cb.top, currentBbox.top)
                            cb.right = maxOf(cb.right, currentBbox.right)
                            cb.bottom = maxOf(cb.bottom, currentBbox.bottom)
                        } ?: run {
                            currentGroupBbox = android.graphics.Rect(currentBbox)
                        }
                    } else {
                        // Create group
                        val text = currentGroupLines.joinToString(" ") { it.text }
                        val normalized = normalizeCharacters(text).normalizedText
                        val bboxFloat = currentGroupBbox?.let { listOf(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
                        groupedBlocks.add(GroupedTextBlock(text, normalized, bboxFloat, info.imagePath, null, currentGroupLines.size))
                        
                        currentGroupLines = mutableListOf(currentLine)
                        currentGroupBbox = android.graphics.Rect(currentBbox)
                    }
                } else {
                    currentGroupLines.add(currentLine)
                }
            }
            
            if (currentGroupLines.isNotEmpty()) {
                val text = currentGroupLines.joinToString(" ") { it.text }
                val normalized = normalizeCharacters(text).normalizedText
                val bboxFloat = currentGroupBbox?.let { listOf(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
                groupedBlocks.add(GroupedTextBlock(text, normalized, bboxFloat, info.imagePath, null, currentGroupLines.size))
            }
        }
        return groupedBlocks
    }

    fun detectCandidates(fullText: String, groupedBlocks: List<GroupedTextBlock>): List<DeclarationCandidate> {
        val candidates = mutableListOf<DeclarationCandidate>()
        
        val mrpRegex = "(?i)(?:m\\.?r\\.?p\\.?|mrp|retail\\s*price)\\s*[:.]?\\s*(?:rs\\.?|₹|RS)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)".toRegex()
        val netQtyRegex = "(?i)(?:net\\s*(?:qty|quantity|wt|weight|vol|volume)|nett\\s*qty)\\s*[:.-]*\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(g|gm|gms|kg|ml|l|ltr|litre|litres|units?|pcs?|pieces?|n|u|pages?|sheets?)\\b".toRegex()
        val mfgRegex = "(?i)(?:mfg\\.?\\s*by|manufactured\\s*by|marketed\\s*by)\\s*[:.-]*\\s*(.+?)(?:\\n|$)".toRegex()
        val packerRegex = "(?i)(?:packed\\s*by|pkd\\.?\\s*by)\\s*[:.-]*\\s*(.+?)(?:\\n|$)".toRegex()
        val importerRegex = "(?i)(?:imported\\s*by|importer)\\s*[:.-]*\\s*(.+?)(?:\\n|$)".toRegex()
        val dateRegex = "(?i)(?:mfg|mfd|pkd|packed|use\\s*by|best\\s*before|expiry|exp)\\s*[:.-]*\\s*([0-3]?[0-9][/\\-\\.\\s|\\\\][0-1]?[0-9][/\\-\\.\\s|\\\\]\\d{2,4}|[0-1]?[0-9][/\\-\\.\\s|\\\\]\\d{2,4}|(?:0[1-9]|1[0-2])20\\d{2}|[1-9]20\\d{2}|[A-Za-z]{3}[/\\-\\s]?\\d{2,4})".toRegex()
        val ccRegex = "(?i)(?:consumer\\s*care|customer\\s*care|toll\\s*free|helpline|1800)".toRegex()
        val uspRegex = "(?i)(?:unit\\s*sale\\s*price|usp|u\\.s\\.p|₹\\s*/\\s*(?:kg|g|l|ml))".toRegex()

        for (block in groupedBlocks) {
            val text = block.normalizedText
            
            fun addCandidate(type: DeclarationType, matchResult: MatchResult?) {
                if (matchResult != null) {
                    val isExact = text.trim() == matchResult.value.trim()
                    val confFloat = if (isExact) 0.95f else 0.80f
                    val confLevel = if (isExact) ConfidenceLevel.HIGH else ConfidenceLevel.MEDIUM
                    candidates.add(DeclarationCandidate(
                        type = type,
                        rawText = text,
                        normalizedText = matchResult.groupValues.drop(1).joinToString(" ").ifBlank { text },
                        confidence = confFloat,
                        confidenceLevel = confLevel,
                        boundingBox = block.boundingBox,
                        sourceEvidenceId = block.sourceEvidenceId,
                        sourceImagePath = block.sourceImagePath
                    ))
                }
            }

            addCandidate(DeclarationType.MRP, mrpRegex.find(text))
            addCandidate(DeclarationType.NET_QUANTITY, netQtyRegex.find(text))
            addCandidate(DeclarationType.MANUFACTURER, mfgRegex.find(text))
            addCandidate(DeclarationType.PACKER, packerRegex.find(text))
            addCandidate(DeclarationType.IMPORTER, importerRegex.find(text))

            val dateMatch = dateRegex.find(text)
            if (dateMatch != null) {
                val parsed = parseDateComponents(text)
                val displayVal = parsed?.formattedDate ?: dateMatch.groupValues.drop(1).joinToString(" ").ifBlank { text }
                val isExact = text.trim() == dateMatch.value.trim()
                candidates.add(DeclarationCandidate(
                    type = DeclarationType.DATE,
                    rawText = text,
                    normalizedText = displayVal,
                    confidence = if (isExact) 0.95f else 0.85f,
                    confidenceLevel = ConfidenceLevel.HIGH,
                    boundingBox = block.boundingBox,
                    sourceEvidenceId = block.sourceEvidenceId,
                    sourceImagePath = block.sourceImagePath,
                    rawOcr = text,
                    enhancedOcr = null,
                    ambiguityReason = if (parsed != null && !text.contains("/")) "inferred_separator" else null
                ))
            } else {
                val isLikelyBarcodeOrPhone = text.replace(Regex("[^0-9]"), "").length >= 8 && !text.contains("/") && !text.contains("-")
                val standaloneParsed = if (!isLikelyBarcodeOrPhone) parseDateComponents(text) else null
                if (standaloneParsed != null) {
                    candidates.add(DeclarationCandidate(
                        type = DeclarationType.DATE,
                        rawText = text,
                        normalizedText = standaloneParsed.formattedDate,
                        confidence = 0.70f,
                        confidenceLevel = ConfidenceLevel.MEDIUM,
                        boundingBox = block.boundingBox,
                        sourceEvidenceId = block.sourceEvidenceId,
                        sourceImagePath = block.sourceImagePath,
                        rawOcr = text,
                        enhancedOcr = null,
                        ambiguityReason = if (!text.contains("/")) "inferred_separator" else null
                    ))
                }
            }
            
            val ccMatch = ccRegex.find(text)
            if (ccMatch != null) {
                candidates.add(DeclarationCandidate(
                    type = DeclarationType.CUSTOMER_CARE,
                    rawText = text,
                    normalizedText = text,
                    confidence = 0.80f,
                    confidenceLevel = ConfidenceLevel.MEDIUM,
                    boundingBox = block.boundingBox,
                    sourceEvidenceId = block.sourceEvidenceId,
                    sourceImagePath = block.sourceImagePath
                ))
            }
            
            val uspMatch = uspRegex.find(text)
            if (uspMatch != null) {
                candidates.add(DeclarationCandidate(
                    type = DeclarationType.UNIT_SALE_PRICE,
                    rawText = text,
                    normalizedText = text,
                    confidence = 0.80f,
                    confidenceLevel = ConfidenceLevel.MEDIUM,
                    boundingBox = block.boundingBox,
                    sourceEvidenceId = block.sourceEvidenceId,
                    sourceImagePath = block.sourceImagePath
                ))
            }
            
            // COMMODITY_NAME fallback
            if (candidates.none { it.rawText == text } && block.boundingBox != null && block.boundingBox[1] < 200) {
                 candidates.add(DeclarationCandidate(
                     type = DeclarationType.COMMODITY_NAME,
                     rawText = text,
                     normalizedText = text,
                     confidence = 0.60f,
                     confidenceLevel = ConfidenceLevel.REVIEW,
                     boundingBox = block.boundingBox,
                     sourceEvidenceId = block.sourceEvidenceId,
                     sourceImagePath = block.sourceImagePath
                 ))
            }
        }
        return candidates
    }

    data class ParsedDateInfo(
        val prefix: String,
        val day: Int?,
        val month: Int,
        val year: Int,
        val isExpiryOrBestBefore: Boolean,
        val rawDateString: String
    ) {
        val formattedDate: String
            get() = if (day != null) {
                String.format("%02d/%02d/%04d", day, month, year)
            } else {
                String.format("%02d/%04d", month, year)
            }
    }

    /**
     * Parses a date string into components and determines if it is a manufacturing or expiry date.
     * Robust to dropped/faint slashes, alternative separators (|, -, ., space), and unseparated CIJ dates (e.g. 42026, 042026).
     * Strictly rejects barcode numbers, phone numbers, and out-of-range years (e.g. 2504).
     */
    fun parseDateComponents(text: String): ParsedDateInfo? {
        val lower = text.lowercase()
        val isExpiry = lower.contains("exp") || lower.contains("use by") || lower.contains("best before")
        val hasDateKeyword = lower.contains("mfg") || lower.contains("mfd") || lower.contains("pkd") ||
                lower.contains("packed") || lower.contains("pack") || isExpiry || lower.contains("date")

        val digitsOnly = text.replace(Regex("[^0-9]"), "")
        // Reject barcodes, phone numbers, serial codes (e.g. "8 901425 022504" or "52484912500")
        if (!hasDateKeyword && digitsOnly.length >= 8 && !text.contains("/") && !text.contains("-")) {
            return null
        }
        if (text.contains("tel", ignoreCase = true) || text.contains("phone", ignoreCase = true) || text.contains("care", ignoreCase = true)) {
            return null
        }
        // Reject price lines (e.g. "MRP (Incl. of all taxes): ₹ 12.00" must not be parsed as 12/2000)
        if (!hasDateKeyword && (lower.contains("mrp") || lower.contains("₹") || lower.contains("rs.") ||
                lower.contains("rs ") || lower.contains("price") || lower.contains("tax") || lower.contains("incl"))) {
            return null
        }
        // Reject size and quantity lines
        if (!hasDateKeyword && (lower.contains("net") || lower.contains("qty") || lower.contains("size") ||
                lower.contains("length") || lower.contains("weight") || lower.contains("vol"))) {
            return null
        }

        val currentYear = java.time.Year.now().value
        val minYear = currentYear - 8
        val maxYear = currentYear + 6

        fun validateYear(rawYear: Int): Int? {
            var y = rawYear
            if (y in 18..35) {
                y += 2000
            } else if (y !in minYear..maxYear) {
                return null
            }
            return y
        }

        // 1. DD/MM/YYYY or DD-MM-YYYY (with slash, dot, dash, space, pipe, backslash)
        val dmyRegex = """(?i)([0-3]?[0-9])\s*[/.\-\s|\\]\s*([0-1]?[0-9])\s*[/.\-\s|\\]\s*(\d{2,4})""".toRegex()
        val dmyMatch = dmyRegex.find(text)
        if (dmyMatch != null) {
            val d = dmyMatch.groupValues[1].toIntOrNull() ?: return null
            val m = dmyMatch.groupValues[2].toIntOrNull() ?: return null
            val rawY = dmyMatch.groupValues[3].toIntOrNull() ?: return null
            val y = validateYear(rawY)
            if (y != null && m in 1..12 && d in 1..31) {
                return ParsedDateInfo(text.substring(0, dmyMatch.range.first).trim(), d, m, y, isExpiry, dmyMatch.value)
            }
        }

        // 2. MM/YYYY with separator (slash, dot, dash, space, pipe, backslash)
        val myRegex = """(?i)\b(0[1-9]|1[0-2]|[1-9])\s*[/.\-\s|\\]\s*(\d{2,4})\b""".toRegex()
        val myMatch = myRegex.find(text)
        if (myMatch != null) {
            val m = myMatch.groupValues[1].toIntOrNull() ?: return null
            val rawY = myMatch.groupValues[2].toIntOrNull() ?: return null
            val y = validateYear(rawY)
            if (y != null && m in 1..12) {
                return ParsedDateInfo(text.substring(0, myMatch.range.first).trim(), null, m, y, isExpiry, myMatch.value)
            }
        }

        // 3. Unseparated Month + 4-digit Year (e.g. "042026", "122025" when slash was dropped by CIJ dot-matrix OCR)
        val unsep6Regex = """(?i)(?:^|[^\d])(0[1-9]|1[0-2])(20\d{2})(?:$|[^\d])""".toRegex()
        val match6 = unsep6Regex.find(text)
        if (match6 != null) {
            val m = match6.groupValues[1].toIntOrNull()
            val rawY = match6.groupValues[2].toIntOrNull()
            val y = rawY?.let { validateYear(it) }
            if (m != null && y != null && m in 1..12) {
                return ParsedDateInfo(text.substring(0, match6.range.first).trim(), null, m, y, isExpiry, match6.value.trim())
            }
        }

        // 4. Unseparated Single-digit Month + 4-digit Year (e.g. "42026" where leading zero and slash were dropped)
        val unsep5Regex = """(?i)(?:^|[^\d])([1-9])(20\d{2})(?:$|[^\d])""".toRegex()
        val match5 = unsep5Regex.find(text)
        if (match5 != null) {
            val m = match5.groupValues[1].toIntOrNull()
            val rawY = match5.groupValues[2].toIntOrNull()
            val y = rawY?.let { validateYear(it) }
            if (m != null && y != null && m in 1..12) {
                return ParsedDateInfo(text.substring(0, match5.range.first).trim(), null, m, y, isExpiry, match5.value.trim())
            }
        }

        // 5. Short year with date prefix: e.g. "MFD 0426" or "MFD 426"
        val shortYearRegex = """(?i)(?:mfg|mfd|pkd|exp|packed)\s*[:.\-]*\s*(0[1-9]|1[0-2]|[1-9])(\d{2})(?:$|[^\d])""".toRegex()
        val shortMatch = shortYearRegex.find(text)
        if (shortMatch != null) {
            val m = shortMatch.groupValues[1].toIntOrNull()
            val rawY = shortMatch.groupValues[2].toIntOrNull()
            val y = rawY?.let { validateYear(it) }
            if (m != null && y != null && m in 1..12) {
                return ParsedDateInfo(text.substring(0, shortMatch.range.first).trim(), null, m, y, isExpiry, shortMatch.value.trim())
            }
        }

        return null
    }

    /**
     * Confidence-aware plausibility check for packaging dates under Legal Metrology standards.
     * Evaluates against device calendar year without hardcoding.
     */
    fun evaluateDatePlausibility(
        dateInfo: ParsedDateInfo,
        currentYear: Int = java.time.Year.now().value
    ): Pair<Boolean, String?> {
        if (dateInfo.isExpiryOrBestBefore) {
            // Expiry/Best-before can legitimately be in the future (up to 5 years ahead)
            if (dateInfo.year > currentYear + 5) {
                return Pair(false, "Expiry date exceeds reasonable horizon (>5 years)")
            }
            return Pair(true, null)
        }

        // Manufacturing / Packing date cannot be in the future (allowing max 1 month grace for end-of-month packing)
        if (dateInfo.year > currentYear) {
            return Pair(false, "Manufacturing date is in future ($dateInfo.year > current $currentYear)")
        }

        return Pair(true, null)
    }

    /**
     * Reconciles primary OCR and dot-matrix enhanced OCR passes without silent mutation.
     * Preserves raw evidence and marks ambiguity for human review.
     */
    fun resolveDateCandidate(
        primaryText: String?,
        enhancedText: String?,
        boundingBox: List<Float>?,
        sourceImagePath: String?,
        sourceEvidenceId: String?,
        currentYear: Int = java.time.Year.now().value
    ): DeclarationCandidate? {
        val primParsed = primaryText?.let { parseDateComponents(it) }
        val enhParsed = enhancedText?.let { parseDateComponents(it) }

        if (primParsed == null && enhParsed == null) return null

        val primaryPlausible = primParsed?.let { evaluateDatePlausibility(it, currentYear).first } ?: false
        val enhancedPlausible = enhParsed?.let { evaluateDatePlausibility(it, currentYear).first } ?: false

        // Case 1: Both passes agree
        if (primParsed != null && enhParsed != null && primParsed.year == enhParsed.year && primParsed.month == enhParsed.month) {
            return DeclarationCandidate(
                type = DeclarationType.DATE,
                rawText = primaryText,
                normalizedText = primParsed.formattedDate,
                confidence = 0.95f,
                confidenceLevel = ConfidenceLevel.HIGH,
                boundingBox = boundingBox,
                sourceEvidenceId = sourceEvidenceId,
                sourceImagePath = sourceImagePath,
                rawOcr = primaryText,
                enhancedOcr = enhancedText,
                ambiguityReason = null
            )
        }

        // Case 2: Primary read future date (e.g. 2028), but enhanced morphological pass resolved to plausible date (e.g. 2026)
        // Known CIJ dot-matrix confusion 8 <-> 6, 5 <-> 6, 1 <-> 7
        if (primParsed != null && enhParsed != null && !primaryPlausible && enhancedPlausible) {
            val isDotMatrixConfusion = (primParsed.year.toString().endsWith("8") && enhParsed.year.toString().endsWith("6")) ||
                    (primParsed.year.toString().endsWith("6") && enhParsed.year.toString().endsWith("5")) ||
                    (primParsed.year.toString().endsWith("7") && enhParsed.year.toString().endsWith("1"))

            val reason = if (isDotMatrixConfusion) "dot_matrix_year_ambiguity (${primParsed.year} vs ${enhParsed.year})"
                         else "pass_disagreement (${primParsed.year} vs ${enhParsed.year})"

            return DeclarationCandidate(
                type = DeclarationType.DATE,
                rawText = primaryText,
                normalizedText = enhParsed.formattedDate, // Provide canonical enhanced reading
                confidence = 0.70f,
                confidenceLevel = ConfidenceLevel.REVIEW, // Strictly flagged for human verification!
                boundingBox = boundingBox,
                sourceEvidenceId = sourceEvidenceId,
                sourceImagePath = sourceImagePath,
                rawOcr = primaryText,
                enhancedOcr = enhancedText,
                ambiguityReason = reason
            )
        }

        // Case 3: Primary pass is plausible, enhanced is missing or unhelpful
        if (primParsed != null && primaryPlausible) {
            return DeclarationCandidate(
                type = DeclarationType.DATE,
                rawText = primaryText,
                normalizedText = primParsed.formattedDate,
                confidence = 0.85f,
                confidenceLevel = ConfidenceLevel.MEDIUM,
                boundingBox = boundingBox,
                sourceEvidenceId = sourceEvidenceId,
                sourceImagePath = sourceImagePath,
                rawOcr = primaryText,
                enhancedOcr = enhancedText,
                ambiguityReason = null
            )
        }

        // Case 4: Disagreement or single pass with implausible future date -> flag for review
        val chosenText = primaryText ?: enhancedText ?: ""
        val fallbackFormatted = (enhParsed ?: primParsed)?.formattedDate ?: chosenText
        return DeclarationCandidate(
            type = DeclarationType.DATE,
            rawText = chosenText,
            normalizedText = fallbackFormatted,
            confidence = 0.50f,
            confidenceLevel = ConfidenceLevel.REVIEW,
            boundingBox = boundingBox,
            sourceEvidenceId = sourceEvidenceId,
            sourceImagePath = sourceImagePath,
            rawOcr = primaryText,
            enhancedOcr = enhancedText,
            ambiguityReason = "implausible_mfg_date"
        )
    }

    fun runFullPipeline(fullText: String, blocks: List<ScannedBlockInfo>): OcrNormalizationResult {
        val normalizedText = normalizeCharacters(fullText)
        val groupedBlocks = groupAdjacentLines(blocks)
        val candidates = detectCandidates(normalizedText.normalizedText, groupedBlocks)
        return OcrNormalizationResult(normalizedText, groupedBlocks, candidates)
    }
}

