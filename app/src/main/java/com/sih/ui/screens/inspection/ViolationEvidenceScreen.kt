package com.sih.ui.screens.inspection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sih.ui.components.ConfidenceIndicator
import com.sih.util.Localization

import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.sih.network.ApiClient
import com.sih.repository.InspectionRepository
import kotlinx.coroutines.launch

import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViolationEvidenceScreen(
    selectedLanguage: String,
    onNavigateBack: () -> Unit,
    onConfirm: () -> Unit,
    onMarkCompliant: () -> Unit,
    onRescan: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val liveResult = InspectionRepository.currentInspection
    val liveViolations = liveResult?.let { InspectionRepository.mapToViolations(it) } ?: emptyList()
    var selectedViolationIndex by remember { mutableStateOf(0) }
    val currentViolation = liveViolations.getOrNull(selectedViolationIndex) ?: liveViolations.firstOrNull()

    val imageUrl = currentViolation?.imagePath?.ifBlank { null } ?: liveResult?.let {
        val ev = it.evidence?.firstOrNull() ?: it.evidencesList?.firstOrNull()
        ApiClient.getFullMediaUrl(ev?.imagePath ?: it.imagePath)
    }

    val handleConfirmDecision = {
        val id = InspectionRepository.currentInspectionId
        if (id != null) {
            scope.launch {
                val res = InspectionRepository.submitDecision(id, "REJECT", "Violation confirmed by inspector")
                res.onSuccess {
                    Toast.makeText(context, "Decision saved: REJECTED", Toast.LENGTH_SHORT).show()
                }
                onConfirm()
            }
        } else {
            onConfirm()
        }
    }

    val handleCompliantDecision = {
        val id = InspectionRepository.currentInspectionId
        if (id != null) {
            scope.launch {
                val res = InspectionRepository.submitDecision(id, "ACCEPT", "Marked compliant by inspector")
                res.onSuccess {
                    Toast.makeText(context, "Decision saved: COMPLIANT", Toast.LENGTH_SHORT).show()
                }
                onMarkCompliant()
            }
        } else {
            onMarkCompliant()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.getString("view_evidence", selectedLanguage)) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "AI Evidence Finding",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Multiple Violations Selector Chips
            if (liveViolations.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    liveViolations.forEachIndexed { idx, viol ->
                        FilterChip(
                            selected = selectedViolationIndex == idx,
                            onClick = { selectedViolationIndex = idx },
                            label = {
                                Text(
                                    text = "${viol.type.replace("_", " ").uppercase()} (#${idx + 1})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selectedViolationIndex == idx) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                    }
                }
            }

            // Evidence Image Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Evidence Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Original Package Image", color = Color.White)
                    }
                }

                if (currentViolation?.boundingBox == null) {
                    Surface(
                        color = Color(0xFFB71C1C).copy(alpha = 0.90f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "MANDATORY DECLARATION NOT DETECTED",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFFC62828).copy(alpha = 0.90f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "📍 OCR VIOLATION EVIDENCE SNIPPET",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentViolation?.type?.replace("_", " ")?.uppercase() ?: "Declaration Check",
                            fontWeight = FontWeight.Bold
                        )
                        ConfidenceIndicator(
                            confidence = currentViolation?.confidence ?: (liveResult?.confidence?.overall ?: 0.0f),
                            modifier = Modifier.width(100.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow("Statutory Finding", currentViolation?.detectedText ?: "No rule violations detected")
                    DetailRow("Status", if (currentViolation != null) "NON-COMPLIANT" else "COMPLIANT", if (currentViolation != null) MaterialTheme.colorScheme.error else Color(0xFF2E7D32))
                }
            }
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Legal Rule Reference: ${currentViolation?.ruleReference ?: "Rule 6"}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentViolation?.reason ?: "All mandatory package declarations comply with Legal Metrology (Packaged Commodities) Rules.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Inspector's Final Decision",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { handleConfirmDecision() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Violation")
                }
                
                OutlinedButton(
                    onClick = { handleCompliantDecision() },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mark as Compliant")
                }
                
                TextButton(
                    onClick = onRescan,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rescan for better evidence")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, color: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ViolationEvidenceScreenPreview() {
    com.sih.ui.theme.Sih_34Theme {
        ViolationEvidenceScreen("English", {}, {}, {}, {})
    }
}
