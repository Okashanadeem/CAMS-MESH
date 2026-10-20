package com.cams.mesh.ui.screens

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
import com.cams.mesh.data.local.entity.SessionEntity
import com.cams.mesh.data.network.NetworkStatus
import com.cams.mesh.ui.components.QrCodeView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassroomScreen(
    activeSession: SessionEntity?,
    joinedClassrooms: List<SessionEntity>,
    networkStatus: NetworkStatus,
    onCreateClassroomClick: () -> Unit,
    onJoinClassroomClick: () -> Unit,
    onSwitchClassroomClick: (SessionEntity) -> Unit,
    onDeleteSession: (SessionEntity) -> Unit,
    onOpenClassroomClick: (SessionEntity) -> Unit,
    onExternalWebUploadClick: () -> Unit,
    onLeaveClassroomClick: () -> Unit
) {
    var showBigQrDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CAMS-MESH", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onExternalWebUploadClick) {
                        Icon(Icons.Default.Language, contentDescription = "External User Web Upload")
                    }
                    if (activeSession != null) {
                        IconButton(onClick = { onOpenClassroomClick(activeSession) }) {
                            Icon(Icons.Default.Chat, contentDescription = "Room Chat")
                        }
                        IconButton(onClick = onLeaveClassroomClick) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Leave Room")
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
            // 1. Action Buttons at the very TOP
            item {
                Spacer(modifier = Modifier.height(4.dp))
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
                        Text("Create Room")
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

            // 2. Current Room Status (Only shown once, with QR code & quick enter)
            if (activeSession != null) {
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Current Room: ${activeSession.sessionName.ifEmpty { "Room ${activeSession.roomCode}" }}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val statusText = when (networkStatus) {
                                        is NetworkStatus.Idle -> "Offline"
                                        is NetworkStatus.Advertising -> "Advertising • Code: ${networkStatus.roomCode}"
                                        is NetworkStatus.Discovering -> "Searching for Room..."
                                        is NetworkStatus.Connected -> "Connected (${networkStatus.peerCount} peers in room)"
                                        is NetworkStatus.Error -> "Connection issue detected"
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (networkStatus is NetworkStatus.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                    if (networkStatus is NetworkStatus.Error) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        com.cams.mesh.ui.components.ReportIssueButton(
                                            errorMessage = "Room Screen Network Error:\n${networkStatus.message}"
                                        )
                                    }
                                }

                                FilledTonalButton(
                                    onClick = { onOpenClassroomClick(activeSession) },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Enter")
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Code: ${activeSession.roomCode}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "Tap QR to enlarge",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clickable { showBigQrDialog = true }
                                        .padding(4.dp)
                                ) {
                                    QrCodeView(
                                        content = "cams-mesh:${activeSession.roomCode}",
                                        modifier = Modifier.size(68.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Joined Rooms list below buttons & current room (excluding duplicate of active room)
            val otherJoinedRooms = joinedClassrooms.filter { it.sessionId != activeSession?.sessionId }

            if (otherJoinedRooms.isNotEmpty()) {
                item {
                    Text(
                        text = "Other Joined Rooms (${otherJoinedRooms.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(otherJoinedRooms, key = { it.sessionId }) { session ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSwitchClassroomClick(session) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.sessionName.ifEmpty { "Room ${session.roomCode}" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Room Code: ${session.roomCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onSwitchClassroomClick(session) }) {
                                    Text("Switch")
                                }
                                IconButton(
                                    onClick = { onDeleteSession(session) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Remove Room",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (activeSession == null && joinedClassrooms.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.MeetingRoom,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Not in any room",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create a new room or join an existing one using the buttons above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (showBigQrDialog && activeSession != null) {
            AlertDialog(
                onDismissRequest = { showBigQrDialog = false },
                title = {
                    Text(
                        text = activeSession.sessionName.ifEmpty { "Classroom ${activeSession.roomCode}" },
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Room Code: ${activeSession.roomCode}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        QrCodeView(
                            content = "cams-mesh:${activeSession.roomCode}",
                            modifier = Modifier.size(240.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Users can scan this QR code or enter code ${activeSession.roomCode} to join.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBigQrDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
