package com.sih.domain.classification

object ProductClassifier {

    fun classify(
        fullText: String,
        detectedCommodity: String? = null,
        detectedNetQuantity: String? = null,
        detectedManufacturer: String? = null
    ): ProductClassification {
        val lowerText = fullText.lowercase()
        val lowerCommodity = (detectedCommodity ?: "").lowercase()
        val lowerNetQty = (detectedNetQuantity ?: "").lowercase()
        val lowerMfg = (detectedManufacturer ?: "").lowercase()

        val signals = mutableListOf<String>()

        // 1. Multi-Signal Category Scoring
        val scores = mutableMapOf<ProductCategory, Float>(
            ProductCategory.FOOD to 0.0f,
            ProductCategory.COSMETIC to 0.0f,
            ProductCategory.ELECTRONIC to 0.0f,
            ProductCategory.HOUSEHOLD to 0.0f,
            ProductCategory.AGRICULTURAL to 0.0f,
            ProductCategory.OTHER to 0.0f
        )

        // A. Commodity Name Signal (+0.50)
        if (lowerCommodity.isNotBlank()) {
            when {
                matchesFoodKeywords(lowerCommodity) -> {
                    scores[ProductCategory.FOOD] = (scores[ProductCategory.FOOD] ?: 0f) + 0.50f
                    signals.add("Commodity title match '$detectedCommodity' indicates food (+0.50)")
                }
                matchesCosmeticKeywords(lowerCommodity) -> {
                    scores[ProductCategory.COSMETIC] = (scores[ProductCategory.COSMETIC] ?: 0f) + 0.50f
                    signals.add("Commodity title match '$detectedCommodity' indicates cosmetic (+0.50)")
                }
                matchesElectronicKeywords(lowerCommodity) -> {
                    scores[ProductCategory.ELECTRONIC] = (scores[ProductCategory.ELECTRONIC] ?: 0f) + 0.50f
                    signals.add("Commodity title match '$detectedCommodity' indicates electronics (+0.50)")
                }
                matchesHouseholdKeywords(lowerCommodity) -> {
                    scores[ProductCategory.HOUSEHOLD] = (scores[ProductCategory.HOUSEHOLD] ?: 0f) + 0.50f
                    signals.add("Commodity title match '$detectedCommodity' indicates household item (+0.50)")
                }
                matchesAgriKeywords(lowerCommodity) -> {
                    scores[ProductCategory.AGRICULTURAL] = (scores[ProductCategory.AGRICULTURAL] ?: 0f) + 0.50f
                    signals.add("Commodity title match '$detectedCommodity' indicates agricultural item (+0.50)")
                }
                matchesStationeryKeywords(lowerCommodity) -> {
                    scores[ProductCategory.OTHER] = (scores[ProductCategory.OTHER] ?: 0f) + 0.50f
                    signals.add("Commodity title match '$detectedCommodity' indicates stationery / paper commodity (+0.50)")
                }
                else -> {
                    scores[ProductCategory.OTHER] = (scores[ProductCategory.OTHER] ?: 0f) + 0.40f
                    signals.add("Officer specified commodity title '$detectedCommodity' registered under general packaged commodities (+0.40)")
                }
            }
        }

        // B. Contextual & Regulatory Lexical Signals (+0.25)
        if (lowerText.contains("fssai") || lowerText.contains("nutrition") || lowerText.contains("veg ") || lowerText.contains("energy:") || lowerText.contains("carbohydrate")) {
            scores[ProductCategory.FOOD] = (scores[ProductCategory.FOOD] ?: 0f) + 0.30f
            signals.add("Food regulatory markers detected (FSSAI/Nutritional/Veg) (+0.30)")
        }
        if (lowerText.contains("for external use only") || lowerText.contains("dermatologically") || lowerText.contains("aqua") || lowerText.contains("glycerin") || lowerText.contains("parfum")) {
            scores[ProductCategory.COSMETIC] = (scores[ProductCategory.COSMETIC] ?: 0f) + 0.30f
            signals.add("Cosmetic chemical formulation markers detected (Aqua/Glycerin/Dermatological) (+0.30)")
        }
        if (lowerText.contains("voltage") || lowerText.contains("input:") || lowerText.contains("output:") || lowerText.contains("mah") || lowerText.contains("watt") || lowerText.contains("usb") || lowerText.contains("bluetooth")) {
            scores[ProductCategory.ELECTRONIC] = (scores[ProductCategory.ELECTRONIC] ?: 0f) + 0.35f
            signals.add("Electrical specification markers detected (V/W/mAh/Input/Output) (+0.35)")
        }
        if (lowerText.contains("stain") || lowerText.contains("laundry") || lowerText.contains("bleach") || lowerText.contains("disinfectant") || lowerText.contains("keep out of reach of children")) {
            scores[ProductCategory.HOUSEHOLD] = (scores[ProductCategory.HOUSEHOLD] ?: 0f) + 0.25f
            signals.add("Household cleaning or cautionary statements detected (+0.25)")
        }
        if (lowerText.contains("germination") || lowerText.contains("genetic purity") || lowerText.contains("inert matter") || lowerText.contains("fertilizer")) {
            scores[ProductCategory.AGRICULTURAL] = (scores[ProductCategory.AGRICULTURAL] ?: 0f) + 0.40f
            signals.add("Agricultural certification standards detected (Germination/Purity) (+0.40)")
        }

        // C. Unit Signals (+0.10)
        if (lowerNetQty.contains("ml") || lowerNetQty.contains(" l") || lowerNetQty.contains("litre") || lowerNetQty.contains("liter")) {
            scores[ProductCategory.FOOD] = (scores[ProductCategory.FOOD] ?: 0f) + 0.05f
            scores[ProductCategory.COSMETIC] = (scores[ProductCategory.COSMETIC] ?: 0f) + 0.05f
            signals.add("Liquid volume unit detected ($detectedNetQuantity)")
        }

        // Determine Highest Scoring Category
        val bestEntry = scores.maxByOrNull { it.value }
        val category: ProductCategory
        val subCategory: String
        val classificationConfidence: Float

        if (bestEntry != null && bestEntry.value >= 0.40f) {
            category = bestEntry.key
            classificationConfidence = bestEntry.value.coerceIn(0.55f, 0.98f)
            subCategory = deriveSubCategory(category, lowerCommodity, lowerText)
        } else {
            category = ProductCategory.UNKNOWN
            classificationConfidence = 0.35f
            subCategory = "Undetermined Commodity"
            signals.add("Insufficient lexical signals to establish category beyond ambiguity")
        }

        // 2. Import Status (Explicit Domestic vs. Imported vs. Undetermined)
        val importMarkers = listOf(
            "imported by", "importer:", "country of origin", "made in china", "made in usa",
            "made in vietnam", "made in thailand", "made in germany", "product of", "imported from"
        )
        val domesticMarkers = listOf(
            "mfd. by", "manufactured by", "manufactured in india", "packed by", "made in india", "pvt. ltd.", "ltd."
        )

        val hasImportMarker = importMarkers.any { lowerText.contains(it) }
        val hasDomesticMarker = domesticMarkers.any { lowerText.contains(it) || lowerMfg.contains(it) }

        val importStatus: ImportStatus = when {
            hasImportMarker -> {
                signals.add("Import declaration marker detected on package")
                ImportStatus.IMPORTED
            }
            hasDomesticMarker -> {
                signals.add("Domestic manufacturer/packer marker detected")
                ImportStatus.DOMESTIC
            }
            else -> {
                signals.add("No explicit domestic or import declaration found; marked UNDETERMINED")
                ImportStatus.UNDETERMINED
            }
        }

        // 3. Package Type (POUCH / BOTTLE_CAN / BOX_CARTON / BLISTER_PACK / STANDARD / UNKNOWN)
        val packageType: PackageType = when {
            lowerText.contains("pouch") || lowerText.contains("sachet") || lowerText.contains("spout") || lowerText.contains("laminate") -> {
                signals.add("Flexible pouch packaging detected")
                PackageType.POUCH
            }
            lowerText.contains("bottle") || lowerText.contains("jar") || lowerText.contains("can") || lowerText.contains("tin") -> {
                signals.add("Rigid bottle/can packaging detected")
                PackageType.BOTTLE_CAN
            }
            lowerText.contains("box") || lowerText.contains("carton") -> {
                signals.add("Carton packaging detected")
                PackageType.BOX_CARTON
            }
            lowerText.contains("blister") -> {
                signals.add("Blister packaging detected")
                PackageType.BLISTER_PACK
            }
            category != ProductCategory.UNKNOWN -> PackageType.STANDARD
            else -> PackageType.UNKNOWN
        }

        // 4. Quantity Type (NEVER default to MASS_WEIGHT without evidence!)
        val quantityType: QuantityType = when {
            lowerNetQty.contains("ml") || lowerNetQty.contains("litre") || lowerNetQty.contains("liter") || lowerNetQty.endsWith("l") -> {
                signals.add("Liquid volume unit detected ($detectedNetQuantity) -> VOLUME_LIQUID")
                QuantityType.VOLUME_LIQUID
            }
            lowerNetQty.contains("kg") || lowerNetQty.contains("gm") || lowerNetQty.contains("gram") || lowerNetQty.contains("g") -> {
                signals.add("Mass/weight unit detected ($detectedNetQuantity) -> MASS_WEIGHT")
                QuantityType.MASS_WEIGHT
            }
            lowerNetQty.contains("cm") || lowerNetQty.contains("mm") || lowerNetQty.contains("meter") || lowerNetQty.contains("m") -> {
                signals.add("Linear length unit detected -> LENGTH")
                QuantityType.LENGTH
            }
            lowerNetQty.contains("sq") -> {
                signals.add("Area unit detected -> AREA")
                QuantityType.AREA
            }
            lowerNetQty.contains(" u") || lowerNetQty.contains(" n") || lowerNetQty.contains("piece") || lowerNetQty.contains("count") -> {
                signals.add("Unit count detected -> COUNT_NUMBER")
                QuantityType.COUNT_NUMBER
            }
            else -> {
                signals.add("Quantity unit not conclusively detected -> UNKNOWN")
                QuantityType.UNKNOWN
            }
        }

        return ProductClassification(
            category = category,
            subCategory = subCategory,
            importStatus = importStatus,
            packageType = packageType,
            quantityType = quantityType,
            confidence = classificationConfidence,
            classificationSignals = signals
        )
    }

