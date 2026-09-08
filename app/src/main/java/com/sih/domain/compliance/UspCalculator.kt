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
}

enum class StatutoryUspUnit(val symbol: String, val baseMultiplier: BigDecimal) {
    PER_G("g", BigDecimal("1")),
    PER_100G("100g", BigDecimal("100")),
    PER_KG("kg", BigDecimal("1000")),
    PER_ML("ml", BigDecimal("1")),
    PER_100ML("100ml", BigDecimal("100")),
    PER_L("L", BigDecimal("1000")),
    PER_UNIT("unit", BigDecimal("1"));

    fun displayUnit(): String = "₹/$symbol"
}

data class NormalizedQuantity(
    val valueInBase: BigDecimal, // in grams or millilitres or units
    val originalUnit: QuantityUnit,
    val originalAmount: BigDecimal,
    val originalText: String,
    val confidence: Float
) {
    fun isSmallPackageExempt(): Boolean {
        return (originalUnit.toBaseUnit() == QuantityUnit.GRAM || originalUnit.toBaseUnit() == QuantityUnit.MILLILITER) &&
                valueInBase <= BigDecimal("10")
    }

    fun getCanonicalStatutoryUnit(): StatutoryUspUnit {
        return when (originalUnit.toBaseUnit()) {
            QuantityUnit.GRAM -> {
                if (valueInBase < BigDecimal("1000")) StatutoryUspUnit.PER_100G
                else StatutoryUspUnit.PER_KG
            }
            QuantityUnit.MILLILITER -> {
                if (valueInBase < BigDecimal("1000")) StatutoryUspUnit.PER_100ML
                else StatutoryUspUnit.PER_L
            }
            else -> StatutoryUspUnit.PER_UNIT
        }
    }
}

enum class UspStatus {
    VERIFIED,
    MISSING,
    UNABLE_TO_VERIFY,
    MISMATCH,
    EXEMPT
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
        val regex = "(?i)([0-9]+(?:\\.[0-9]+)?)\\s*(g|gm|gms|gram|grams|kg|kgs|ml|mls|l|ltr|ltrs|litre|litres|liter|liters|units|n|nos|pages|sheets)\\b".toRegex()
        val match = regex.find(ocrText) ?: return null
        
        val valueStr = match.groupValues[1]
        val unitStr = match.groupValues[2].lowercase()
        
        val value = try {
            BigDecimal(valueStr)
        } catch (e: Exception) {
            return null
        }
        
        val unit = when (unitStr) {
            "g", "gm", "gms", "gram", "grams" -> QuantityUnit.GRAM
            "kg", "kgs" -> QuantityUnit.KILOGRAM
            "ml", "mls" -> QuantityUnit.MILLILITER
            "l", "ltr", "ltrs", "litre", "litres", "liter", "liters" -> QuantityUnit.LITER
            "units", "n", "nos" -> QuantityUnit.UNIT
            "pages", "sheets" -> QuantityUnit.PAGE
            else -> return null
        }
        
        val valueInBase = when (unit) {
            QuantityUnit.KILOGRAM -> value.multiply(BigDecimal("1000"))
            QuantityUnit.LITER -> value.multiply(BigDecimal("1000"))
            else -> value
        }
        
