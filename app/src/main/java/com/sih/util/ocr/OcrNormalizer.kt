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
                charArray[i] = (charArray[i] - '०' + '0'.toInt()).toChar()
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
                charArray2[i] = (charArray2[i] - '௦' + '0'.toInt()).toChar()
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

        val mrpPattern = "(?i)\\b(m\\.r\\.p\\.?)\\b".toRegex()
        if (mrpPattern.containsMatchIn(text)) {
            text = text.replace(mrpPattern, "MRP")
            substitutions.add("M.R.P. -> MRP")
        }

        val inclPattern = "(?i)\\b(incl\\.)\\b".toRegex()
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
            var currentGroupBbox = lines.first().boundingBox
            
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
                        
                        // update bounding box union
                        currentGroupBbox?.let { cb ->
                            cb.left = minOf(cb.left, currentBbox.left)
                            cb.top = minOf(cb.top, currentBbox.top)
                            cb.right = maxOf(cb.right, currentBbox.right)
                            cb.bottom = maxOf(cb.bottom, currentBbox.bottom)
                        } ?: run {
                            currentGroupBbox = currentBbox
                        }
                    } else {
                        // Create group
                        val text = currentGroupLines.joinToString(" ") { it.text }
                        val normalized = normalizeCharacters(text).normalizedText
                        val bboxFloat = currentGroupBbox?.let { listOf(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
                        groupedBlocks.add(GroupedTextBlock(text, normalized, bboxFloat, info.imagePath, null, currentGroupLines.size))
                        
                        currentGroupLines = mutableListOf(currentLine)
                        currentGroupBbox = currentBbox
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
        val netQtyRegex = "(?i)(?:net\\s*(?:qty|quantity|wt|weight|vol|volume))\\s*[:.-]*\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(g|gm|kg|ml|l|ltr|litre|litres|units|n|pages|sheets)".toRegex()
        val mfgRegex = "(?i)(?:mfg\\.?\\s*by|manufactured\\s*by|marketed\\s*by)\\s*[:.-]*\\s*(.+?)(?:\\n|$)".toRegex()
        val packerRegex = "(?i)(?:packed\\s*by|pkd\\.?\\s*by)\\s*[:.-]*\\s*(.+?)(?:\\n|$)".toRegex()
        val importerRegex = "(?i)(?:imported\\s*by|importer)\\s*[:.-]*\\s*(.+?)(?:\\n|$)".toRegex()
        val dateRegex = "(?i)(?:mfg|mfd|pkd|packed|use\\s*by|best\\s*before|expiry|exp)\\s*[:.-]*\\s*([0-3]?[0-9][/\\-\\.][0-1]?[0-9][/\\-\\.]\\d{2,4}|[A-Za-z]{3}[/\\-\\s]?\\d{2,4})".toRegex()
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
            addCandidate(DeclarationType.DATE, dateRegex.find(text))
            
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

    fun runFullPipeline(fullText: String, blocks: List<ScannedBlockInfo>): OcrNormalizationResult {
        val normalizedText = normalizeCharacters(fullText)
        val groupedBlocks = groupAdjacentLines(blocks)
        val candidates = detectCandidates(normalizedText.normalizedText, groupedBlocks)
        return OcrNormalizationResult(normalizedText, groupedBlocks, candidates)
    }
}