    private fun matchesFoodKeywords(target: String): Boolean {
        val foodKeywords = listOf(
            "oil", "atta", "flour", "salt", "sugar", "milk", "tea", "coffee", "biscuit",
            "chip", "snack", "ketchup", "sauce", "spice", "masala", "chocolate", "juice",
            "water", "beverage", "rice", "dal", "pulse", "wheat", "noodle", "pasta", "butter"
        )
        return foodKeywords.any { target.contains(it) }
    }

    private fun matchesCosmeticKeywords(target: String): Boolean {
        val cosmeticKeywords = listOf(
            "shampoo", "soap", "cream", "lotion", "serum", "perfume", "deodorant",
            "hair", "skin", "face", "conditioner", "gel", "toothpaste", "cosmetic",
            "sunscreen", "lipstick", "cleanser", "moisturizer"
        )
        return cosmeticKeywords.any { target.contains(it) }
    }

    private fun matchesElectronicKeywords(target: String): Boolean {
        val electronicKeywords = listOf(
            "phone", "charger", "cable", "battery", "led", "bulb", "adapter",
            "usb", "bluetooth", "earphone", "electronic", "power bank", "smartwatch"
        )
        return electronicKeywords.any { target.contains(it) }
    }

    private fun matchesHouseholdKeywords(target: String): Boolean {
        val householdKeywords = listOf(
            "detergent", "cleaner", "soap powder", "bleach", "disinfectant",
            "repellent", "mosquito", "scrub", "tissue", "foil", "dishwash"
        )
        return householdKeywords.any { target.contains(it) }
    }

