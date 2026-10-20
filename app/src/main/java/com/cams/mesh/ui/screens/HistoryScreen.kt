package com.cams.mesh.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cams.mesh.data.local.entity.MaterialEntity
import com.cams.mesh.data.local.entity.SessionEntity
import com.cams.mesh.ui.components.MaterialCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    materials: List<MaterialEntity>,
    sessions: List<SessionEntity>,
    onMaterialClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var selectedSessionId by remember { mutableStateOf<String?>("ALL") }

    val sessionMap = remember(sessions) {
        sessions.associate { it.sessionId to it.sessionName.ifEmpty { "Room ${it.roomCode}" } }
    }

    val filteredMaterials = remember(materials, searchQuery, selectedFilter, selectedSessionId) {
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
                "EXTERNAL" -> mat.sessionId == null || mat.uploaderDeviceId == "external_web" || mat.uploaderDeviceId == "teacher_web"
                "TEXT" -> mat.contentType == "TEXT"
                "FILE" -> mat.contentType == "FILE"
                else -> true
            }
            val matchesSession = when (selectedSessionId) {
                "ALL" -> true
                "EXTERNAL" -> mat.sessionId == null
                else -> mat.sessionId == selectedSessionId
            }
            matchesQuery && matchesFilter && matchesSession
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
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

            // Room Filter Chips (horizontal scroll)
            Text(
                text = "Filter by Room:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSessionId == "ALL",
                    onClick = { selectedSessionId = "ALL" },
                    label = { Text("All Rooms") }
                )
                FilterChip(
                    selected = selectedSessionId == "EXTERNAL",
                    onClick = { selectedSessionId = "EXTERNAL" },
                    label = { Text("External Uploads") }
                )
                sessions.forEach { sess ->
                    val displayName = sess.sessionName.ifEmpty { "Room ${sess.roomCode}" }
                    FilterChip(
                        selected = selectedSessionId == sess.sessionId,
                        onClick = { selectedSessionId = sess.sessionId },
                        label = { Text(displayName) }
                    )
                }
            }

            // Type Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All Types") }
                )
                FilterChip(
                    selected = selectedFilter == "EXTERNAL",
                    onClick = { selectedFilter = "EXTERNAL" },
                    label = { Text("External User") }
                )
                FilterChip(
                    selected = selectedFilter == "UPLOADED",
                    onClick = { selectedFilter = "UPLOADED" },
                    label = { Text("Uploaded by Me") }
                )
                FilterChip(
                    selected = selectedFilter == "RECEIVED",
                    onClick = { selectedFilter = "RECEIVED" },
                    label = { Text("Received") }
                )
                FilterChip(
                    selected = selectedFilter == "FILE",
                    onClick = { selectedFilter = "FILE" },
                    label = { Text("Files") }
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
                        text = if (searchQuery.isBlank()) "No materials match the selected room/filters." else "No materials match search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredMaterials, key = { it.materialId }) { mat ->
                        val roomName = if (mat.sessionId == null) {
                            "External Upload"
                        } else {
                            sessionMap[mat.sessionId] ?: "Unknown Room"
                        }
                        MaterialCard(
                            material = mat,
                            sessionName = roomName,
                            onClick = { onMaterialClick(mat.materialId) }
                        )
                    }
                }
            }
        }
    }
}
