package com.sih.ui.screens.inspection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.sih.model.AcknowledgementStatus
import com.sih.model.FindingDecision
import com.sih.model.InspectionSignOff
import com.sih.network.ApiClient
import com.sih.repository.InspectionRepository
import com.sih.util.Localization
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun computeInspectionHash(json: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(json.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignOffScreen(
    selectedLanguage: String,
    onNavigateBack: () -> Unit,
    onFinalized: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val inspection = InspectionRepository.currentInspection
    
    val findingsList = remember {
        val list = mutableListOf<FindingItem>()
        inspection?.declarations?.forEach {
            list.add(FindingItem(id = "decl_${it.id ?: it.hashCode()}", type = it.type ?: "Declaration", value = it.value, confidence = it.confidence, rule = null, isViolation = false))
        }
        inspection?.violations?.forEach {
            list.add(FindingItem(id = "viol_${it.id ?: it.hashCode()}", type = it.type ?: "Violation", value = it.description, confidence = it.confidence, rule = it.ruleId, isViolation = true))
        }
        list
    }

    val decisions = remember { mutableStateMapOf<String, FindingDecisionState>() }
    
    var repName by remember { mutableStateOf("") }
    var ackStatus by remember { mutableStateOf(AcknowledgementStatus.ACKNOWLEDGED) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.getDualString("signoff_title", selectedLanguage)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = Localization.getDualString("section_findings", selectedLanguage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(findingsList) { finding ->
                FindingReviewCard(
                    finding = finding,
                    decisionState = decisions[finding.id] ?: FindingDecisionState(),
                    onDecisionChange = { decisions[finding.id] = it }
                )
            }

            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = Localization.getDualString("section_ack", selectedLanguage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = repName,
                    onValueChange = { repName = it },
                    label = { Text(Localization.getDualString("rep_name_label", selectedLanguage)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(Localization.getString("ack_disclaimer", selectedLanguage), style = MaterialTheme.typography.bodySmall)
                
                Column {
                    AcknowledgementStatus.values().forEach { status ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = ackStatus == status,
                                onClick = { ackStatus = status }
                            )
                            Text(
                                text = when (status) {
                                    AcknowledgementStatus.ACKNOWLEDGED -> Localization.getString("ack_receipt", selectedLanguage)
                                    else -> status.name.replace("_", " ")
                                }
                            )
                        }
                    }
                }
            }

            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = Localization.getDualString("section_finalization", selectedLanguage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                val totalConfirmed = decisions.values.count { it.decision == "CONFIRMED" }
                val totalRejected = decisions.values.count { it.decision == "REJECTED" }
                val totalUnable = decisions.values.count { it.decision == "UNABLE_TO_VERIFY" }
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Summary:", fontWeight = FontWeight.Bold)
                        Text("Confirmed: $totalConfirmed", color = Color(0xFF4CAF50))
                        Text("Rejected: $totalRejected", color = Color(0xFFF44336))
                        Text("Unable to Verify: $totalUnable", color = Color(0xFFFF9800))
                    }
                }
                
                val allDecided = findingsList.all { decisions.containsKey(it.id) }

                Button(
                    onClick = { showConfirmDialog = true },
                    enabled = allDecided,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(Localization.getDualString("sign_finalize_button", selectedLanguage))
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(Localization.getDualString("finalize_confirm_title", selectedLanguage)) },
            text = { Text(Localization.getString("finalize_confirm_text", selectedLanguage)) },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    finalizeInspection(
                        context = context,
                        findingsList = findingsList,
                        decisions = decisions,
                        ackStatus = ackStatus,
                        repName = repName,
                        inspection = inspection,
                        onFinalized = onFinalized
                    )
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun finalizeInspection(
    context: android.content.Context,
    findingsList: List<FindingItem>,
    decisions: Map<String, FindingDecisionState>,
    ackStatus: AcknowledgementStatus,
    repName: String,
    inspection: com.sih.network.dto.FullInspectionResponse?,
    onFinalized: () -> Unit
) {
    val gson = Gson()
    val inspectionJson = gson.toJson(inspection)
    val hash = computeInspectionHash(inspectionJson)
    
    val mappedDecisions = findingsList.map { finding ->
        val decision = decisions[finding.id]
        FindingDecision(
            declarationType = finding.type,
            ruleId = finding.rule,
            decision = decision?.decision ?: "UNABLE_TO_VERIFY",
            reason = decision?.reason
        )
    }

    val officerId = ApiClient.getTokenManager()?.getUserId()?.toString()?.toIntOrNull() ?: 0
    val officerName = ApiClient.getTokenManager()?.getUserName() ?: "Unknown Officer"
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
    
    val signOff = InspectionSignOff(
        officerId = officerId,
        officerName = officerName,
        officerTimestamp = timestamp,
        officerDeviceId = android.os.Build.MODEL,
        inspectionJsonHash = hash,
        findingDecisions = mappedDecisions,
        representativeAcknowledgement = ackStatus,
        representativeName = repName.ifBlank { null },
        representativeTimestamp = if (ackStatus == AcknowledgementStatus.ACKNOWLEDGED) timestamp else null,
        signatureImagePath = null,
        signatureHash = null
    )
    
    // Update inspection state to FINALIZED and persist to database
    if (inspection != null) {
        val updated = inspection.copy(
            signOff = signOff,
            inspectionState = "FINALIZED"
        )
        InspectionRepository.currentInspection = updated
        val inspId = updated.inspectionId ?: 1
        try {
            com.sih.data.local.LocalDatabase.getInstance(context).saveSignOff(inspId.toString(), signOff)
            com.sih.data.local.LocalDatabase.getInstance(context).insertOrUpdateInspection(updated)
            com.sih.data.local.LocalDatabase.getInstance(context).updateDraftState(inspId.toString(), com.sih.model.DraftState.COMPLETED, gson.toJson(updated))
        } catch (e: Exception) {
            android.util.Log.e("SignOffScreen", "Error saving signoff: ${e.message}", e)
        }
    }
    
    onFinalized()
}

data class FindingItem(
    val id: String,
    val type: String,
    val value: String?,
    val confidence: Float?,
    val rule: String?,
    val isViolation: Boolean
)

data class FindingDecisionState(
    val decision: String = "",
    val reason: String = ""
)

@Composable
fun FindingReviewCard(
    finding: FindingItem,
    decisionState: FindingDecisionState,
    onDecisionChange: (FindingDecisionState) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${if (finding.isViolation) "Violation" else "Declaration"}: ${finding.type}",
                fontWeight = FontWeight.Bold
            )
            finding.value?.let { Text("Detected: $it") }
            finding.confidence?.let { Text("AI Confidence: ${(it * 100).toInt()}%") }
            finding.rule?.let { Text("Rule Ref: $it") }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DecisionButton("CONFIRMED", Color(0xFF4CAF50), decisionState.decision, Modifier.weight(1f)) {
                        onDecisionChange(decisionState.copy(decision = "CONFIRMED"))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    DecisionButton("REJECTED", Color(0xFFF44336), decisionState.decision, Modifier.weight(1f)) {
                        onDecisionChange(decisionState.copy(decision = "REJECTED"))
                    }
                }
                DecisionButton("UNABLE_TO_VERIFY", Color(0xFFFF9800), decisionState.decision, Modifier.fillMaxWidth()) {
                    onDecisionChange(decisionState.copy(decision = "UNABLE_TO_VERIFY"))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = decisionState.reason,
                onValueChange = { onDecisionChange(decisionState.copy(reason = it)) },
                label = { Text("Optional Reason") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun DecisionButton(text: String, color: Color, selectedDecision: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val isSelected = selectedDecision == text
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) color else Color.LightGray
        )
    ) {
        Text(text, color = if (isSelected) Color.White else Color.Black)
    }
}