    private fun matchesAgriKeywords(target: String): Boolean {
        val agriKeywords = listOf("seed", "fertilizer", "pesticide", "compost", "urea", "feed")
        return agriKeywords.any { target.contains(it) }
    }

    private fun matchesStationeryKeywords(target: String): Boolean {
        val stationeryKeywords = listOf(
            "notebook", "book", "exercise book", "register", "drawing book",
            "long book", "pen", "pencil", "stationery", "paper", "diary", "pad"
        )
        return stationeryKeywords.any { target.contains(it) }
    }

    private fun deriveSubCategory(category: ProductCategory, commodity: String, text: String): String {
        val target = "$commodity $text"
        return when (category) {
            ProductCategory.FOOD -> when {
                target.contains("oil") -> "Edible Vegetable Oil"
                target.contains("atta") || target.contains("flour") -> "Wheat Flour & Ground Grain"
                target.contains("salt") -> "Edible Common Salt"
                target.contains("ketchup") || target.contains("sauce") -> "Culinary Sauces & Condiments"
                target.contains("chip") || target.contains("biscuit") || target.contains("snack") -> "Packaged Snacks & Confectionery"
                target.contains("tea") || target.contains("coffee") || target.contains("juice") -> "Packaged Beverages"
                else -> "Packaged Food Product"
            }
            ProductCategory.COSMETIC -> when {
                target.contains("shampoo") || target.contains("conditioner") -> "Hair Care Preparation"
                target.contains("soap") || target.contains("cleanser") -> "Bathing Bar & Skin Cleanser"
                target.contains("cream") || target.contains("lotion") -> "Skincare & Moisturizer"
                target.contains("toothpaste") -> "Dental & Oral Hygiene"
                else -> "Personal Care & Cosmetics"
            }
            ProductCategory.ELECTRONIC -> when {
                target.contains("charger") || target.contains("adapter") -> "Power Adapters & Charging Devices"
                target.contains("battery") -> "Energy Storage & Batteries"
                target.contains("led") || target.contains("bulb") -> "Lamps & Luminaires"
                else -> "Consumer Electronics & IT Hardware"
            }
            ProductCategory.HOUSEHOLD -> when {
                target.contains("detergent") || target.contains("laundry") -> "Fabric Wash & Laundry Detergent"
                target.contains("mosquito") || target.contains("repellent") -> "Household Insecticide / Repellent"
                else -> "Household Cleaning & Sanitary Goods"
            }
            ProductCategory.AGRICULTURAL -> "Agricultural Seeds & Inputs"
            ProductCategory.OTHER -> when {
                matchesStationeryKeywords(target) -> "Stationery & Paper Products"
                else -> "General Packaged Commodity"
            }
            ProductCategory.UNKNOWN -> "Undetermined Commodity"
        }
    }
}
