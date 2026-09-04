package com.sih.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sih.model.ComplianceStatus

@Composable
fun DeclarationRow(
    label: String,
    value: String,
    status: ComplianceStatus,
    confidence: Float,
    onClick: () -> Unit
) {
    val (icon, color) = when (status) {
        ComplianceStatus.COMPLIANT -> Icons.Default.CheckCircle to Color(0xFF2E7D32)
        ComplianceStatus.NON_COMPLIANT -> Icons.Default.Warning to MaterialTheme.colorScheme.error
        ComplianceStatus.NEEDS_REVIEW -> Icons.Default.Info to Color(0xFFEF6C00)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.labelMedium)
                Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                ConfidenceIndicator(confidence = confidence, modifier = Modifier.width(120.dp))
            }
            
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
