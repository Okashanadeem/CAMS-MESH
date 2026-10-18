package com.cams.mesh.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteTextScreen(
    onBackClick: () -> Unit,
    onShareText: (title: String, content: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var textContent by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paste Text Note") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                placeholder = { Text("e.g. Chapter 1 Notes") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = textContent,
                onValueChange = { textContent = it },
                label = { Text("Content") },
                placeholder = { Text("Paste or type notes here...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Button(
                onClick = {
                    val finalTitle = title.ifBlank {
                        textContent.lines().firstOrNull()?.take(30) ?: "Untitled Note"
                    }
                    onShareText(finalTitle, textContent.trim())
                },
                enabled = textContent.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Share Note")
            }
        }
    }
}
