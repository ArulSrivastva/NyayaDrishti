package com.sih.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sih.model.ComplianceStatus
import com.sih.model.Inspection
import com.sih.model.RiskLevel
import com.sih.ui.components.InspectionCard
import com.sih.ui.components.PrimaryButton
import com.sih.ui.components.StatusCard
import com.sih.util.Localization
import java.time.LocalDateTime

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sih.repository.InspectionRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    selectedLanguage: String,
    onNewInspection: () -> Unit,
    onInspectionClick: (Inspection) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var inspectionsList by remember { mutableStateOf<List<Inspection>>(emptyList()) }
    var activeDrafts by remember { mutableStateOf<List<com.sih.model.InspectionDraft>>(emptyList()) }

    LaunchedEffect(Unit) {
        inspectionsList = InspectionRepository.getRecentInspections()
        activeDrafts = InspectionRepository.getActiveDrafts(context)
    }

    val totalCount = inspectionsList.size
    val violationCount = inspectionsList.count { it.status == ComplianceStatus.NON_COMPLIANT }
    val highRiskCount = inspectionsList.count { it.riskLevel == RiskLevel.HIGH }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Localization.getDualString("app_name", selectedLanguage),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                    }
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                GreetingSection(selectedLanguage)
            }
            
            item {
                StatsSection(
                    language = selectedLanguage,
                    inspectionsCount = totalCount.toString(),
                    violationsCount = violationCount.toString(),
                    highRiskCount = highRiskCount.toString()
                )
            }

            // System 2: Active Draft Recovery
            if (activeDrafts.isNotEmpty()) {
                item {
                    Text(
                        text = "Resume Inspection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(activeDrafts) { draft ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Draft: ${draft.inspectionId}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = draft.state.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            draft.establishmentName?.let {
                                Text(text = "Establishment: $it", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                text = "Last Updated: ${draft.lastUpdated.take(19).replace('T', ' ')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                androidx.compose.material3.Button(
                                    onClick = {
                                        InspectionRepository.currentDraft = draft
                                        InspectionRepository.currentEstablishmentName = draft.establishmentName
                                        InspectionRepository.currentInspectionType = draft.inspectionType
                                        InspectionRepository.currentLocation = draft.location
                                        onNewInspection()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Resume")
                                }
                                androidx.compose.material3.OutlinedButton(
                                    onClick = {
                                        InspectionRepository.abandonDraft(context, draft.inspectionId)
                                        activeDrafts = InspectionRepository.getActiveDrafts(context)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Abandon")
                                }
                            }
                        }
                    }
                }
            }
            
            item {
                PrimaryButton(
                    text = Localization.getDualString("start_inspection", selectedLanguage),
                    onClick = onNewInspection
                )
            }
            
            item {
                Text(
                    text = Localization.getDualString("recent_inspections", selectedLanguage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            if (inspectionsList.isNotEmpty()) {
                items(inspectionsList) { inspection ->
                    InspectionCard(
                        inspection = inspection,
                        onClick = { onInspectionClick(inspection) }
                    )
                }
            } else {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No recent inspections found. Tap 'Start New Inspection' to scan a commodity package.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun GreetingSection(language: String) {
    Column {
        Text(
            text = Localization.getDualString("good_morning", language),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = Localization.getDualString("legal_metrology", language),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color(0xFFECFDF5), RoundedCornerShape(16.dp))
                .border(0.5.dp, Color(0xFFA7F3D0), RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFF10B981), CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Autonomous On-Device Backend Active",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF047857)
            )
        }
    }
}

@Composable
fun StatsSection(
    language: String,
    inspectionsCount: String = "12",
    violationsCount: String = "04",
    highRiskCount: String = "02"
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusCard(
            title = Localization.getString("inspections", language),
            value = inspectionsCount,
            icon = Icons.Default.TaskAlt,
            modifier = Modifier.weight(1f)
        )
        StatusCard(
            title = Localization.getString("violations", language),
            value = violationsCount,
            icon = Icons.AutoMirrored.Filled.Assignment,
            modifier = Modifier.weight(1f)
        )
        StatusCard(
            title = Localization.getString("high_risk", language),
            value = highRiskCount,
            icon = Icons.Default.ReportProblem,
            modifier = Modifier.weight(1f)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    com.sih.ui.theme.Sih_34Theme {
        HomeScreen("English", onNewInspection = {}, onInspectionClick = {})
    }
}

val mockRecentInspections = listOf(
    Inspection(
        id = "1",
        productName = "Tata Salt 1kg",
        date = LocalDateTime.now().minusHours(2),
        status = ComplianceStatus.COMPLIANT,
        violationCount = 0,
        riskLevel = RiskLevel.LOW,
        productId = "P1"
    ),
    Inspection(
        id = "2",
        productName = "Fortune Soyabean Oil 1L",
        date = LocalDateTime.now().minusHours(5),
        status = ComplianceStatus.NON_COMPLIANT,
        violationCount = 2,
        riskLevel = RiskLevel.HIGH,
        productId = "P2"
    ),
    Inspection(
        id = "3",
        productName = "Amul Gold Milk 500ml",
        date = LocalDateTime.now().minusDays(1),
        status = ComplianceStatus.NEEDS_REVIEW,
        violationCount = 1,
        riskLevel = RiskLevel.MEDIUM,
        productId = "P3"
    ),
    Inspection(
        id = "4",
        productName = "Maggi 2-Minute Noodles",
        date = LocalDateTime.now().minusDays(1),
        status = ComplianceStatus.COMPLIANT,
        violationCount = 0,
        riskLevel = RiskLevel.LOW,
        productId = "P4"
    )
)
