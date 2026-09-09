package com.sih.domain.classification

object RuleApplicabilityEngine {

    fun resolveApplicability(
        classification: ProductClassification,
        numericQuantity: Float = 0f,
        officerCategory: String? = null,
        officerUnitBasis: String? = null,
        officerSchedule: String? = null,
        activeChecklist: List<String> = emptyList()
    ): ApplicableRuleSet {
        val activeRules = mutableListOf<ApplicableRule>()
        val excludedRules = mutableListOf<ApplicableRule>()
        val statutoryRefs = mutableListOf<String>()

        // Base Reference
        statutoryRefs.add("Legal Metrology (Packaged Commodities) Rules, 2011")

        // Pre-Scan Statutory Schedule Override / Addition
        if (!officerSchedule.isNullOrBlank()) {
            statutoryRefs.add("Officer Selected Schedule: $officerSchedule")
        }

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

        // 4. Rule 6(1)(d) — Net Quantity in Standard Metric Units (Officer Unit Basis takes precedence)
        val permittedUnits = if (!officerUnitBasis.isNullOrBlank()) {
            when {
                officerUnitBasis.contains("Mass", ignoreCase = true) || officerUnitBasis.contains("kg", ignoreCase = true) -> listOf("g", "kg")
                officerUnitBasis.contains("Volume", ignoreCase = true) || officerUnitBasis.contains("ml", ignoreCase = true) || officerUnitBasis.contains("L", ignoreCase = true) -> listOf("ml", "l", "L")
                officerUnitBasis.contains("Count", ignoreCase = true) || officerUnitBasis.contains("N", ignoreCase = true) || officerUnitBasis.contains("U", ignoreCase = true) -> listOf("N", "U", "Set", "units")
                officerUnitBasis.contains("Dimension", ignoreCase = true) || officerUnitBasis.contains("cm", ignoreCase = true) || officerUnitBasis.contains("m", ignoreCase = true) -> listOf("cm", "m", "mm", "sq cm", "sq m")
                else -> listOf("g", "kg", "ml", "l", "N", "U")
            }
        } else {
            when (classification.quantityType) {
                QuantityType.VOLUME_LIQUID -> listOf("ml", "l", "L")
                QuantityType.MASS_WEIGHT -> listOf("g", "kg")
                QuantityType.LENGTH -> listOf("cm", "m")
                QuantityType.AREA -> listOf("sq cm", "sq m")
                QuantityType.COUNT_NUMBER -> listOf("N", "U")
                QuantityType.UNKNOWN -> listOf("g", "kg", "ml", "l", "N", "U")
            }
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
        val isFoodOrCosmetic = classification.category == ProductCategory.FOOD || 
                              classification.category == ProductCategory.COSMETIC ||
                              officerCategory?.contains("Food", ignoreCase = true) == true ||
                              officerCategory?.contains("Cosmetic", ignoreCase = true) == true

        val dateReason = if (isFoodOrCosmetic) {
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

        // 10. Optional Checklist Rules
        if (activeChecklist.any { it.contains("Veg", ignoreCase = true) }) {
            activeRules.add(
                ApplicableRule(
                    ruleId = "R_VEG_DOT",
                    reference = "Rule 6 / FSSAI & Cosmetic Color Code",
                    title = "Vegetarian / Non-Vegetarian Logo",
                    reason = "Mandatory green/brown color dot logo verification.",
                    mandatory = true
                )
            )
        }

        if (activeChecklist.any { it.contains("Drained", ignoreCase = true) }) {
            activeRules.add(
                ApplicableRule(
                    ruleId = "R_DRAINED_WT",
                    reference = "Rule 24",
                    title = "Drained Weight Statement",
                    reason = "Commodities packed in liquid medium require declaration of drained weight.",
                    mandatory = true
                )
            )
        }

        if (officerSchedule?.contains("Second Schedule", ignoreCase = true) == true ||
            activeChecklist.any { it.contains("Second Schedule", ignoreCase = true) || it.contains("Standard Pack", ignoreCase = true) }) {
            activeRules.add(
                ApplicableRule(
                    ruleId = "R_SCHED_2",
                    reference = "Rule 5 & Second Schedule",
                    title = "Standard Pack Size Compliance",
                    reason = "Commodity subject to mandatory standard quantity packaging prescribed under Second Schedule.",
                    mandatory = true
                )
            )
            statutoryRefs.add("Second Schedule Mandatory Pack Sizes")
        }

        // Category-Specific Schedule References
        val effectiveCategory = when {
            officerCategory?.contains("Food", ignoreCase = true) == true -> ProductCategory.FOOD
            officerCategory?.contains("Cosmetic", ignoreCase = true) == true -> ProductCategory.COSMETIC
            officerCategory?.contains("Electronic", ignoreCase = true) == true || officerCategory?.contains("Hardware", ignoreCase = true) == true -> ProductCategory.ELECTRONIC
            officerCategory?.contains("Textile", ignoreCase = true) == true -> ProductCategory.OTHER
            else -> classification.category
        }

        when (effectiveCategory) {
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

        // Active Checklist Scoping Filter: If officer specified an active checklist, exclude rules outside scope
        val finalActiveRules = mutableListOf<ApplicableRule>()
        if (activeChecklist.isNotEmpty()) {
            val hasRule6 = activeChecklist.any { it.contains("Rule 6 Mandatory", ignoreCase = true) }
            val hasUsp = activeChecklist.any { it.contains("USP", ignoreCase = true) }
            val hasFont = activeChecklist.any { it.contains("Font", ignoreCase = true) }
            val hasOrigin = activeChecklist.any { it.contains("Origin", ignoreCase = true) || it.contains("Barcode", ignoreCase = true) }
            val hasVeg = activeChecklist.any { it.contains("Veg", ignoreCase = true) }
            val hasDrained = activeChecklist.any { it.contains("Drained", ignoreCase = true) }
            val hasSched2 = activeChecklist.any { it.contains("Second Schedule", ignoreCase = true) || it.contains("Standard Pack", ignoreCase = true) } ||
                            officerSchedule?.contains("Second Schedule", ignoreCase = true) == true

            for (rule in activeRules) {
                val isIncluded = when (rule.ruleId) {
                    "R6_004", "R6_001", "R6_005", "R6_006", "R6_007", "R6_008" -> hasRule6
                    "R6_011" -> hasUsp
                    "Rule 7(1)" -> hasFont
                    "R6_003" -> hasOrigin
                    "R_VEG_DOT" -> hasVeg
                    "R_DRAINED_WT" -> hasDrained
                    "R_SCHED_2" -> hasSched2
                    else -> true
                }

                if (isIncluded) {
                    finalActiveRules.add(rule)
                } else {
                    excludedRules.add(
                        rule.copy(
                            mandatory = false,
                            reason = "Excluded by Officer Active Checklist Scope."
                        )
                    )
                }
            }
        } else {
            finalActiveRules.addAll(activeRules)
        }

        val applicabilityConfidence = (classification.confidence * 0.95f).coerceIn(0.60f, 0.99f)

        return ApplicableRuleSet(
            activeRules = finalActiveRules,
            excludedRules = excludedRules,
            permittedUnits = permittedUnits,
            statutoryReferences = statutoryRefs.distinct(),
            applicabilityConfidence = applicabilityConfidence
        )
    }
}
