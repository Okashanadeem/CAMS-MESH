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
fun JoinClassroomScreen(
    onBackClick: () -> Unit,
    onJoinWithCode: (String) -> Unit
) {
    var roomCode by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join Classroom") },
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
            Text(
                text = "Enter 6-character room code:",
                style = MaterialTheme.typography.bodyLarge
            )

            OutlinedTextField(
                value = roomCode,
                onValueChange = { if (it.length <= 6) roomCode = it.uppercase() },
                label = { Text("Room Code") },
                placeholder = { Text("e.g. AB12CD") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onJoinWithCode(roomCode.trim()) },
                enabled = roomCode.length >= 4,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Join Nearby Session")
            }
        }
    }
}
