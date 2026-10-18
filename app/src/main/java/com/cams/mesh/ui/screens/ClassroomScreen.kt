package com.cams.mesh.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cams.mesh.data.local.entity.MaterialEntity
import com.cams.mesh.data.local.entity.SessionEntity
import com.cams.mesh.data.network.NetworkStatus
import com.cams.mesh.ui.components.MaterialCard
import com.cams.mesh.ui.components.QrCodeView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassroomScreen(
    activeSession: SessionEntity?,
    joinedClassrooms: List<SessionEntity>,
    networkStatus: NetworkStatus,
    recentMaterials: List<MaterialEntity>,
    onCreateClassroomClick: () -> Unit,
    onJoinClassroomClick: () -> Unit,
    onSwitchClassroomClick: (SessionEntity) -> Unit,
    onChatClick: () -> Unit,
    onTeacherWebUploadClick: () -> Unit,
    onPasteTextClick: () -> Unit,
    onUploadFileSelected: (Uri) -> Unit,
    onMaterialClick: (String) -> Unit,
    onLeaveClassroomClick: () -> Unit
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onUploadFileSelected(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CAMS-MESH", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onTeacherWebUploadClick) {
                        Icon(Icons.Default.Language, contentDescription = "Teacher Web Upload")
                    }
                    if (activeSession != null) {
                        IconButton(onClick = onChatClick) {
                            Icon(Icons.Default.Chat, contentDescription = "Classroom Chat")
                        }
                        IconButton(onClick = onLeaveClassroomClick) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Leave Classroom")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (activeSession != null) "Classroom: ${activeSession.sessionName.ifEmpty { "Active Room" }}" else "Not in Classroom",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            if (activeSession != null) {
                                FilledTonalButton(
                                    onClick = onChatClick,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Chat")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val statusText = when (networkStatus) {
                            is NetworkStatus.Idle -> "Offline"
                            is NetworkStatus.Advertising -> "Advertising Room Code: ${networkStatus.roomCode}"
                            is NetworkStatus.Discovering -> "Searching for Room..."
                            is NetworkStatus.Connected -> "Connected (${networkStatus.peerCount} peers in room ${networkStatus.roomCode})"
                            is NetworkStatus.Error -> "Error: ${networkStatus.message}"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        if (activeSession != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Code: ${activeSession.roomCode}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black
                                )
                                QrCodeView(
                                    content = "cams-mesh:${activeSession.roomCode}",
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Joined Classrooms list (switching & joining multiple classrooms)
            if (joinedClassrooms.isNotEmpty()) {
                item {
                    Text(
                        text = "Joined Classrooms",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(joinedClassrooms) { session ->
                    val isActive = session.sessionId == activeSession?.sessionId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSwitchClassroomClick(session) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.sessionName.ifEmpty { "Classroom ${session.roomCode}" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Room Code: ${session.roomCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isActive) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Active") },
                                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                )
                            } else {
                                TextButton(onClick = { onSwitchClassroomClick(session) }) {
                                    Text("Switch")
                                }
                            }
                        }
                    }
                }
            }

            // Action Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onCreateClassroomClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create")
                    }
                    OutlinedButton(
                        onClick = onJoinClassroomClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Join Room")
                    }
                }
            }

            if (activeSession != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload File")
                        }
                        FilledTonalButton(
                            onClick = onPasteTextClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Paste Text")
                        }
                    }
                }
            }

            // Recent Materials Section
            item {
                Text(
                    text = "Recent Materials",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (recentMaterials.isEmpty()) {
                item {
                    Text(
                        text = "No materials available yet. Share text or files to begin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(recentMaterials) { material ->
                    MaterialCard(
                        material = material,
                        onClick = { onMaterialClick(material.materialId) }
                    )
                }
            }
        }
    }
}
