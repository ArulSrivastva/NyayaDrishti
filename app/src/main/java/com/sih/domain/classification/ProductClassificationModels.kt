package com.sih.domain.classification

import com.google.gson.annotations.SerializedName

enum class ProductCategory {
    @SerializedName("FOOD") FOOD,
    @SerializedName("COSMETIC") COSMETIC,
    @SerializedName("HOUSEHOLD") HOUSEHOLD,
    @SerializedName("ELECTRONIC") ELECTRONIC,
    @SerializedName("AGRICULTURAL") AGRICULTURAL,
    @SerializedName("OTHER") OTHER,
    @SerializedName("UNKNOWN") UNKNOWN;

    val displayName: String
        get() = when (this) {
            FOOD -> "Food & Groceries"
            COSMETIC -> "Cosmetic & Personal Care"
            HOUSEHOLD -> "Household & Cleaning"
            ELECTRONIC -> "Consumer Electronics"
            AGRICULTURAL -> "Agricultural / Seeds"
            OTHER -> "General Packaged Commodity"
            UNKNOWN -> "Undetermined Category"
        }

    val iconSymbol: String
        get() = when (this) {
            FOOD -> "🍞"
            COSMETIC -> "🧴"
            HOUSEHOLD -> "🧹"
            ELECTRONIC -> "⚡"
            AGRICULTURAL -> "🌱"
            OTHER -> "📦"
            UNKNOWN -> "❓"
        }
}

enum class ImportStatus {
    @SerializedName("DOMESTIC") DOMESTIC,
    @SerializedName("IMPORTED") IMPORTED,
    @SerializedName("UNDETERMINED") UNDETERMINED
}

enum class PackageType {
    @SerializedName("POUCH") POUCH,
    @SerializedName("BOTTLE_CAN") BOTTLE_CAN,
    @SerializedName("BOX_CARTON") BOX_CARTON,
    @SerializedName("BLISTER_PACK") BLISTER_PACK,
    @SerializedName("STANDARD") STANDARD,
    @SerializedName("UNKNOWN") UNKNOWN
}

enum class QuantityType {
    @SerializedName("MASS_WEIGHT") MASS_WEIGHT,
    @SerializedName("VOLUME_LIQUID") VOLUME_LIQUID,
    @SerializedName("LENGTH") LENGTH,
    @SerializedName("AREA") AREA,
    @SerializedName("COUNT_NUMBER") COUNT_NUMBER,
    @SerializedName("UNKNOWN") UNKNOWN
}

data class ProductClassification(
    @SerializedName("category") val category: ProductCategory = ProductCategory.UNKNOWN,
    @SerializedName("sub_category") val subCategory: String = "Undetermined",
    @SerializedName("import_status") val importStatus: ImportStatus = ImportStatus.UNDETERMINED,
    @SerializedName("package_type") val packageType: PackageType = PackageType.UNKNOWN,
    @SerializedName("quantity_type") val quantityType: QuantityType = QuantityType.UNKNOWN,
    @SerializedName("confidence") val confidence: Float = 0.50f,
    @SerializedName("classification_signals") val classificationSignals: List<String> = emptyList()
)

data class ApplicableRule(
    @SerializedName("rule_id") val ruleId: String,
    @SerializedName("reference") val reference: String,
    @SerializedName("title") val title: String,
    @SerializedName("reason") val reason: String,
    @SerializedName("mandatory") val mandatory: Boolean = true
)

data class ApplicableRuleSet(
    @SerializedName("active_rules") val activeRules: List<ApplicableRule> = emptyList(),
    @SerializedName("excluded_rules") val excludedRules: List<ApplicableRule> = emptyList(),
    @SerializedName("permitted_units") val permittedUnits: List<String> = emptyList(),
    @SerializedName("statutory_references") val statutoryReferences: List<String> = emptyList(),
    @SerializedName("applicability_confidence") val applicabilityConfidence: Float = 0.90f
)
