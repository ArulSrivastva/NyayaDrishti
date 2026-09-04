package com.sih.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.sih.util.ReportGenerator

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.sih.util.Localization

import com.sih.repository.InspectionRepository
import com.sih.network.ApiClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    selectedLanguage: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val liveResult = InspectionRepository.currentInspection
    val inspectionIdInt = InspectionRepository.currentInspectionId ?: 1
    val inspectionId = inspectionIdInt.toString()
    val scope = rememberCoroutineScope()

    val liveProduct = liveResult?.product
    val liveViolations = liveResult?.violations ?: emptyList()
    val isNonCompliant = liveResult?.compliance?.status?.uppercase() == "FAIL"

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null) {
            Toast.makeText(context, "Export cancelled", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    var writtenFromRemote = false
                    try {
                        val response = ApiClient.getService().getReportPdf(inspectionIdInt)
                        if (response.isSuccessful && response.body() != null) {
                            context.contentResolver.openOutputStream(uri)?.use { stream ->
                                response.body()!!.byteStream().copyTo(stream)
                            }
                            writtenFromRemote = true
                        }
                    } catch (netEx: Exception) {
                        Log.d("ReportScreen", "Remote backend PDF not reachable: ${netEx.message}. Generating on-device report.")
                    }

                    if (writtenFromRemote) {
                        true
                    } else {
                        // Generate on-device using real inspected commodity details
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            ReportGenerator.writeReportToStream(context, liveResult, inspectionId, stream)
                        } ?: false
                    }
                }
                if (success) {
                    ContextCompat.getMainExecutor(context).execute {
                        Toast.makeText(context, "Report saved successfully", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    ContextCompat.getMainExecutor(context).execute {
                        Toast.makeText(context, "Failed to write report content", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ReportScreen", "Error saving report", e)
                ContextCompat.getMainExecutor(context).execute {
                    Toast.makeText(context, "Error saving report: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val handleExport = remember(inspectionId, liveProduct) {
        {
            try {
                val safeName = (liveProduct?.name ?: "Commodity").replace(Regex("[^a-zA-Z0-9_]"), "_")
                createDocumentLauncher.launch("Inspection_Report_${safeName}_$inspectionId.pdf")
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open file picker", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val handleShare = remember(inspectionId, liveResult) {
        {
            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) {
                        ReportGenerator.generateInspectionReport(context, liveResult, inspectionId)
                    }
                    if (file != null && file.exists() && file.length() > 0) {
                        val uri = ReportGenerator.getFileUri(context, file)
                        val prodName = liveProduct?.name ?: "Packaged Commodity"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Legal Metrology Inspection Report - $prodName (#$inspectionId)")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Legal Metrology Inspection Report for $prodName.\n" +
                                "Status: ${if (isNonCompliant) "NON-COMPLIANT" else "COMPLIANT"}\n" +
                                "Violations: ${liveViolations.size} detected.\n" +
                                "Generated by NyayaDrishti."
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Inspection Report"))
                    } else {
                        ContextCompat.getMainExecutor(context).execute {
                            Toast.makeText(context, "Failed to generate report for sharing", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ReportScreen", "Error sharing report", e)
                    ContextCompat.getMainExecutor(context).execute {
                        Toast.makeText(context, "Error sharing report: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inspection Report") },
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
            ReportHeader()
            
            val liveDeclarations = liveResult?.declarations ?: emptyList()
            val liveDate = liveDeclarations.firstOrNull { it.type == "date" }?.value
            val liveConsumerCare = liveDeclarations.firstOrNull { it.type == "consumer_care" }?.value
            val livePacker = liveProduct?.packer ?: liveDeclarations.firstOrNull { it.type == "packer" }?.value
            val liveImporter = liveProduct?.importer ?: liveDeclarations.firstOrNull { it.type == "importer" }?.value

            ReportSection(title = "Product Details") {
                ReportRow("Product Name", liveProduct?.name ?: "Scanned Commodity Package")
                ReportRow("Manufacturer", liveProduct?.manufacturer ?: "Not Detected")
                if (!livePacker.isNullOrBlank()) {
                    ReportRow("Packer", livePacker)
                }
                if (!liveImporter.isNullOrBlank()) {
                    ReportRow("Importer", liveImporter)
                }
                ReportRow("Category", "Packaged Commodity")
                ReportRow("MRP", liveProduct?.mrp ?: "Not Detected")
                ReportRow("Net Quantity", liveProduct?.netQuantity ?: "Not Detected")
                if (!liveDate.isNullOrBlank()) {
                    ReportRow("Mfg / Pkg Date", liveDate)
                }
                if (!liveConsumerCare.isNullOrBlank()) {
                    ReportRow("Consumer Care", liveConsumerCare)
                }
            }

            if (liveDeclarations.isNotEmpty()) {
                ReportSection(title = "Mandatory Declarations Audit (Rule 6)") {
                    liveDeclarations.forEach { d ->
                        val label = when (d.type) {
                            "commodity" -> "Commodity Name (R. 6(1)(a))"
                            "manufacturer" -> "Manufacturer (R. 6(1)(b))"
                            "packer" -> "Packer (R. 6(1)(b))"
                            "importer" -> "Importer (R. 6(1)(c))"
                            "net_quantity" -> "Net Quantity (R. 6(1)(f))"
                            "date" -> "Mfg / Pkg Date (R. 6(1)(d))"
                            "mrp" -> "Retail Price MRP (R. 6(1)(e))"
                            "consumer_care" -> "Consumer Care (R. 6(1)(g))"
                            else -> d.type?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: "Declaration"
                        }
                        val isPresent = d.present == true
                        val valText = if (isPresent) (d.value ?: "Present") else "Missing"
                        ReportRow(
                            label = label,
                            value = valText,
                            color = if (isPresent) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            ReportSection(title = "Compliance Summary") {
                ReportRow("Status", if (isNonCompliant) "NON-COMPLIANT" else "COMPLIANT", color = if (isNonCompliant) MaterialTheme.colorScheme.error else Color(0xFF2E7D32))
                ReportRow("Score", "${((liveResult?.confidence?.overall ?: 0.0f) * 100).toInt()}%")
                ReportRow("Violations", "${liveViolations.size} Detected")
                ReportRow("Inspector", "Officer #${ApiClient.getTokenManager()?.getUserId() ?: 1}")
                ReportRow("Date", liveResult?.createdAt?.take(10) ?: "Today")
            }
            
            ReportSection(title = "Detected Violations") {
                if (liveViolations.isNotEmpty()) {
                    liveViolations.forEach { v ->
                        ViolationItem(v.type ?: v.ruleId ?: "Violation", v.description ?: "Rule Violation")
                    }
                } else {
                    Text("No rule violations detected in this commodity package.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { handleExport() },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export PDF")
                }
                
                OutlinedButton(
                    onClick = { handleShare() },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share")
                }
            }
        }
    }
}

@Composable
fun ReportHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("LEGAL METROLOGY INSPECTION REPORT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Generated by NyayaDrishti", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
    }
}

@Composable
fun ReportSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        content()
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
    }
}

@Composable
fun ReportRow(label: String, value: String, color: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun ViolationItem(type: String, detail: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = "• $type", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Text(text = detail, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp))
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ReportScreenPreview() {
    com.sih.ui.theme.Sih_34Theme {
        ReportScreen("English", {})
    }
}
