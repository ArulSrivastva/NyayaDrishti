package com.sih.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sih.model.RiskLevel

@Composable
fun RiskBadge(level: RiskLevel, modifier: Modifier = Modifier) {
    val (text, color) = when (level) {
        RiskLevel.LOW -> "Low Risk" to Color(0xFF2E7D32)
        RiskLevel.MEDIUM -> "Medium Risk" to Color(0xFFEF6C00)
        RiskLevel.HIGH -> "High Risk" to MaterialTheme.colorScheme.error
    }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
