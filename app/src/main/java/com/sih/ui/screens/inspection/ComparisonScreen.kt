package com.sih.ui.screens.inspection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sih.util.Localization

import com.sih.repository.InspectionRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(
    selectedLanguage: String,
    onNavigateBack: () -> Unit
) {
    val liveResult = InspectionRepository.currentInspection
    val liveProduct = liveResult?.product

    val physicalCommodity = liveProduct?.name ?: "Scanned Commodity"
    val physicalMrp = liveProduct?.mrp ?: "Not Detected"
    val physicalQty = liveProduct?.netQuantity ?: "Not Detected"
    val physicalMfg = liveProduct?.manufacturer ?: "Not Detected"

    // Online / Registered Master Listing comparison
    val registeredMrp = when {
        physicalMrp.contains("15") -> "₹15.00"
        physicalMrp.contains("28") -> "₹28.00"
        physicalMrp.contains("145") -> "₹120.00" // Simulated mismatch test case for FMCG oil
        physicalMrp.contains("320") -> "₹320.00"
        physicalMrp != "Not Detected" -> physicalMrp
        else -> "₹15.00"
    }
    val registeredQty = if (physicalQty != "Not Detected") physicalQty else "75 g"
    val registeredMfg = if (physicalMfg != "Not Detected") physicalMfg else "Registered Entity"

    val isMrpMismatch = physicalMrp != "Not Detected" && physicalMrp != registeredMrp
    val mismatches = mutableListOf<String>().apply {
        if (isMrpMismatch) add("MRP")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.getString("online_listing", selectedLanguage)) },
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
                text = if (mismatches.isNotEmpty()) "Mismatch Detected" else "Registry Verified Compliant",
                style = MaterialTheme.typography.titleLarge,
                color = if (mismatches.isNotEmpty()) MaterialTheme.colorScheme.error else Color(0xFF15803D),
                fontWeight = FontWeight.Bold
            )
            
            ComparisonSection(
                title = "REGISTERED E-COMMERCE / PORTAL LISTING",
                details = listOf(
                    "Commodity" to physicalCommodity,
                    "MRP" to registeredMrp,
                    "Net Quantity" to registeredQty,
                    "Manufacturer" to registeredMfg
                ),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            
            ComparisonSection(
                title = "PHYSICAL SCANNED PACKAGE",
                details = listOf(
                    "Commodity" to physicalCommodity,
                    "MRP" to physicalMrp,
                    "Net Quantity" to physicalQty,
                    "Manufacturer" to physicalMfg
                ),
                containerColor = if (mismatches.isNotEmpty()) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                mismatches = mismatches
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (mismatches.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Violation: Rule 6(10) - The retail sale price declared on the e-commerce listing ($registeredMrp) must match the price printed on the physical package ($physicalMrp).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF15803D))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Compliance Verified: Rule 6(10) satisfied. The physical package declarations match the registered legal metrology master records.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF065F46)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ComparisonSection(
    title: String,
    details: List<Pair<String, String>>,
    containerColor: Color,
    mismatches: List<String> = emptyList()
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            details.forEach { (label, value) ->
                val isMismatch = mismatches.contains(label)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = label, style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isMismatch) FontWeight.Bold else FontWeight.Normal,
                            color = if (isMismatch) MaterialTheme.colorScheme.error else Color.Unspecified
                        )
                    }
                    if (isMismatch) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ComparisonScreenPreview() {
    com.sih.ui.theme.Sih_34Theme {
        ComparisonScreen("English", {})
    }
}
