package com.sih.ui.screens.history

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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sih.ui.components.InspectionCard
import com.sih.ui.screens.home.mockRecentInspections
import com.sih.util.Localization

import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sih.repository.InspectionRepository
import kotlinx.coroutines.launch

import androidx.compose.material3.Card

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    selectedLanguage: String,
    onInspectionClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var historyList by remember { mutableStateOf<List<com.sih.model.Inspection>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        scope.launch {
            historyList = InspectionRepository.getRecentInspections()
        }
        onPauseOrDispose { }
    }

    val filteredList = historyList.filter {
        searchQuery.isBlank() || it.productName.contains(searchQuery, ignoreCase = true) || it.id.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.getString("history", selectedLanguage), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* TODO: Filter */ }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search product or ID...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredList.size} Inspections Found",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (filteredList.isNotEmpty()) {
                    items(filteredList) { inspection ->
                        InspectionCard(
                            inspection = inspection,
                            onClick = {
                                val id = inspection.id.toIntOrNull()
                                if (id != null) {
                                    scope.launch {
                                        InspectionRepository.getInspectionFull(id)
                                        onInspectionClick(inspection.id)
                                    }
                                } else {
                                    onInspectionClick(inspection.id)
                                }
                            }
                        )
                    }
                } else {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No inspection history found.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    com.sih.ui.theme.Sih_34Theme {
        HistoryScreen("English", {})
    }
}
