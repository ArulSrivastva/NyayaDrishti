package com.sih.ui.screens.inspection

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.sih.repository.InspectionRepository
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
    var commodityName by remember { mutableStateOf("") }
    var establishmentName by remember { mutableStateOf(activeDraft?.establishmentName ?: InspectionRepository.currentEstablishmentName ?: "") }
    var location by remember { mutableStateOf(activeDraft?.location ?: InspectionRepository.currentLocation ?: "") }
    var inspectionType by remember { mutableStateOf(activeDraft?.inspectionType ?: InspectionRepository.currentInspectionType ?: "Routine Inspection") }
    var numberOfPackages by remember { mutableStateOf(InspectionRepository.currentNumberOfPackages ?: "1") }
    var remarks by remember { mutableStateOf(InspectionRepository.currentRemarks ?: "") }
    var showError by remember { mutableStateOf(false) }

    fun validateCommodity(): Boolean {
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
        return true
    }

    fun createAndSaveDraft(): String {
        val db = com.sih.data.local.LocalDatabase.getInstance(context)
        val inspId = InspectionRepository.currentDraft?.inspectionId ?: db.getNextInspectionId().toString()
        val officerId = com.sih.network.ApiClient.getTokenManager()?.getUserId() ?: 1
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
            lastUpdated = java.time.LocalDateTime.now().toString()
        )
        InspectionRepository.currentInspectionId = inspId.toIntOrNull()
        InspectionRepository.currentEstablishmentName = establishmentName.ifBlank { "Retail Store" }
        InspectionRepository.currentInspectionType = inspectionType
        InspectionRepository.currentLocation = location.ifBlank { "Field Inspection Zone" }
        InspectionRepository.currentNumberOfPackages = numberOfPackages
        InspectionRepository.currentRemarks = remarks
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                            label = { Text(Localization.getDualString("location_label", selectedLanguage)) },
                            placeholder = { Text(Localization.getString("location_placeholder", selectedLanguage)) },
                            singleLine = true,
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = numberOfPackages,
                            onValueChange = { numberOfPackages = it },
                            label = { Text(Localization.getDualString("packages_label", selectedLanguage)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
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
                    fontWeight = FontWeight.Bold
                )
                if (commodityName.trim().isBlank()) {
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
                    if (!validateCommodity()) return@InspectionMethodCard
                    createAndSaveDraft()
                    InspectionRepository.clearCurrentInspection()
                    InspectionRepository.activeCommodityName = commodityName.trim()
                    onScanCamera(commodityName.trim())
                }
            )
            
            InspectionMethodCard(
                title = Localization.getDualString("upload_file", selectedLanguage),
                description = Localization.getString("upload_desc", selectedLanguage),
                icon = Icons.Default.CloudUpload,
                onClick = { 
                    if (!validateCommodity()) return@InspectionMethodCard
                    InspectionRepository.clearCurrentInspection()
                    InspectionRepository.activeCommodityName = commodityName.trim()
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
                    if (!validateCommodity()) return@InspectionMethodCard
                    createAndSaveDraft()
                    InspectionRepository.activeCommodityName = commodityName.trim()
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
            
            Column {
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
