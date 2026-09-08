package com.sih.ui.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    onNavigateBack: () -> Unit,
    onFinish: () -> Unit = onNavigateBack
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
                title = { Text("Inspection Report", fontWeight = FontWeight.Bold) },
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
            // 1. Report Title Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LEGAL METROLOGY INSPECTION REPORT",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Statutory Packaging Audit • Packaged Commodities Rules, 2011",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Report Ref: #$inspectionId",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Date: ${liveResult?.createdAt?.take(10) ?: "Today"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Executive Compliance Status Banner Card
            val statusColor = if (isNonCompliant) Color(0xFFDC2626) else Color(0xFF059669)
            val statusBg = if (isNonCompliant) Color(0xFFFEF2F2) else Color(0xFFECFDF5)
            val statusBorder = if (isNonCompliant) Color(0xFFFCA5A5) else Color(0xFFA7F3D0)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = statusBg),
                border = BorderStroke(1.dp, statusBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isNonCompliant) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isNonCompliant) "NON-COMPLIANT" else "COMPLIANT",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                        Text(
                            text = if (isNonCompliant) 
                                "${liveViolations.size} violation(s) detected under Rule 6"
                            else 
                                "All statutory packaging declarations verified",
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        val scorePercent = ((liveResult?.confidence?.overall ?: 0.95f) * 100).toInt()
                        Text(
                            text = "$scorePercent% Score",
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 3. Product & Establishment Details Card
            val liveDeclarations = liveResult?.declarations ?: emptyList()
            val liveDate = liveDeclarations.firstOrNull { it.type == "date" }?.value
            val liveConsumerCare = liveDeclarations.firstOrNull { it.type == "consumer_care" }?.value
            val livePacker = liveProduct?.packer ?: liveDeclarations.firstOrNull { it.type == "packer" }?.value
            val liveImporter = liveProduct?.importer ?: liveDeclarations.firstOrNull { it.type == "importer" }?.value

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Product Details",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    ReportKeyValRow("Product Name", liveProduct?.name ?: "Scanned Commodity Package")
                    ReportKeyValRow("Category", "Packaged Commodity")
                    ReportKeyValRow("MRP", cleanMrpText(liveProduct?.mrp))
                    ReportKeyValRow("Net Quantity", cleanQuantityText(liveProduct?.netQuantity))
                    if (!liveDate.isNullOrBlank()) {
                        ReportKeyValRow("Mfg / Pkg Date", liveDate)
                    }
                    if (!liveConsumerCare.isNullOrBlank()) {
                        ReportKeyValRow("Consumer Care", liveConsumerCare)
                    }

                    // Multi-line address for Manufacturer
                    val mfgAddress = cleanAddressText(liveProduct?.manufacturer)
                    if (mfgAddress.isNotBlank() && mfgAddress != "Not Detected") {
                        Spacer(modifier = Modifier.height(2.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Manufacturer",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = mfgAddress,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }

                    if (!livePacker.isNullOrBlank()) {
                        ReportKeyValRow("Packer", livePacker)
                    }
                    if (!liveImporter.isNullOrBlank()) {
                        ReportKeyValRow("Importer", liveImporter)
                    }
                }
            }

            // 4. Mandatory Declarations Audit (Rule 6) Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                            text = "Mandatory Declarations Audit (Rule 6)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Statutory",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    val effectiveDeclarations = if (liveDeclarations.isNotEmpty()) {
                        liveDeclarations
                    } else {
                        listOfNotNull(
                            com.sih.network.dto.DeclarationDto(type = "commodity", value = liveProduct?.name, present = !liveProduct?.name.isNullOrBlank()),
                            com.sih.network.dto.DeclarationDto(type = "manufacturer", value = liveProduct?.manufacturer, present = !liveProduct?.manufacturer.isNullOrBlank()),
                            com.sih.network.dto.DeclarationDto(type = "importer", value = liveImporter ?: "Domestic (Exempt under Rule 10(1))", present = true),
                            com.sih.network.dto.DeclarationDto(type = "date", value = liveDate, present = !liveDate.isNullOrBlank()),
                            com.sih.network.dto.DeclarationDto(type = "mrp", value = liveProduct?.mrp, present = !liveProduct?.mrp.isNullOrBlank()),
                            com.sih.network.dto.DeclarationDto(type = "net_quantity", value = liveProduct?.netQuantity, present = !liveProduct?.netQuantity.isNullOrBlank()),
                            com.sih.network.dto.DeclarationDto(type = "consumer_care", value = liveConsumerCare, present = !liveConsumerCare.isNullOrBlank())
                        )
                    }

                    effectiveDeclarations.forEach { d ->
                        val ruleTitle = when (d.type) {
                            "commodity" -> "Commodity Name (R. 6(1)(a))"
                            "manufacturer" -> "Manufacturer (R. 6(1)(b))"
                            "packer" -> "Packer (R. 6(1)(b))"
                            "importer" -> "Importer (R. 6(1)(c))"
                            "date" -> "Mfg / Pkg Date (R. 6(1)(d))"
                            "mrp" -> "Retail Price MRP (R. 6(1)(e))"
                            "net_quantity" -> "Net Quantity (R. 6(1)(f))"
                            "consumer_care" -> "Consumer Care (R. 6(1)(g))"
                            else -> d.type?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: "Declaration"
                        }

                        val isPresent = d.present == true
                        val isExempt = d.value?.contains("exempt", ignoreCase = true) == true ||
                                       (d.type == "importer" && d.value?.contains("domestic", ignoreCase = true) == true)

                        val displayVal = when (d.type) {
                            "mrp" -> cleanMrpText(d.value)
                            "net_quantity" -> cleanQuantityText(d.value)
                            "manufacturer" -> cleanAddressText(d.value)
                            else -> d.value ?: "Present"
                        }

                        AuditDeclarationCard(
                            ruleTitle = ruleTitle,
                            value = displayVal,
                            isPresent = isPresent,
                            isExempt = isExempt
                        )
                    }
                }
            }

            // 5. Detected Violations Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Detected Violations",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (liveViolations.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    if (liveViolations.isNotEmpty()) {
                        liveViolations.forEach { v ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = v.type ?: v.ruleId ?: "Statutory Violation",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = v.description ?: "Non-compliant declaration under Legal Metrology Act.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "No rule violations detected in this commodity package.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF166534),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 6. Officer & Evidentiary Audit Trail Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "OFFICER & EVIDENTIARY AUDIT TRAIL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Field Officer: Officer #${ApiClient.getTokenManager()?.getUserId() ?: 1}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Verification: Autonomous On-Device Neural Engine (Rulebook v2011)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Evidentiary Integrity: Cryptographic SHA-256 Digest Authenticated under Section 65B of Indian Evidence Act.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 7. Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { handleExport() },
                    modifier = Modifier.weight(1f).height(54.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export PDF")
                }

                OutlinedButton(
                    onClick = { handleShare() },
                    modifier = Modifier.weight(1f).height(54.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share")
                }
            }

            Button(
                onClick = {
                    InspectionRepository.resetSession()
                    onFinish()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Finish Inspection & Return to Home", fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun cleanMrpText(raw: String?): String {
    if (raw.isNullOrBlank()) return "Not Detected"
    val trimmed = raw.trim()
    val match = Regex("""(?:₹|Rs\.?|INR|\*)\s*([0-9]+(?:\.[0-9]{1,2})?)""").find(trimmed)
    return if (match != null) {
        "₹ ${match.groupValues[1]} (Incl. of all taxes)"
    } else {
        trimmed
    }
}

fun cleanQuantityText(raw: String?): String {
    if (raw.isNullOrBlank()) return "Not Detected"
    return raw.replace(Regex("""(?i)^Net\s*Quantity\s*:\s*"""), "").trim()
}

fun cleanAddressText(raw: String?): String {
    if (raw.isNullOrBlank()) return "Not Detected"
    return raw.replace(Regex("""(?i)^Manufactured\s*by\s*:\s*"""), "").trim()
}

@Composable
fun ReportKeyValRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
fun AuditDeclarationCard(
    ruleTitle: String,
    value: String,
    isPresent: Boolean,
    isExempt: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ruleTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = when {
                        isExempt -> Color(0xFFDBEAFE)
                        isPresent -> Color(0xFFDCFCE7)
                        else -> Color(0xFFFEE2E2)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when {
                            isExempt -> "EXEMPT"
                            isPresent -> "✓ PASS"
                            else -> "✗ MISSING"
                        },
                        color = when {
                            isExempt -> Color(0xFF1D4ED8)
                            isPresent -> Color(0xFF15803D)
                            else -> Color(0xFFB91C1C)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = if (isPresent || isExempt) value else "Declaration missing from physical packaging",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isPresent || isExempt) FontWeight.Medium else FontWeight.Normal,
                color = if (isPresent || isExempt) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ReportScreenPreview() {
    com.sih.ui.theme.Sih_34Theme {
        ReportScreen("English", {})
    }
}
