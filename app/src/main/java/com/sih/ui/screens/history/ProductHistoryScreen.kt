package com.sih.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sih.ui.components.PrimaryButton
import com.sih.util.Localization

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sih.network.dto.ProductHistoryResponse
import com.sih.repository.InspectionRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductHistoryScreen(
    selectedLanguage: String,
    onNavigateBack: () -> Unit,
    onStartInspection: () -> Unit
) {
    var productHistoryData by remember { mutableStateOf<ProductHistoryResponse?>(null) }

    LaunchedEffect(Unit) {
        val currentProdId = InspectionRepository.currentInspection?.product?.id ?: 1
        val history = InspectionRepository.getProductHistory(currentProdId)
        if (history != null) {
            productHistoryData = history
        }
    }

    val prodName = productHistoryData?.productName ?: "Fortune Soyabean Oil 1L"
    val totalInsp = productHistoryData?.totalInspections?.toString() ?: "14"
    val totalViol = productHistoryData?.totalViolations?.toString() ?: "03"
    val riskLvl = productHistoryData?.riskLevel ?: "MEDIUM"
    val riskReasonText = productHistoryData?.riskReason ?: "Repeated font size violations detected in the last 3 months."

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product History") },
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
            ProductOverviewCard(
                productName = prodName,
                totalInspections = totalInsp,
                totalViolations = totalViol,
                riskLevel = riskLvl
            )
            
            RiskTrendCard()
            
            RepeatedNonComplianceAlert(reason = riskReasonText)
            
            PrimaryButton(
                text = Localization.getString("start_inspection", selectedLanguage),
                onClick = onStartInspection
            )
            
            Text(
                text = "Previous Inspections",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            val historyList = productHistoryData?.history ?: emptyList()
            if (historyList.isNotEmpty()) {
                historyList.forEach { h ->
                    val isFail = h.compliance?.status?.uppercase() == "FAIL"
                    TimelineItem(
                        date = h.createdAt?.take(10) ?: "Today",
                        status = if (isFail) "Violation" else "Compliant",
                        color = if (isFail) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                    )
                }
            } else {
                repeat(3) { index ->
                    TimelineItem(
                        date = if (index == 0) "Today" else "15 Aug 2026",
                        status = if (index == 1) "Violation" else "Compliant",
                        color = if (index == 1) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                    )
                }
            }
        }
    }
}

@Composable
fun ProductOverviewCard(
    productName: String = "Fortune Soyabean Oil 1L",
    totalInspections: String = "14",
    totalViolations: String = "03",
    riskLevel: String = "MEDIUM"
) {
    val riskColor = when (riskLevel.uppercase()) {
        "HIGH" -> MaterialTheme.colorScheme.error
        "MEDIUM" -> Color(0xFFEF6C00)
        else -> Color(0xFF2E7D32)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(productName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Adani Wilmar Ltd.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatColumn("Inspections", totalInspections)
                StatColumn("Violations", totalViolations)
                StatColumn("Risk", riskLevel, riskColor)
            }
        }
    }
}

@Composable
fun StatColumn(label: String, value: String, color: Color = Color.Unspecified) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun RiskTrendCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Risk Level Trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(100.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("Chart Placeholder", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun RepeatedNonComplianceAlert(reason: String = "Repeated font size violations detected in the last 3 months.") {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun TimelineItem(date: String, status: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(100.dp)) {
            Text(text = date, style = MaterialTheme.typography.bodySmall)
        }
        Box(modifier = Modifier.size(12.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = color)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ProductHistoryScreenPreview() {
    com.sih.ui.theme.Sih_34Theme {
        ProductHistoryScreen("English", {}, {})
    }
}
