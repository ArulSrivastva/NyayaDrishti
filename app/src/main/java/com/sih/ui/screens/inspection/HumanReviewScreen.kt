package com.sih.ui.screens.inspection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sih.util.Localization

import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.sih.network.ApiClient
import com.sih.repository.InspectionRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HumanReviewScreen(
    selectedLanguage: String,
    onNavigateBack: () -> Unit,
    onPresent: () -> Unit,
    onMissing: () -> Unit,
    onRescan: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val liveResult = InspectionRepository.currentInspection
    val imageUrl = liveResult?.let {
        val ev = it.evidence?.firstOrNull() ?: it.evidencesList?.firstOrNull()
        ApiClient.getFullMediaUrl(ev?.imagePath ?: it.imagePath)
    }

    val declarations = liveResult?.declarations ?: emptyList()
    // Identify all items that require human review (missing or confidence < 0.7)
    val reviewItems = remember(declarations) {
        val pending = declarations.filter { it.present == false || (it.confidence ?: 1.0f) < 0.70f }
        if (pending.isEmpty() && declarations.isNotEmpty()) {
            listOfNotNull(declarations.minByOrNull { it.confidence ?: 1.0f })
        } else {
            pending
        }
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    val reviewTarget = reviewItems.getOrNull(currentIndex) ?: declarations.firstOrNull()

    val fieldDisplayName = when (reviewTarget?.type) {
        "net_quantity" -> "Net Quantity"
        "mrp" -> "Maximum Retail Price (MRP)"
        "date" -> "Month & Year of Manufacture / Packing"
        "manufacturer" -> "Manufacturer Name & Complete Address"
        "packer" -> "Packer Name & Address"
        "importer" -> "Importer Details"
        "consumer_care" -> "Consumer Care Helpline / Email"
        "commodity" -> "Generic Name of Commodity"
        else -> "Mandatory Declaration"
    }

    val ruleRef = when (reviewTarget?.type) {
        "net_quantity" -> "Rule 6(1)(d)"
        "date" -> "Rule 6(1)(e)"
        "mrp" -> "Rule 6(1)(f)"
        "manufacturer" -> "Rule 6(1)(b)"
        "consumer_care" -> "Rule 6(1)(g)"
        "packer" -> "Rule 10(1)"
        "importer" -> "Rule 6(1)(c)"
        "commodity" -> "Rule 6(1)(a)"
        else -> "Rule 6"
    }

    val detectedValue = reviewTarget?.value
    val explanationText = if (!detectedValue.isNullOrBlank()) {
        "AI flagged low confidence reading for '$detectedValue'. Please visually confirm if this satisfies $ruleRef."
    } else {
        "The automated OCR engine could not reliably detect '$fieldDisplayName' on the package. Please check if it is physically visible."
    }

    val confidencePct = ((reviewTarget?.confidence ?: (liveResult?.confidence?.overall ?: 0.45f)) * 100).toInt()

    val handlePresent = {
        val id = InspectionRepository.currentInspectionId
        if (id != null) {
            scope.launch {
                InspectionRepository.submitDecision(id, "ACCEPT", "Inspector verified $fieldDisplayName present")
            }
        }
        Toast.makeText(context, "Verified: $fieldDisplayName Present", Toast.LENGTH_SHORT).show()
        if (currentIndex < reviewItems.size - 1) {
            currentIndex++
        } else {
            onPresent()
        }
    }

    val handleMissing = {
        val id = InspectionRepository.currentInspectionId
        if (id != null) {
            scope.launch {
                InspectionRepository.submitDecision(id, "REJECT", "Inspector confirmed $fieldDisplayName missing")
            }
        }
        Toast.makeText(context, "Confirmed: $fieldDisplayName Missing", Toast.LENGTH_SHORT).show()
        if (currentIndex < reviewItems.size - 1) {
            currentIndex++
        } else {
            onMissing()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Human Review") },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Is '$fieldDisplayName' present?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = "AI Confidence: $confidencePct% • $ruleRef",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                    Text("Package Viewfinder Crop", color = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = explanationText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { handlePresent() },
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("It is Present")
                }
                
                Button(
                    onClick = { handleMissing() },
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("It is Missing")
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            TextButton(
                onClick = onRescan,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rescan to improve quality")
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun HumanReviewScreenPreview() {
    com.sih.ui.theme.Sih_34Theme {
        HumanReviewScreen("English", {}, {}, {}, {})
    }
}
