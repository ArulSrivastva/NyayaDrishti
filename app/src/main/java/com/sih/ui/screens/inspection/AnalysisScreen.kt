package com.sih.ui.screens.inspection

import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sih.repository.InspectionRepository
import kotlinx.coroutines.delay

@Composable
fun AnalysisScreen(
    selectedLanguage: String,
    imageUri: Uri? = null,
    imageUris: List<Uri> = if (imageUri != null) listOf(imageUri) else emptyList(),
    commodityName: String = "",
    onAnalysisComplete: () -> Unit
) {
    val context = LocalContext.current
    val stages = listOf(
        "Image quality check",
        "Commodity classification & rules",
        "OCR (Optical Character Recognition)",
        "Mandatory declaration extraction",
        "Multilingual statutory validation",
        "Schedule compliance check",
        "Evidence generation & audit trail"
    )
    
    var currentStageIndex by remember { mutableStateOf(0) }
    val urisToProcess = remember(imageUris, imageUri) {
        if (imageUris.isNotEmpty()) imageUris
        else if (imageUri != null) listOf(imageUri)
        else InspectionRepository.activeImageUris
    }
    val effectiveCommodity = if (commodityName.isNotBlank()) commodityName else (InspectionRepository.activeCommodityName ?: "")

    LaunchedEffect(urisToProcess, effectiveCommodity) {
        Log.d("AnalysisScreen", "Analysis starting with ${urisToProcess.size} URIs. Commodity: '$effectiveCommodity'")
        if (urisToProcess.isNotEmpty()) {
            val response = InspectionRepository.runInspection(context, urisToProcess, effectiveCommodity.ifBlank { null })
            if (response.isFailure) {
                val err = response.exceptionOrNull()?.message ?: "Inspection failed"
                Log.w("AnalysisScreen", "Inspect failed: $err, using fallback")
                InspectionRepository.createFallbackInspection(context, urisToProcess.first())
            }
        } else {
            Log.w("AnalysisScreen", "No URIs to process, creating fallback inspection")
            InspectionRepository.createFallbackInspection(context, Uri.EMPTY)
        }

        // System 2: Update InspectionDraft state
        val liveInsp = InspectionRepository.currentInspection
        if (liveInsp != null && liveInsp.inspectionId != null) {
            val gson = com.google.gson.Gson()
            val inspId = liveInsp.inspectionId.toString()
            val officerId = com.sih.network.ApiClient.getTokenManager()?.getUserId() ?: 1
            val draft = com.sih.model.InspectionDraft(
                inspectionId = inspId,
                officerId = officerId,
                establishmentName = liveInsp.establishmentName ?: InspectionRepository.currentEstablishmentName,
                inspectionType = liveInsp.inspectionType ?: InspectionRepository.currentInspectionType,
                location = liveInsp.location ?: InspectionRepository.currentLocation,
                state = com.sih.model.DraftState.RULE_EVALUATION,
                capturedEvidenceIds = liveInsp.evidenceRecords?.map { it.evidenceId } ?: emptyList(),
                qualityResultsJson = gson.toJson(liveInsp.imageQuality),
                ocrResultsJson = gson.toJson(liveInsp.declarations),
                extractedDeclarationsJson = gson.toJson(liveInsp.declarations),
                classificationJson = gson.toJson(liveInsp.classification),
                applicableRulesJson = gson.toJson(liveInsp.applicableRuleSet),
                complianceResultsJson = gson.toJson(liveInsp.compliance),
                reviewState = "PENDING_OFFICER_REVIEW",
                signOffJson = null,
                lastUpdated = java.time.LocalDateTime.now().toString()
            )
            InspectionRepository.saveDraft(context, draft)
        }

        for (i in stages.indices) {
            currentStageIndex = i
            delay(350) // Animated stage transition
        }
        delay(150)
        onAnalysisComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            strokeWidth = 6.dp
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Analyzing Package...",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "AI is verifying mandatory declarations",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            stages.forEachIndexed { index, stage ->
                AnalysisStageRow(
                    label = stage,
                    isCompleted = index < currentStageIndex,
                    isCurrent = index == currentStageIndex
                )
            }
        }
    }
}

@Composable
fun AnalysisStageRow(
    label: String,
    isCompleted: Boolean,
    isCurrent: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCompleted) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(20.dp)
            )
        } else if (isCurrent) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Spacer(modifier = Modifier.size(20.dp))
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun AnalysisScreenPreview() {
    com.sih.ui.theme.Sih_34Theme {
        AnalysisScreen(selectedLanguage = "English", onAnalysisComplete = {})
    }
}