        return NormalizedQuantity(
            valueInBase = valueInBase,
            originalUnit = unit,
            originalAmount = value,
            originalText = ocrText,
            confidence = 0.9f
        )
    }

    fun extractMrpValue(mrpText: String): BigDecimal? {
        val currencyRegex = "(?i)(?:m\\.?r\\.?p\\.?|rs\\.?|₹|inr)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)".toRegex()
        val match = currencyRegex.find(mrpText) ?: "(?i)([0-9,]+(?:\\.[0-9]{1,2})?)".toRegex().find(mrpText) ?: return null
        val valueStr = match.groupValues[1].replace(",", "")
        return try {
            BigDecimal(valueStr)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calculates statutory USP using exact Legal Metrology Rule 6(11) rules:
     * - Items < 1 kg: canonical display unit is 'per 100 g'
     * - Items >= 1 kg: canonical display unit is 'per kg'
     * - Items < 1 L: canonical display unit is 'per 100 ml'
     * - Items >= 1 L: canonical display unit is 'per L'
     */
    fun calculateUsp(mrpValue: BigDecimal, quantity: NormalizedQuantity): UspResult {
        if (quantity.valueInBase.compareTo(BigDecimal.ZERO) == 0) {
            return UspResult(null, null, null, UspStatus.UNABLE_TO_VERIFY, null, null, null)
        }

        if (quantity.isSmallPackageExempt()) {
            return UspResult(null, null, null, UspStatus.EXEMPT, null, null, null)
        }

        val canonicalUnit = quantity.getCanonicalStatutoryUnit()
        // Price per base unit = mrpValue / valueInBase
        // Canonical USP = (mrpValue / valueInBase) * canonicalUnit.baseMultiplier
        val calculatedUsp = mrpValue
            .multiply(canonicalUnit.baseMultiplier)
            .divide(quantity.valueInBase, 2, RoundingMode.HALF_UP)

        val displayUnit = canonicalUnit.displayUnit()
        val displayValue = "₹$calculatedUsp/${canonicalUnit.symbol}"

        return UspResult(
            calculatedUsp = calculatedUsp,
            displayUnit = displayUnit,
            displayValue = displayValue,
            status = UspStatus.MISSING,
            declaredUsp = null,
            calculatedUspStr = calculatedUsp.toString(),
            mismatchDelta = null
        )
    }

    /**
     * Parses declared USP text and returns a Pair of (Declared Value, Declared Unit).
     */
    fun parseDeclaredUsp(declaredUspText: String): Pair<BigDecimal, StatutoryUspUnit>? {
        val lower = declaredUspText.lowercase()
        val num = extractMrpValue(declaredUspText) ?: return null

        val unit = when {
            Regex("""(?i)(?:/|per)\s*100\s*(?:g|gm|gms|gram|grams)\b""").containsMatchIn(lower) -> StatutoryUspUnit.PER_100G
            Regex("""(?i)(?:/|per)\s*100\s*(?:m|ml|mls|millilitres?|milliliters?)\b""").containsMatchIn(lower) -> StatutoryUspUnit.PER_100ML
            Regex("""(?i)(?:/|per)\s*(?:kg|kgs|kilogram|kilograms)\b""").containsMatchIn(lower) -> StatutoryUspUnit.PER_KG
            Regex("""(?i)(?:/|per)\s*(?:l|ltr|ltrs|litre|litres|liter|liters)\b""").containsMatchIn(lower) -> StatutoryUspUnit.PER_L
            Regex("""(?i)(?:/|per)\s*(?:g|gm|gms|gram|grams)\b""").containsMatchIn(lower) -> StatutoryUspUnit.PER_G
            Regex("""(?i)(?:/|per)\s*(?:m|ml|mls|millilitres?|milliliters?)\b""").containsMatchIn(lower) -> StatutoryUspUnit.PER_ML
            Regex("""(?i)(?:/|per)\s*(?:unit|u|piece|pcs?|item)\b""").containsMatchIn(lower) -> StatutoryUspUnit.PER_UNIT
            else -> null
        } ?: return null

        return Pair(num, unit)
    }

    /**
     * Converts any rate (value + unit) to a normalized price per base unit (per gram or per ml or per unit).
     */
    fun toBaseRate(rate: BigDecimal, unit: StatutoryUspUnit): BigDecimal {
        return rate.divide(unit.baseMultiplier, 6, RoundingMode.HALF_UP)
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

        if (quantity.isSmallPackageExempt()) {
            return UspResult(null, null, null, UspStatus.EXEMPT, declaredUspText, null, null)
        }

        val calcResult = calculateUsp(mrpValue, quantity)
        val calculatedCanonical = calcResult.calculatedUsp ?: return calcResult.copy(status = UspStatus.UNABLE_TO_VERIFY)
        val canonicalUnit = quantity.getCanonicalStatutoryUnit()

        if (declaredUspText.isNullOrBlank()) {
            return calcResult.copy(status = UspStatus.MISSING)
        }

        val parsedDeclared = parseDeclaredUsp(declaredUspText)
        if (parsedDeclared == null) {
            // Cannot parse unit or value reliably
            return calcResult.copy(status = UspStatus.UNABLE_TO_VERIFY, declaredUsp = declaredUspText)
        }

        val (declaredValue, declaredUnit) = parsedDeclared

        // Convert both calculated statutory rate and declared rate to the same base rate (per gram or per ml)
        // or compare in declared unit space
        val expectedInDeclaredUnit = mrpValue
            .multiply(declaredUnit.baseMultiplier)
            .divide(quantity.valueInBase, 2, RoundingMode.HALF_UP)

        val delta = expectedInDeclaredUnit.subtract(declaredValue).abs()
        val tolerance = expectedInDeclaredUnit.multiply(BigDecimal("0.02")).max(BigDecimal("0.10"))

        val status = if (delta <= tolerance) UspStatus.VERIFIED else UspStatus.MISMATCH

        return calcResult.copy(
            status = status,
            declaredUsp = declaredUspText,
            mismatchDelta = if (status == UspStatus.MISMATCH) delta else null
        )
    }
}
