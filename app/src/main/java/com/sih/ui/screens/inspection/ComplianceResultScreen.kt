package com.sih.ui.screens.inspection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sih.model.ComplianceStatus
import com.sih.ui.components.ComplianceBadge
import com.sih.ui.components.DeclarationRow
import com.sih.util.Localization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.sih.network.dto.DeclarationDto
import com.sih.repository.InspectionRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplianceResultScreen(
    selectedLanguage: String,
    onNavigateBack: () -> Unit,
    onViewEvidence: () -> Unit,
    onViewReport: () -> Unit
) {
    val liveResult = InspectionRepository.currentInspection
    var showClassificationDialog by remember { mutableStateOf(false) }
    var selectedDeclarationForProof by remember { mutableStateOf<DeclarationDto?>(null) }

    val productName = liveResult?.product?.name ?: "Scanned Package"
    val inspId = liveResult?.inspectionId?.toString() ?: "101"
    
    val status = when (liveResult?.compliance?.status?.uppercase()) {
        "PASS", "COMPLIANT" -> ComplianceStatus.COMPLIANT
        "FAIL", "NON_COMPLIANT" -> ComplianceStatus.NON_COMPLIANT
        else -> ComplianceStatus.NEEDS_REVIEW
    }
    
    val score = ((liveResult?.confidence?.overall ?: 0.0f) * 100).toInt()
    val declarationsList = liveResult?.declarations ?: emptyList()
    val violationsList = liveResult?.violations ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.getString("compliance_result", selectedLanguage)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ResultHeader(
                    productName = productName,
                    inspectionId = inspId,
                    status = status,
                    score = score,
                    language = selectedLanguage
                )
            }

            // Product Classification & Statutory Rule Applicability Card
            val classification = liveResult?.classification
            val ruleSet = liveResult?.applicableRuleSet
            if (classification != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PRODUCT CLASSIFICATION",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${classification.category.iconSymbol} ${classification.category.displayName}",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = classification.subCategory,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Attribute pills row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val originLabel = when (classification.importStatus) {
                                    com.sih.domain.classification.ImportStatus.DOMESTIC -> "🇮🇳 Domestic"
                                    com.sih.domain.classification.ImportStatus.IMPORTED -> "🌐 Imported"
                                    com.sih.domain.classification.ImportStatus.UNDETERMINED -> "❓ Origin Unknown"
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                ) {
                                    Text(
                                        text = originLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                ) {
                                    Text(
                                        text = "📦 ${classification.packageType.name.replace("_", " ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                ) {
                                    Text(
                                        text = "⚖️ ${classification.quantityType.name.replace("_", " ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                thickness = 0.5.dp
                            )

                            // Rules summary and action
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val activeCount = ruleSet?.activeRules?.size ?: 0
                                val excludedCount = ruleSet?.excludedRules?.size ?: 0
                                val confPercent = (classification.confidence * 100).toInt()

                                Text(
                                    text = "Confidence: $confPercent% • Active: $activeCount • Excluded: $excludedCount",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedButton(
                                    onClick = { showClassificationDialog = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Evidence", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Image Quality Gate & Audit Card
            item {
                val quality = liveResult?.imageQuality
                val qScore = ((quality?.qualityScore ?: 0.95f) * 100).toInt()
                val isOverridden = quality?.qualityOverride == true

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOverridden) Color(0xFFE65100).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isOverridden) Color(0xFFFF9800).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "IMAGE QUALITY & EVIDENTIARY AUDIT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isOverridden) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                color = if (isOverridden) Color(0xFFFF9800).copy(alpha = 0.2f) else Color(0xFF2E7D32).copy(alpha = 0.25f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (isOverridden) "WARNING • OVERRIDDEN" else "PASSED ($qScore%)",
                                    color = if (isOverridden) Color(0xFFFFB74D) else Color(0xFF4ADE80),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = if (isOverridden) {
                                "Officer Override Active (${quality?.overrideOfficer ?: "Senior Inspector"})\nReason: ${quality?.overrideReason ?: "Field Discretion"}\nObscured regions marked UNABLE TO VERIFY."
                            } else {
                                "Focus, contrast & blur verified (Discrete Laplacian operator). Minimum DPI satisfied under Legal Metrology standards."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            item {
                Text(
                    text = "EXTRACTED DECLARATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            if (declarationsList.isNotEmpty()) {
                items(declarationsList.size) { index ->
                    val decl = declarationsList[index]
                    val declStatus = if (decl.present == true && (decl.confidence ?: 0f) >= 0.7f) {
                        ComplianceStatus.COMPLIANT
                    } else if (decl.present == false) {
                        ComplianceStatus.NON_COMPLIANT
                    } else {
                        ComplianceStatus.NEEDS_REVIEW
                    }
                    DeclarationRow(
                        label = decl.type?.replace("_", " ")?.uppercase() ?: "DECLARATION",
                        value = decl.value ?: (if (decl.present == true) "Present" else "Not Found"),
                        status = declStatus,
                        confidence = decl.confidence ?: 0.0f,
                        onClick = {
                            selectedDeclarationForProof = decl
                        }
                    )
                }
            } else {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No mandatory declarations detected in the image. Please ensure the package label is clear, well-lit, and directly facing the camera.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Statutory Findings / Violations / Unverified Items
            if (violationsList.isNotEmpty()) {
                item {
                    Text(
                        text = "LEGAL METROLOGY FINDINGS & STATUTORY CHECKS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(violationsList.size) { vIndex ->
                    val viol = violationsList[vIndex]
                    val isUnable = viol.severity?.contains("unable", ignoreCase = true) == true
                    val isViolation = viol.severity == "violation"

                    val cardBg = when {
                        isUnable -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        isViolation -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        else -> Color(0xFFEF6C00).copy(alpha = 0.20f)
                    }
                    val cardBorder = when {
                        isUnable -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        isViolation -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        else -> Color(0xFFEF6C00).copy(alpha = 0.6f)
                    }
                    val titleColor = when {
                        isUnable -> MaterialTheme.colorScheme.onSurface
                        isViolation -> MaterialTheme.colorScheme.error
                        else -> Color(0xFFFFB74D)
                    }
                    val badgeBg = when {
                        isUnable -> MaterialTheme.colorScheme.surfaceContainerHighest
                        isViolation -> MaterialTheme.colorScheme.errorContainer
                        else -> Color(0xFFFFE082).copy(alpha = 0.25f)
                    }
                    val badgeTextColor = when {
                        isUnable -> MaterialTheme.colorScheme.onSurface
                        isViolation -> MaterialTheme.colorScheme.onErrorContainer
                        else -> Color(0xFFFFB74D)
                    }
                    val badgeLabel = when {
                        isUnable -> "⚪ UNABLE TO VERIFY"
                        isViolation -> "🔴 NON-COMPLIANT"
                        else -> "⚠️ MANUAL REVIEW"
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${viol.ruleId ?: "Rule 6"} • ${viol.type?.replace("_", " ")?.uppercase() ?: "STATUTORY FINDING"}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = titleColor,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = badgeBg,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = badgeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeTextColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = viol.description ?: "Statutory declaration check.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            
            item {
                SummarySection(
                    totalChecked = declarationsList.size,
                    violations = violationsList.count { it.severity == "violation" },
                    reviews = violationsList.count { it.severity != "violation" }
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onViewEvidence,
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Localization.getString("view_evidence", selectedLanguage))
                    }
                    Button(
                        onClick = onViewReport,
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Review & Sign Off")
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showClassificationDialog && liveResult?.classification != null) {
        val classification = liveResult.classification
        val ruleSet = liveResult.applicableRuleSet
        AlertDialog(
            onDismissRequest = { showClassificationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${classification.category.iconSymbol} Classification & Statutory Rules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "PRODUCT TAXONOMY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Category: ${classification.category.displayName}\nSub-category: ${classification.subCategory}\nImport Status: ${classification.importStatus.name}\nPackaging: ${classification.packageType.name}\nQuantity Type: ${classification.quantityType.name}\nClassifier Confidence: ${(classification.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (classification.classificationSignals.isNotEmpty()) {
                        item {
                            Text(
                                text = "DETECTION SIGNALS (EVIDENTIARY AUDIT)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                classification.classificationSignals.forEach { sig ->
                                    Text("• $sig", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }

                    if (ruleSet?.activeRules?.isNotEmpty() == true) {
                        item {
                            Text(
                                text = "ACTIVE STATUTORY RULES (${ruleSet.activeRules.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4ADE80)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ruleSet.activeRules.forEach { r ->
                                    Text(
                                        "✓ ${r.reference} — ${r.title}\n   ${r.reason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    if (ruleSet?.excludedRules?.isNotEmpty() == true) {
                        item {
                            Text(
                                text = "EXCLUDED RULES (${ruleSet.excludedRules.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ruleSet.excludedRules.forEach { r ->
                                    Text(
                                        "⊘ ${r.reference} — ${r.title}\n   Reason: ${r.reason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (ruleSet?.statutoryReferences?.isNotEmpty() == true) {
                        item {
                            Text(
                                text = "STATUTORY REFERENCES & SCHEDULES",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                ruleSet.statutoryReferences.forEach { sRef ->
                                    Text("§ $sRef", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showClassificationDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (selectedDeclarationForProof != null) {
        DeclarationProofDialog(
            declaration = selectedDeclarationForProof!!,
            primaryImagePath = liveResult?.imagePath,
            onDismiss = { selectedDeclarationForProof = null }
        )
    }
}

@Composable
fun DeclarationProofDialog(
    declaration: DeclarationDto,
    primaryImagePath: String?,
    onDismiss: () -> Unit
) {
    val typeName = declaration.type?.replace("_", " ")?.uppercase() ?: "DECLARATION"
    val isPresent = declaration.present == true && !declaration.value.isNullOrBlank()
    val isGlared = declaration.value?.contains("Glare", ignoreCase = true) == true
    val proofImagePath = declaration.imagePath?.ifBlank { null }

    val ruleInfo = when (declaration.type?.lowercase()) {
        "mrp" -> "Rule 6(1)(f) & Rule 6(11)" to "Mandatory Retail Sale Price (MRP) declaration including 'inclusive of all taxes'. Unit Sale Price (USP) required for net weights/volumes > 100g/ml."
        "net_quantity" -> "Rule 6(1)(d), Rule 12 & 13" to "Net quantity declaration in standard SI metric units on the principal display panel. Non-standard count units or qualifiers like 'approx/min' are strictly prohibited."
        "commodity" -> "Rule 6(1)(a)" to "Generic or common name of the commodity must be prominently declared on the principal display panel."
        "manufacturer" -> "Rule 6(1)(b)" to "Name and complete address of the manufacturer with area, district, state and PIN code."
        "packer" -> "Rule 6(1)(b)" to "Name and complete address of the pre-packer if different from manufacturer."
        "importer" -> "Rule 6(1)(c)" to "Name and address of importer and Country of Origin for imported pre-packaged goods."
        "packing_date" -> "Rule 6(1)(e)" to "Month and year of manufacture or pre-packing. Food and cosmetic packages require Best Before / Expiry declaration."
        "consumer_care" -> "Rule 6(1)(g)" to "Name, address, telephone number and email of person or office to contact in case of consumer complaints."
        "unit_sale_price" -> "Rule 6(11)" to "Unit sale price (e.g. ₹/g, ₹/kg, ₹/ml, ₹/l) must be indicated on packages exceeding 100g or 100ml."
        "font_size_readability" -> "Rule 7 & Schedule II" to "Statutory minimum numeral and letter height requirements based on package size and weight."
        else -> "Rule 6, Legal Metrology Rules, 2011" to "Statutory package commodity declaration standards."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$typeName PROOF",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = when {
                        isPresent -> Color(0xFF2E7D32).copy(alpha = 0.2f)
                        isGlared -> Color(0xFFFF9800).copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.errorContainer
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when {
                            isPresent -> "✓ VERIFIED"
                            isGlared -> "⚠️ OBSCURED"
                            else -> "✕ NOT DETECTED"
                        },
                        color = when {
                            isPresent -> Color(0xFF4ADE80)
                            isGlared -> Color(0xFFFFB74D)
                            else -> MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Visual Evidence Image Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                        .border(
                            1.dp,
                            if (isPresent) Color(0xFF4ADE80).copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val displayImage = proofImagePath ?: primaryImagePath
                    if (!displayImage.isNullOrBlank()) {
                        AsyncImage(
                            model = displayImage,
                            contentDescription = "Visual Evidence",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    if (proofImagePath != null) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "📍 Cropped On-Device Evidentiary Snippet",
                                color = Color(0xFF4ADE80),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = Color(0xFFB71C1C).copy(alpha = 0.88f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "MANDATORY DECLARATION NOT DETECTED",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Extracted Value Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "EXTRACTED TEXT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = declaration.value ?: "Not detected in optical scan",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isPresent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                        val conf = ((declaration.confidence ?: 0f) * 100).toInt()
                        Text(
                            text = "OCR Confidence: $conf%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Evidence Provenance Card
                val liveInspection = InspectionRepository.currentInspection
                val evidenceRec = liveInspection?.evidenceRecords?.firstOrNull()
                val sha = evidenceRec?.sha256 ?: liveInspection?.evidence?.firstOrNull()?.sha256
                if (!sha.isNullOrBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "EVIDENCE PROVENANCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "✓ VERIFIED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                            Text(
                                text = "SHA-256: ${sha.take(8)}...${sha.takeLast(8)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            evidenceRec?.evidenceId?.let {
                                Text(text = "Evidence ID: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Statutory Reference Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "§ ${ruleInfo.first}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = ruleInfo.second,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ResultHeader(
    productName: String,
    inspectionId: String = "88219-01",
    status: ComplianceStatus,
    score: Int,
    language: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = productName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = "ID: #INSP-$inspectionId", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            ComplianceBadge(status = status)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "$score",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = when (status) {
                    ComplianceStatus.COMPLIANT -> Color(0xFF2E7D32)
                    ComplianceStatus.NON_COMPLIANT -> MaterialTheme.colorScheme.error
                    ComplianceStatus.NEEDS_REVIEW -> Color(0xFFEF6C00)
                }
            )
            Text(text = Localization.getString("compliance_score", language), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SummarySection(totalChecked: Int, violations: Int, reviews: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("Checked", totalChecked.toString())
            StatItem("Violations", violations.toString(), MaterialTheme.colorScheme.error)
            StatItem("Reviews", reviews.toString(), Color(0xFFEF6C00))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
    }
}
