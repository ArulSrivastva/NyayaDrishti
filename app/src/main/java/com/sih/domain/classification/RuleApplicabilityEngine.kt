package com.sih.domain.classification

object RuleApplicabilityEngine {

    fun resolveApplicability(
        classification: ProductClassification,
        numericQuantity: Float = 0f
    ): ApplicableRuleSet {
        val activeRules = mutableListOf<ApplicableRule>()
        val excludedRules = mutableListOf<ApplicableRule>()
        val statutoryRefs = mutableListOf<String>()

        // Base Reference
        statutoryRefs.add("Legal Metrology (Packaged Commodities) Rules, 2011")

        // 1. Rule 6(1)(a) — Common / Generic Commodity Name
        activeRules.add(
            ApplicableRule(
                ruleId = "R6_004",
                reference = "Rule 6(1)(a)",
                title = "Generic Commodity Name",
                reason = "Mandatory declaration on principal display panel across all packaged commodities.",
                mandatory = true
            )
        )

        // 2. Rule 6(1)(b) & Rule 10(1) — Manufacturer / Packer Details
        activeRules.add(
            ApplicableRule(
                ruleId = "R6_001",
                reference = "Rule 6(1)(b) & Rule 10(1)",
                title = "Manufacturer / Packer Name & Address",
                reason = "Name and complete address of manufacturer or packer is mandatory. Rule 10(1) packer equivalence applies.",
                mandatory = true
            )
        )

        // 3. Rule 6(1)(c) — Importer Declaration & Country of Origin
        when (classification.importStatus) {
            ImportStatus.IMPORTED -> {
                activeRules.add(
                    ApplicableRule(
                        ruleId = "R6_003",
                        reference = "Rule 6(1)(c)",
                        title = "Importer Name, Address & Country of Origin",
                        reason = "Imported product classification requires name, address of the importer and Country of Origin.",
                        mandatory = true
                    )
                )
                statutoryRefs.add("Rule 6(1)(c) Import Provisions")
            }
            ImportStatus.DOMESTIC -> {
                excludedRules.add(
                    ApplicableRule(
                        ruleId = "R6_003",
                        reference = "Rule 6(1)(c)",
                        title = "Importer Declaration",
                        reason = "Domestic product classification. Manufacturer/packer declaration satisfies statutory chain (Rule 10(1)).",
                        mandatory = false
                    )
                )
            }
            ImportStatus.UNDETERMINED -> {
                activeRules.add(
                    ApplicableRule(
                        ruleId = "R6_003",
                        reference = "Rule 6(1)(c)",
                        title = "Importer / Origin Verification",
                        reason = "Import status undetermined from OCR signals. Officer verification required to confirm if imported.",
                        mandatory = false
                    )
                )
            }
        }

        // 4. Rule 6(1)(d) — Net Quantity in Standard Metric Units
        val permittedUnits = when (classification.quantityType) {
            QuantityType.VOLUME_LIQUID -> listOf("ml", "l", "L")
            QuantityType.MASS_WEIGHT -> listOf("g", "kg")
            QuantityType.LENGTH -> listOf("cm", "m")
            QuantityType.AREA -> listOf("sq cm", "sq m")
            QuantityType.COUNT_NUMBER -> listOf("N", "U")
            QuantityType.UNKNOWN -> listOf("g", "kg", "ml", "l", "N", "U")
        }

        activeRules.add(
            ApplicableRule(
                ruleId = "R6_005",
                reference = "Rule 6(1)(d)",
                title = "Net Quantity Statement",
                reason = "Mandatory declaration in standard metric units (${permittedUnits.joinToString(", ")}).",
                mandatory = true
            )
        )

        // 5. Rule 6(1)(e) — Month & Year of Packing / Expiry
        val dateReason = if (classification.category == ProductCategory.FOOD || classification.category == ProductCategory.COSMETIC) {
            "Month & year of manufacture/pre-packing is mandatory. Best Before / Expiry declaration required under category standards."
        } else {
            "Month and year of manufacture or pre-packing is mandatory."
        }
        activeRules.add(
            ApplicableRule(
                ruleId = "R6_006",
                reference = "Rule 6(1)(e)",
                title = "Date of Packing / Expiry",
                reason = dateReason,
                mandatory = true
            )
        )

        // 6. Rule 6(1)(f) — Maximum Retail Price (MRP)
        activeRules.add(
            ApplicableRule(
                ruleId = "R6_007",
                reference = "Rule 6(1)(f)",
                title = "Maximum Retail Price (MRP)",
                reason = "Retail sale price in INR inclusive of all taxes must be declared clearly.",
                mandatory = true
            )
        )

        // 7. Rule 6(1)(g) — Consumer Grievance Redressal
        activeRules.add(
            ApplicableRule(
                ruleId = "R6_008",
                reference = "Rule 6(1)(g)",
                title = "Consumer Grievance Redressal Details",
                reason = "Name, address, phone number and email of person or office for consumer complaints.",
                mandatory = true
            )
        )

        // 8. Rule 6(11) — Unit Sale Price (USP)
        if (numericQuantity > 10f || numericQuantity == 0f) {
            activeRules.add(
                ApplicableRule(
                    ruleId = "R6_011",
                    reference = "Rule 6(11)",
                    title = "Unit Sale Price (USP)",
                    reason = "Mandatory declaration under Rule 6(11) (per g/100g for <1kg, per ml/100ml for <1L, or per kg/L for >=1kg/L).",
                    mandatory = true
                )
            )
            statutoryRefs.add("Rule 6(11) Unit Sale Price Amendment")
        } else {
            excludedRules.add(
                ApplicableRule(
                    ruleId = "R6_011",
                    reference = "Rule 6(11)",
                    title = "Unit Sale Price (USP)",
                    reason = "Small package (net content <= 10g / 10ml) exempt under Rule 26 statutory provisions.",
                    mandatory = false
                )
            )
        }

        // 9. Rule 7(1) & Schedule II — Font Height
        activeRules.add(
            ApplicableRule(
                ruleId = "Rule 7(1)",
                reference = "Rule 7(1) & Schedule II",
                title = "Minimum Font Height & Numerals",
                reason = "Statutory minimum character and numeral height based on package surface area.",
                mandatory = true
            )
        )

        // Category-Specific Schedule References
        when (classification.category) {
            ProductCategory.FOOD -> {
                statutoryRefs.add("First & Second Schedule (Standard Quantities)")
                statutoryRefs.add("FSSAI Labeling & Packaging Harmonization")
            }
            ProductCategory.COSMETIC -> {
                statutoryRefs.add("Second Schedule & Drugs and Cosmetics Rules Harmonized")
            }
            ProductCategory.ELECTRONIC -> {
                statutoryRefs.add("Electronics & IT Goods (Compulsory Registration) Order")
            }
            ProductCategory.AGRICULTURAL -> {
                statutoryRefs.add("Seeds Act & Legal Metrology Agricultural Schedules")
            }
            else -> {
                statutoryRefs.add("General Packaged Commodities Schedule II")
            }
        }

        val applicabilityConfidence = (classification.confidence * 0.95f).coerceIn(0.60f, 0.99f)

        return ApplicableRuleSet(
            activeRules = activeRules,
            excludedRules = excludedRules,
            permittedUnits = permittedUnits,
            statutoryReferences = statutoryRefs,
            applicabilityConfidence = applicabilityConfidence
        )
    }
}
