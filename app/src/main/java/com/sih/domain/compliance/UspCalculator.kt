package com.sih.domain.compliance

import java.math.BigDecimal
import java.math.RoundingMode

enum class QuantityUnit {
    GRAM, KILOGRAM, MILLILITER, LITER, UNIT, PAGE;
    
    fun toBaseUnit(): QuantityUnit = when(this) {
        GRAM, KILOGRAM -> GRAM
        MILLILITER, LITER -> MILLILITER
        else -> this
    }
    
    fun displayStandardUnit(): String = when(this) {
        GRAM, KILOGRAM -> "kg"
        MILLILITER, LITER -> "L"
        UNIT -> "unit"
        PAGE -> "page"
    }
}

data class NormalizedQuantity(
    val value: BigDecimal,
    val unit: QuantityUnit,
    val originalText: String,
    val confidence: Float
)

enum class UspStatus {
    VERIFIED,
    MISSING,
    UNABLE_TO_VERIFY,
    MISMATCH
}

data class UspResult(
    val calculatedUsp: BigDecimal?,
    val displayUnit: String?,
    val displayValue: String?,
    val status: UspStatus,
    val declaredUsp: String?,
    val calculatedUspStr: String?,
    val mismatchDelta: BigDecimal?
)

object UspCalculator {

    fun normalizeQuantity(ocrText: String): NormalizedQuantity? {
        val regex = "(?i)([0-9]+(?:\\.[0-9]+)?)\\s*(g|gm|kg|ml|l|ltr|litre|litres|units|n|pages|sheets)".toRegex()
        val match = regex.find(ocrText) ?: return null
        
        val valueStr = match.groupValues[1]
        val unitStr = match.groupValues[2].lowercase()
        
        val value = try {
            BigDecimal(valueStr)
        } catch (e: Exception) {
            return null
        }
        
        val unit = when (unitStr) {
            "g", "gm" -> QuantityUnit.GRAM
            "kg" -> QuantityUnit.KILOGRAM
            "ml" -> QuantityUnit.MILLILITER
            "l", "ltr", "litre", "litres" -> QuantityUnit.LITER
            "units", "n" -> QuantityUnit.UNIT
            "pages", "sheets" -> QuantityUnit.PAGE
            else -> return null
        }
        
        var normalizedValue = value
        if (unit == QuantityUnit.KILOGRAM) {
            normalizedValue = value.multiply(BigDecimal(1000))
        } else if (unit == QuantityUnit.LITER) {
            normalizedValue = value.multiply(BigDecimal(1000))
        }
        
        return NormalizedQuantity(normalizedValue, unit.toBaseUnit(), ocrText, 0.9f)
    }

    fun extractMrpValue(mrpText: String): BigDecimal? {
        val regex = "(?i)(?:m\\.?r\\.?p\\.?|rs\\.?|₹|RS)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)".toRegex()
        val match = regex.find(mrpText) ?: return null
        val valueStr = match.groupValues[1].replace(",", "")
        return try {
            BigDecimal(valueStr)
        } catch (e: Exception) {
            null
        }
    }

    fun calculateUsp(mrpValue: BigDecimal, quantity: NormalizedQuantity): UspResult {
        val qtyInStandard = when (quantity.unit) {
            QuantityUnit.GRAM -> quantity.value.divide(BigDecimal(1000), 4, RoundingMode.HALF_UP)
            QuantityUnit.MILLILITER -> quantity.value.divide(BigDecimal(1000), 4, RoundingMode.HALF_UP)
            else -> quantity.value
        }
        
        if (qtyInStandard.compareTo(BigDecimal.ZERO) == 0) {
            return UspResult(null, null, null, UspStatus.UNABLE_TO_VERIFY, null, null, null)
        }
        
        val calculatedUsp = mrpValue.divide(qtyInStandard, 2, RoundingMode.HALF_UP)
        val displayUnit = "₹/${quantity.unit.displayStandardUnit()}"
        val displayValue = "₹$calculatedUsp/$displayUnit".replace("/₹/", "/") // Clean up formatting
        
        return UspResult(
            calculatedUsp = calculatedUsp,
            displayUnit = displayUnit,
            displayValue = displayValue,
            status = UspStatus.MISSING, // Initial status
            declaredUsp = null,
            calculatedUspStr = calculatedUsp.toString(),
            mismatchDelta = null
        )
    }

    fun verifyDeclaredUsp(declaredUspText: String?, mrpText: String?, quantityText: String?): UspResult {
        if (mrpText == null || quantityText == null) {
            return UspResult(null, null, null, UspStatus.UNABLE_TO_VERIFY, declaredUspText, null, null)
        }
        
        val mrpValue = extractMrpValue(mrpText)
        val quantity = normalizeQuantity(quantityText)
        
        if (mrpValue == null || quantity == null || quantity.confidence < 0.70f) {
            return UspResult(null, null, null, UspStatus.UNABLE_TO_VERIFY, declaredUspText, null, null)
        }
        
        val calcResult = calculateUsp(mrpValue, quantity)
        val calculated = calcResult.calculatedUsp ?: return calcResult.copy(status = UspStatus.UNABLE_TO_VERIFY)
        
        if (declaredUspText.isNullOrBlank()) {
            return calcResult.copy(status = UspStatus.MISSING)
        }
        
        val declaredValue = extractMrpValue(declaredUspText) ?: return calcResult.copy(status = UspStatus.UNABLE_TO_VERIFY, declaredUsp = declaredUspText)
        
        val delta = calculated.subtract(declaredValue).abs()
        val tolerance = calculated.multiply(BigDecimal("0.01")) // 1% tolerance
        
        val status = if (delta <= tolerance) UspStatus.VERIFIED else UspStatus.MISMATCH
        
        return calcResult.copy(
            status = status,
            declaredUsp = declaredUspText,
            mismatchDelta = if (status == UspStatus.MISMATCH) delta else null
        )
    }
}
