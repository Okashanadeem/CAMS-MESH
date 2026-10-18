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
fun CreateClassroomScreen(
    onBackClick: () -> Unit,
    onCreateConfirm: (name: String, approvalMode: Boolean) -> Unit
) {
    var classroomName by remember { mutableStateOf("") }
    var approvalMode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Classroom") },
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
                value = classroomName,
                onValueChange = { classroomName = it },
                label = { Text("Classroom Name (Optional)") },
                placeholder = { Text("e.g. Physics 101") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Approval Mode", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Require approval before peers can join",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = approvalMode,
                    onCheckedChange = { approvalMode = it }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onCreateConfirm(classroomName.trim(), approvalMode) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Session")
            }
        }
    }
}
