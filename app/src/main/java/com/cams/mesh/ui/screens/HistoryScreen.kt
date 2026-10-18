package com.cams.mesh.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cams.mesh.data.local.entity.MaterialEntity
import com.cams.mesh.ui.components.MaterialCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    materials: List<MaterialEntity>,
    onMaterialClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredMaterials = remember(materials, searchQuery, selectedFilter) {
        materials.filter { mat ->
            val matchesQuery = if (searchQuery.isBlank()) true else {
                mat.title.contains(searchQuery, ignoreCase = true) ||
                (mat.originalFileName?.contains(searchQuery, ignoreCase = true) == true) ||
                (mat.textContent?.contains(searchQuery, ignoreCase = true) == true) ||
                mat.uploaderDisplayName.contains(searchQuery, ignoreCase = true)
            }
            val matchesFilter = when (selectedFilter) {
                "UPLOADED" -> mat.isUploadedByLocalUser
                "RECEIVED" -> !mat.isUploadedByLocalUser
                "TEXT" -> mat.contentType == "TEXT"
                "FILE" -> mat.contentType == "FILE"
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Material History") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search materials...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
            )

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = selectedFilter == "UPLOADED",
                    onClick = { selectedFilter = "UPLOADED" },
                    label = { Text("Uploaded") }
                )
                FilterChip(
                    selected = selectedFilter == "RECEIVED",
                    onClick = { selectedFilter = "RECEIVED" },
                    label = { Text("Received") }
                )
                FilterChip(
                    selected = selectedFilter == "TEXT",
                    onClick = { selectedFilter = "TEXT" },
                    label = { Text("Text") }
                )
            }

            if (filteredMaterials.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "No materials stored locally." else "No materials match search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredMaterials) { mat ->
                        MaterialCard(
                            material = mat,
                            onClick = { onMaterialClick(mat.materialId) }
                        )
                    }
                }
            }
        }
    }
}
