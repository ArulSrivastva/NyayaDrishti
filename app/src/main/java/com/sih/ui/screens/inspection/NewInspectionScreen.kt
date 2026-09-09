package com.sih.ui.screens.inspection

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.sih.repository.InspectionRepository
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.sih.util.Localization
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewInspectionScreen(
    selectedLanguage: String,
    onNavigateBack: () -> Unit,
    onScanCamera: (String) -> Unit,
    onImagesSelected: (List<android.net.Uri>, String) -> Unit,
    onOnlineListing: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val activeDraft = InspectionRepository.currentDraft
    var commodityName by remember { mutableStateOf(InspectionRepository.activeCommodityName ?: "") }
    var establishmentName by remember { mutableStateOf(activeDraft?.establishmentName ?: InspectionRepository.currentEstablishmentName ?: "") }
    var location by remember { mutableStateOf(activeDraft?.location ?: InspectionRepository.currentLocation ?: "") }
    var inspectionType by remember { mutableStateOf(activeDraft?.inspectionType ?: InspectionRepository.currentInspectionType ?: "Routine Inspection") }
    var numberOfPackages by remember { mutableStateOf(InspectionRepository.currentNumberOfPackages ?: "1") }
    var remarks by remember { mutableStateOf(InspectionRepository.currentRemarks ?: "") }
    var showError by remember { mutableStateOf(false) }

    val categoryDisplayItems = listOf(
        "Food & Beverages" to "Food & Beverages",
        "Cosmetics & Personal Care" to "Cosmetics & Personal Care",
        "Electronics & Hardware" to "Electronics & Hardware",
        "Textiles & Sheet Materials" to "Textiles & Sheet Materials",
        "General Packaged Commodity" to "General Packaged Commodity"
    )

    val unitBasisDisplayItems = listOf(
        "Mass (g / kg)" to "Mass (g / kg)",
        "Volume (ml / L)" to "Volume (ml / L)",
        "Count (N / U / Set)" to "Count (N / U / Set)",
        "Dimensions (cm / m / m²)" to "Dimensions (cm / m / m²)"
    )

    var isInstitutionalBulk by remember { mutableStateOf(false) }

    fun isSecondScheduleCommodity(name: String, category: String): Boolean {
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

    fun inferStatutorySchedule(name: String, category: String, isBulk: Boolean): String {
        if (isBulk) {
            return "Fourth Schedule (Institutional Consumer Exemption)"
        }
        if (isSecondScheduleCommodity(name, category)) {
            return "Second Schedule (Prescribed Standard Quantities) & First Schedule"
        }
        return "First Schedule (Standard Minimum Font & Numerals)"
    }

    fun getScheduleRationale(name: String, category: String, isBulk: Boolean): String {
        if (isBulk) {
            return "Institutional / industrial consumer package exempt from standard retail labeling under Rule 3."
        }
        if (isSecondScheduleCommodity(name, category)) {
            val commodityDisplay = name.ifBlank { "Selected commodity" }
            return "'$commodityDisplay' is subject to Rule 5 & Second Schedule mandatory standard pack sizes."
        }
        return "Standard retail packaged commodity governed by Rule 7 font height & PDP area gates."
    }

    fun parseRuleBadgeAndTitle(ruleName: String): Pair<String, String> {
        return when {
            ruleName.startsWith("Rule 6(11)") -> "Rule 6(11)" to "Unit Sale Price (USP)"
            ruleName.startsWith("Rule 6") -> "Rule 6" to "Mandatory Declarations"
            ruleName.startsWith("Rule 7") -> "Rule 7" to "Font Height & Area Gate"
            ruleName.contains("Veg", ignoreCase = true) -> "Logo" to "Veg / Non-Veg Dot"
            ruleName.contains("Drained", ignoreCase = true) -> "Rule 24" to "Drained Weight"
            ruleName.contains("Barcode", ignoreCase = true) || ruleName.contains("Origin", ignoreCase = true) -> "Origin" to "Barcode & Country of Origin"
            ruleName.contains("Second Schedule", ignoreCase = true) -> "Rule 5" to "Standard Pack Size (Sched II)"
            else -> "Rule" to ruleName
        }
    }

    fun getApplicableChecklistOptions(category: String, schedule: String): List<String> {
        val options = mutableListOf(
            "Rule 6 Mandatory Declarations",
            "Rule 6(11) Unit Sale Price (USP)",
            "Rule 7 Font Height & Area Gate",
            "Barcode / Origin Check"
        )
        if (category == "Food & Beverages" || category == "Cosmetics & Personal Care") {
            options.add(3, "Veg/Non-Veg Dot Check")
        }
        if (category == "Food & Beverages") {
            options.add(4, "Drained Weight Check")
        }
        if (schedule.contains("Second Schedule", ignoreCase = true)) {
            options.add("Second Schedule Standard Pack Size")
        }
        if (category == "Textiles & Sheet Materials") {
            options.remove("Rule 6(11) Unit Sale Price (USP)")
        }
        return options
    }

    fun computeDefaultChecklist(category: String, schedule: String, commodity: String = ""): List<String> {
        val applicable = getApplicableChecklistOptions(category, schedule)
        val defaults = applicable.toMutableList()
        val lower = commodity.lowercase().trim()
        if (category == "Food & Beverages" && !lower.contains("ketchup") && !lower.contains("sauce") && !lower.contains("pickle") && !lower.contains("canned")) {
            defaults.remove("Drained Weight Check")
        }
        return defaults
    }

    var selectedCategory by remember {
        mutableStateOf(activeDraft?.selectedCategory ?: InspectionRepository.currentSelectedCategory ?: "Food & Beverages")
    }
    var selectedUnitBasis by remember {
        mutableStateOf(activeDraft?.selectedUnitBasis ?: InspectionRepository.currentSelectedUnitBasis ?: "Mass (g / kg)")
    }
    var selectedSchedule by remember {
        mutableStateOf(
            activeDraft?.selectedSchedule 
                ?: InspectionRepository.currentSelectedSchedule 
                ?: inferStatutorySchedule(commodityName, selectedCategory, isInstitutionalBulk)
        )
    }
    var activeChecklist by remember {
        mutableStateOf(
            if (activeDraft?.activeChecklistJson != null) {
                try {
                    val listType = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                    com.google.gson.Gson().fromJson<List<String>>(activeDraft.activeChecklistJson, listType) ?: computeDefaultChecklist(selectedCategory, selectedSchedule, commodityName)
                } catch (e: Exception) {
                    computeDefaultChecklist(selectedCategory, selectedSchedule, commodityName)
                }
            } else if (InspectionRepository.currentActiveChecklist.isNotEmpty()) {
                InspectionRepository.currentActiveChecklist
            } else {
                computeDefaultChecklist(selectedCategory, selectedSchedule, commodityName)
            }
        )
    }

    var isChecklistExpanded by remember { mutableStateOf(false) }

    fun onCategorySelected(newCat: String) {
        selectedCategory = newCat
        when (newCat) {
            "Food & Beverages" -> {
                if (selectedUnitBasis.startsWith("Count") || selectedUnitBasis.startsWith("Dimensions")) {
                    selectedUnitBasis = "Mass (g / kg)"
                }
            }
            "Cosmetics & Personal Care" -> {
                if (selectedUnitBasis.startsWith("Count") || selectedUnitBasis.startsWith("Dimensions")) {
                    selectedUnitBasis = "Volume (ml / L)"
                }
            }
            "Electronics & Hardware" -> {
                selectedUnitBasis = "Count (N / U / Set)"
            }
            "General Packaged Commodity" -> {
                selectedUnitBasis = "Count (N / U / Set)"
            }
            "Textiles & Sheet Materials" -> {
                selectedUnitBasis = "Dimensions (cm / m / m²)"
            }
        }
        selectedSchedule = inferStatutorySchedule(commodityName, newCat, isInstitutionalBulk)
        activeChecklist = computeDefaultChecklist(newCat, selectedSchedule, commodityName)
    }

    fun onScheduleSelected(newSchedule: String) {
        selectedSchedule = newSchedule
        activeChecklist = computeDefaultChecklist(selectedCategory, newSchedule, commodityName)
    }

    fun applyPresetForCommodity(name: String) {
        val lower = name.lowercase().trim()
        if (lower.isBlank()) return
        when {
            lower.contains("oil") || lower.contains("milk") || lower.contains("juice") || lower.contains("beverage") || lower.contains("drink") || lower.contains("water") || lower.contains("syrup") || lower.contains("ghee") -> {
                selectedCategory = "Food & Beverages"
                selectedUnitBasis = "Volume (ml / L)"
            }
            lower.contains("ketchup") || lower.contains("sauce") || lower.contains("pickle") || lower.contains("canned") -> {
                selectedCategory = "Food & Beverages"
                selectedUnitBasis = "Mass (g / kg)"
            }
            lower.contains("salt") || lower.contains("atta") || lower.contains("biscuit") || lower.contains("flour") || lower.contains("rice") || lower.contains("dal") || lower.contains("sugar") || lower.contains("tea") || lower.contains("coffee") || lower.contains("snack") || lower.contains("chips") || lower.contains("food") -> {
                selectedCategory = "Food & Beverages"
                selectedUnitBasis = "Mass (g / kg)"
            }
            lower.contains("soap") || lower.contains("detergent") || lower.contains("cream") || lower.contains("toothpaste") || lower.contains("powder") -> {
                selectedCategory = "Cosmetics & Personal Care"
                selectedUnitBasis = "Mass (g / kg)"
            }
            lower.contains("shampoo") || lower.contains("lotion") || lower.contains("perfume") || lower.contains("deodorant") -> {
                selectedCategory = "Cosmetics & Personal Care"
                selectedUnitBasis = "Volume (ml / L)"
            }
            lower.contains("notebook") || lower.contains("book") || lower.contains("pen") || lower.contains("pencil") -> {
                selectedCategory = "General Packaged Commodity"
                selectedUnitBasis = "Count (N / U / Set)"
            }
            lower.contains("cable") || lower.contains("charger") || lower.contains("bulb") || lower.contains("battery") || lower.contains("headphone") || lower.contains("plug") || lower.contains("switch") -> {
                selectedCategory = "Electronics & Hardware"
                selectedUnitBasis = "Count (N / U / Set)"
            }
            lower.contains("towel") || lower.contains("sheet") || lower.contains("shirt") || lower.contains("cloth") || lower.contains("fabric") -> {
                selectedCategory = "Textiles & Sheet Materials"
                selectedUnitBasis = "Dimensions (cm / m / m²)"
            }
        }
        selectedSchedule = inferStatutorySchedule(name, selectedCategory, isInstitutionalBulk)
        activeChecklist = computeDefaultChecklist(selectedCategory, selectedSchedule, name)
    }

    fun validatePreScanConfiguration(): Boolean {
        if (commodityName.trim().isBlank()) {
            showError = true
            Toast.makeText(
                context,
                Localization.getString("commodity_required_error", selectedLanguage),
                Toast.LENGTH_LONG
            ).show()
            scope.launch {
                scrollState.animateScrollTo(0)
                try {
                    focusRequester.requestFocus()
                } catch (e: Exception) {
                    // focus request fallback
                }
            }
            return false
        }
        if (selectedCategory.isBlank() || selectedUnitBasis.isBlank() || selectedSchedule.isBlank() || activeChecklist.isEmpty()) {
            Toast.makeText(
                context,
                Localization.getString("config_required_error", selectedLanguage),
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        return true
    }

    fun createAndSaveDraft(): String {
        val db = com.sih.data.local.LocalDatabase.getInstance(context)
        val inspId = InspectionRepository.currentDraft?.inspectionId ?: db.getNextInspectionId().toString()
        val officerId = com.sih.network.ApiClient.getTokenManager()?.getUserId() ?: 1
        val gson = com.google.gson.Gson()
        val draft = com.sih.model.InspectionDraft(
            inspectionId = inspId,
            officerId = officerId,
            establishmentName = establishmentName.ifBlank { "Retail Store / Establishment" },
            inspectionType = inspectionType,
            location = location.ifBlank { "Field Inspection Zone" },
            state = com.sih.model.DraftState.CREATED,
            capturedEvidenceIds = emptyList(),
            qualityResultsJson = null,
            ocrResultsJson = null,
            extractedDeclarationsJson = null,
            classificationJson = null,
            applicableRulesJson = null,
            complianceResultsJson = null,
            reviewState = null,
            signOffJson = null,
            lastUpdated = java.time.LocalDateTime.now().toString(),
            selectedCategory = selectedCategory,
            selectedUnitBasis = selectedUnitBasis,
            selectedSchedule = selectedSchedule,
            activeChecklistJson = gson.toJson(activeChecklist)
        )
        InspectionRepository.currentInspectionId = inspId.toIntOrNull()
        InspectionRepository.currentEstablishmentName = establishmentName.ifBlank { "Retail Store" }
        InspectionRepository.currentInspectionType = inspectionType
        InspectionRepository.currentLocation = location.ifBlank { "Field Inspection Zone" }
        InspectionRepository.currentNumberOfPackages = numberOfPackages
        InspectionRepository.currentRemarks = remarks
        InspectionRepository.currentSelectedCategory = selectedCategory
        InspectionRepository.currentSelectedUnitBasis = selectedUnitBasis
        InspectionRepository.currentSelectedSchedule = selectedSchedule
        InspectionRepository.currentActiveChecklist = activeChecklist
        InspectionRepository.saveDraft(context, draft)
        return inspId
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            createAndSaveDraft()
            InspectionRepository.clearCurrentInspection()
            InspectionRepository.activeImageUris = uris
            InspectionRepository.activeCommodityName = commodityName.trim()
            InspectionRepository.currentSelectedCategory = selectedCategory
            InspectionRepository.currentSelectedUnitBasis = selectedUnitBasis
            InspectionRepository.currentSelectedSchedule = selectedSchedule
            InspectionRepository.currentActiveChecklist = activeChecklist
            onImagesSelected(uris, commodityName.trim())
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            createAndSaveDraft()
            InspectionRepository.clearCurrentInspection()
            InspectionRepository.activeImageUris = uris
            InspectionRepository.activeCommodityName = commodityName.trim()
            InspectionRepository.currentSelectedCategory = selectedCategory
            InspectionRepository.currentSelectedUnitBasis = selectedUnitBasis
            InspectionRepository.currentSelectedSchedule = selectedSchedule
            InspectionRepository.currentActiveChecklist = activeChecklist
            onImagesSelected(uris, commodityName.trim())
        }
    }

    val quickCommodities = listOf(
        "Tomato Ketchup", "Notebook", "Edible Oil", "Atta", "Biscuits", "Soap", "Detergent Powder", "Packaged Milk"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.getDualString("start_inspection", selectedLanguage)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = Localization.getDualString("product_input_title", selectedLanguage),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Compulsory Commodity Name Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (showError && commodityName.isBlank()) 
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    else 
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = if (showError && commodityName.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Localization.getDualString("commodity_compulsory", selectedLanguage),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (showError && commodityName.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = commodityName,
                        onValueChange = { 
                            commodityName = it
                            if (showError && it.isNotBlank()) showError = false
                            applyPresetForCommodity(it)
                        },
                        placeholder = { Text(Localization.getString("commodity_placeholder", selectedLanguage)) },
                        singleLine = true,
                        isError = showError && commodityName.isBlank(),
                        supportingText = {
                            if (showError && commodityName.isBlank()) {
                                Text(Localization.getString("commodity_required_error", selectedLanguage), color = MaterialTheme.colorScheme.error)
                            } else {
                                Text(Localization.getString("commodity_hint", selectedLanguage))
                            }
                        },
                        trailingIcon = {
                            if (commodityName.isNotEmpty()) {
                                IconButton(onClick = { commodityName = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear text")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = Localization.getDualString("quick_suggestions", selectedLanguage),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        quickCommodities.forEach { item ->
                            val isSelected = commodityName.equals(item, ignoreCase = true)
                            SuggestionChip(
                                onClick = { 
                                    commodityName = item
                                    showError = false
                                    applyPresetForCommodity(item)
                                },
                                label = { Text(item, fontSize = 12.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }

            // Statutory Inspection Parameters & Schedule Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Card Header with Icon & Authority Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Gavel,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = Localization.getDualString("statutory_config_title", selectedLanguage),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Establish legal baseline & audit parameters",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "LMO Authority",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // 1. Packaging Category
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Localization.getDualString("category_label", selectedLanguage),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                categoryDisplayItems.chunked(2).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rowItems.forEach { (catKey, catDisplay) ->
                                            val isSelected = selectedCategory == catKey
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(
                                                    width = if (isSelected) 1.5.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { onCategorySelected(catKey) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                        contentDescription = null,
                                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = catDisplay,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                        fontSize = 11.5.sp,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Measurement Unit Basis
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Scale,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Localization.getDualString("unit_basis_label", selectedLanguage),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                unitBasisDisplayItems.chunked(2).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rowItems.forEach { (unitKey, unitDisplay) ->
                                            val isSelected = selectedUnitBasis == unitKey
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(
                                                    width = if (isSelected) 1.5.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { selectedUnitBasis = unitKey }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                        contentDescription = null,
                                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = unitDisplay,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                        fontSize = 11.5.sp,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Statutory Enforcement Schedule (Inferred Automatically)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = Localization.getDualString("schedule_label", selectedLanguage),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Auto-Inferred",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedSchedule,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = getScheduleRationale(commodityName, selectedCategory, isInstitutionalBulk),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isInstitutionalBulk = !isInstitutionalBulk
                                        selectedSchedule = inferStatutorySchedule(commodityName, selectedCategory, isInstitutionalBulk)
                                        activeChecklist = computeDefaultChecklist(selectedCategory, selectedSchedule, commodityName)
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isInstitutionalBulk,
                                    onCheckedChange = { checked ->
                                        isInstitutionalBulk = checked
                                        selectedSchedule = inferStatutorySchedule(commodityName, selectedCategory, checked)
                                        activeChecklist = computeDefaultChecklist(selectedCategory, selectedSchedule, commodityName)
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Package is for Institutional / Industrial Bulk Consumer (Rule 3 Exemption)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // 4. Active Inspection Checklist (Audit Scope - Advanced Feature Dropdown)
                    val applicableOptions = getApplicableChecklistOptions(selectedCategory, selectedSchedule)
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Clickable Dropdown Header Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isChecklistExpanded = !isChecklistExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.FactCheck,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = Localization.getString("checklist_label", selectedLanguage).replace(" *", ""),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "Advanced",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${activeChecklist.size} / ${applicableOptions.size} Active Rules • Auto-Tailored",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (isChecklistExpanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (isChecklistExpanded) "Collapse" else "Dropdown",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isChecklistExpanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (isChecklistExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isChecklistExpanded) "Collapse" else "Dropdown",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Expandable Content: Reselect & Override Rules
                            if (isChecklistExpanded) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Toggle statutory rules to customize audit scope:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "Reset Defaults",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable {
                                                activeChecklist = computeDefaultChecklist(selectedCategory, selectedSchedule, commodityName)
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    applicableOptions.forEach { chk ->
                                        val isChecked = activeChecklist.contains(chk)
                                        val (badgeText, titleText) = parseRuleBadgeAndTitle(chk)
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(
                                                width = if (isChecked) 1.2.dp else 1.dp,
                                                color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    activeChecklist = if (isChecked) {
                                                        if (activeChecklist.size > 1) activeChecklist - chk else activeChecklist
                                                    } else {
                                                        activeChecklist + chk
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Surface(
                                                    color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = badgeText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isChecked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 10.5.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = titleText,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Establishment & Inspection Metadata Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = Localization.getDualString("establishment_details_title", selectedLanguage),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = establishmentName,
                        onValueChange = { establishmentName = it },
                        label = { Text(Localization.getDualString("establishment_label", selectedLanguage)) },
                        placeholder = { Text(Localization.getString("establishment_placeholder", selectedLanguage)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text(Localization.getString("location_label", selectedLanguage)) },
                            placeholder = { Text(Localization.getString("location_placeholder", selectedLanguage)) },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = numberOfPackages,
                            onValueChange = { numberOfPackages = it },
                            label = { Text(Localization.getString("packages_label", selectedLanguage)) },
                            placeholder = { Text("1") },
                            singleLine = true,
                            modifier = Modifier.weight(0.9f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    OutlinedTextField(
                        value = remarks,
                        onValueChange = { remarks = it },
                        label = { Text(Localization.getDualString("remarks_label", selectedLanguage)) },
                        placeholder = { Text(Localization.getString("remarks_placeholder", selectedLanguage)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = Localization.getDualString("select_method", selectedLanguage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (commodityName.trim().isBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "* Enter Commodity First",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            InspectionMethodCard(
                title = Localization.getDualString("scan_package", selectedLanguage),
                description = Localization.getString("scan_desc", selectedLanguage),
                icon = Icons.Default.CameraAlt,
                onClick = {
                    if (!validatePreScanConfiguration()) return@InspectionMethodCard
                    createAndSaveDraft()
                    InspectionRepository.clearCurrentInspection()
                    InspectionRepository.activeCommodityName = commodityName.trim()
                    InspectionRepository.currentSelectedCategory = selectedCategory
                    InspectionRepository.currentSelectedUnitBasis = selectedUnitBasis
                    InspectionRepository.currentSelectedSchedule = selectedSchedule
                    InspectionRepository.currentActiveChecklist = activeChecklist
                    onScanCamera(commodityName.trim())
                }
            )
            
            InspectionMethodCard(
                title = Localization.getDualString("upload_file", selectedLanguage),
                description = Localization.getString("upload_desc", selectedLanguage),
                icon = Icons.Default.CloudUpload,
                onClick = { 
                    if (!validatePreScanConfiguration()) return@InspectionMethodCard
                    createAndSaveDraft()
                    InspectionRepository.clearCurrentInspection()
                    InspectionRepository.activeCommodityName = commodityName.trim()
                    InspectionRepository.currentSelectedCategory = selectedCategory
                    InspectionRepository.currentSelectedUnitBasis = selectedUnitBasis
                    InspectionRepository.currentSelectedSchedule = selectedSchedule
                    InspectionRepository.currentActiveChecklist = activeChecklist
                    try {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } catch (e: Exception) {
                        try {
                            filePickerLauncher.launch("image/*")
                        } catch (e2: Exception) {
                            try {
                                filePickerLauncher.launch("*/*")
                            } catch (e3: Exception) {
                                Toast.makeText(context, "Picker not available", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
            
            InspectionMethodCard(
                title = Localization.getDualString("online_listing", selectedLanguage),
                description = Localization.getString("online_desc", selectedLanguage),
                icon = Icons.Default.Language,
                onClick = {
                    if (!validatePreScanConfiguration()) return@InspectionMethodCard
                    createAndSaveDraft()
                    InspectionRepository.activeCommodityName = commodityName.trim()
                    InspectionRepository.currentSelectedCategory = selectedCategory
                    InspectionRepository.currentSelectedUnitBasis = selectedUnitBasis
                    InspectionRepository.currentSelectedSchedule = selectedSchedule
                    InspectionRepository.currentActiveChecklist = activeChecklist
                    onOnlineListing()
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Legal Metrology Verification Gate",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "The specified commodity name directly configures the statutory rulebook (First & Second Schedules) and guides OCR detection to prevent erroneous mismatch findings.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InspectionMethodCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(48.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
