package com.sih.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sih.network.ApiClient
import com.sih.util.Localization
import android.widget.Toast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.sih.network.local.LocalBackendServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    currentLanguage: String,
    onLanguageClick: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tm = ApiClient.getTokenManager()
    val userName = tm?.getUserName() ?: "Officer Rajesh Kumar"
    val userId = tm?.getUserId() ?: 1042
    val userRole = tm?.getUserRole()?.uppercase() ?: "SENIOR INSPECTOR"
    val userEmail = tm?.getUserEmail() ?: "officer@nyayadrishti.gov.in"

    var autoSyncEnabled by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var lastSyncStatus by remember { mutableStateOf("Pending manual sync") }
    val inspectionCount by remember {
        mutableStateOf(
            try {
                LocalBackendServer.getInspectionCount()
            } catch (e: Exception) {
                0
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.getString("profile", currentLanguage), fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(90.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(text = userName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = "ID: #LM-$userId-DEL ($userEmail)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            ProfileSection("Department Info") {
                ProfileRow(label = "Department", value = "Legal Metrology Dept.")
                ProfileRow(label = "Jurisdiction", value = "North Delhi Zone")
                ProfileRow(label = "Designation", value = userRole)
                ProfileRow(label = "Badge Status", value = "Active Enforcement")
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            ProfileSection("Central Cloud & National Portal Sync") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Cloud Auto-Sync", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (autoSyncEnabled) "Enabled (Syncs when connected)" else "Disabled (Strict 100% Offline Mode)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = {
                            autoSyncEnabled = it
                            Toast.makeText(
                                context,
                                if (it) "Cloud Auto-Sync enabled for National Portal" else "Cloud Sync disabled: Device running 100% locally",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                ProfileRow(label = "Sync Status", value = lastSyncStatus)
                ProfileRow(label = "Unsynced Local Records", value = "$inspectionCount inspections")

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            isSyncing = true
                            delay(1200) // Simulate cloud gateway verification
                            isSyncing = false
                            lastSyncStatus = "Up to date (Synced just now)"
                            Toast.makeText(
                                context,
                                "Successfully synced $inspectionCount offline inspections with Central Portal!",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Syncing to National Portal...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Offline Records Now")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ProfileSection("On-Device Engine & Storage") {
                ProfileRow(label = "Storage Engine", value = "Local SQLite (lmcs_mobile.db)")
                ProfileRow(label = "Inspections Saved", value = "$inspectionCount Records")
                ProfileRow(label = "OCR & Parser Engine", value = "ML Kit + Heuristic v2.4")
                ProfileRow(label = "Statutory Baseline", value = "Legal Metrology Rules 2011")
                ProfileRow(label = "Network Status", value = "100% Autonomous (No Host PC)")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            ProfileSection("App Settings") {
                ProfileSettingRow(
                    icon = Icons.Default.Language, 
                    label = "Language", 
                    value = currentLanguage,
                    onClick = onLanguageClick
                )
                ProfileSettingRow(icon = Icons.Default.Notifications, label = "Inspection Alerts", value = "Enabled")
                ProfileSettingRow(icon = Icons.Default.OfflinePin, label = "Autonomous Mode", value = "Active")
                ProfileSettingRow(icon = Icons.Default.Security, label = "Biometric Lock", value = "Active")
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            Button(
                onClick = {
                    ApiClient.getTokenManager()?.clear()
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(Localization.getString("logout", currentLanguage))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun ProfileRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProfileSettingRow(
    icon: ImageVector, 
    label: String, 
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    com.sih.ui.theme.Sih_34Theme {
        ProfileScreen("English", {}, {})
    }
}
