package com.cams.mesh.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cams.mesh.data.local.entity.ChatMessageEntity
import com.cams.mesh.data.local.entity.MaterialEntity
import com.cams.mesh.util.AudioPlaybackManager
import com.cams.mesh.util.AudioRecordingManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassroomChatScreen(
    roomName: String,
    roomCode: String,
    messages: List<ChatMessageEntity>,
    roomMaterials: List<MaterialEntity> = emptyList(),
    peerCount: Int = 0,
    isConnected: Boolean = true,
    onBackClick: () -> Unit,
    onSendMessage: (String) -> Unit,
    onUploadFileClick: (Uri) -> Unit,
    onUploadAudioFile: ((File) -> Unit)? = null,
    onPasteTextClick: () -> Unit,
    onViewRoomFilesClick: () -> Unit
) {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val audioRecordingManager = remember { AudioRecordingManager(context) }
    val audioPlaybackManager = remember { AudioPlaybackManager(context) }
    var isRecordingAudio by remember { mutableStateOf(false) }

    val currentlyPlayingPath by audioPlaybackManager.currentlyPlayingPath.collectAsState()
    val isAudioPlaying by audioPlaybackManager.isPlaying.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            if (audioRecordingManager.isRecording()) {
                audioRecordingManager.stopRecording()?.delete()
            }
            audioPlaybackManager.release()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val file = audioRecordingManager.startRecording()
            if (file != null) {
                isRecordingAudio = true
                Toast.makeText(context, "Recording audio...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not start audio recorder", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission required for audio recording", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (audioRecordingManager.isRecording()) {
                audioRecordingManager.stopRecording()?.delete()
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onUploadFileClick(it) }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (roomName.isNotBlank()) roomName else "Room Chat",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        val statusDesc = if (isConnected) {
                            "Room: $roomCode • $peerCount connected peer${if (peerCount != 1) "s" else ""}"
                        } else {
                            "Room: $roomCode • Connecting to peers..."
                        }
                        Text(
                            text = statusDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onViewRoomFilesClick) {
                        Icon(Icons.Default.Folder, contentDescription = "Room Files")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No messages in this classroom yet. Send a message, upload a file, or paste text to share with peers!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.messageId }) { msg ->
                        // Check if this message represents a shared voice recording
                        val isVoiceNoteNotice = msg.text.contains("voice recording", ignoreCase = true) || msg.text.contains("voice note", ignoreCase = true)
                        val matchingAudioMaterial = if (isVoiceNoteNotice) {
                            roomMaterials.firstOrNull { mat ->
                                val isAudioType = mat.mimeType.startsWith("audio/") || (mat.originalFileName?.endsWith(".m4a", ignoreCase = true) == true)
                                isAudioType && mat.localStoragePath != null && File(mat.localStoragePath).exists() &&
                                        (mat.uploaderDeviceId == msg.senderDeviceId || Math.abs(mat.createdAt - msg.timestamp) < 60000)
                            }
                        } else null

                        ChatBubble(
                            message = msg,
                            audioFilePath = matchingAudioMaterial?.localStoragePath,
                            isPlaying = matchingAudioMaterial?.localStoragePath != null &&
                                    currentlyPlayingPath == matchingAudioMaterial.localStoragePath && isAudioPlaying,
                            onPlayAudioToggle = { path ->
                                audioPlaybackManager.togglePlay(path)
                            }
                        )
                    }
                }
            }

            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Upload File", tint = MaterialTheme.colorScheme.primary)
                        }

                        IconButton(onClick = onPasteTextClick) {
                            Icon(Icons.Default.NoteAdd, contentDescription = "Paste Text", tint = MaterialTheme.colorScheme.primary)
                        }

                        IconButton(
                            onClick = {
                                if (isRecordingAudio) {
                                    val recorded = audioRecordingManager.stopRecording()
                                    isRecordingAudio = false
                                    if (recorded != null && onUploadAudioFile != null) {
                                        onUploadAudioFile(recorded)
                                        Toast.makeText(context, "Voice recording shared", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No audio recorded", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                        val file = audioRecordingManager.startRecording()
                                        if (file != null) {
                                            isRecordingAudio = true
                                            Toast.makeText(context, "Recording audio...", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Could not start recording", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isRecordingAudio) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isRecordingAudio) "Stop Recording" else "Record Audio",
                                tint = if (isRecordingAudio) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = if (isRecordingAudio) "Recording voice note..." else textInput,
                            onValueChange = { if (!isRecordingAudio) textInput = it },
                            placeholder = { Text("Type a message...") },
                            modifier = Modifier.weight(1f),
                            enabled = !isRecordingAudio,
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = {
                                val trimmed = textInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    onSendMessage(trimmed)
                                    textInput = ""
                                }
                            },
                            enabled = textInput.isNotBlank(),
                            colors = IconButtonDefaults.filledIconButtonColors()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessageEntity,
    audioFilePath: String? = null,
    isPlaying: Boolean = false,
    onPlayAudioToggle: ((String) -> Unit)? = null
) {
    val alignment = if (message.isFromMe) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isFromMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (message.isFromMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (!message.isFromMe) {
            Text(
                text = message.senderDisplayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = bubbleColor,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isFromMe) 16.dp else 2.dp,
                        bottomEnd = if (message.isFromMe) 2.dp else 16.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                if (audioFilePath != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        IconButton(
                            onClick = { onPlayAudioToggle?.invoke(audioFilePath) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause Voice Note" else "Play Voice Note",
                                tint = textColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isPlaying) "Playing voice note..." else "Voice Note",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor
                            )
                            Text(
                                text = "Tap to listen",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = textColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                } else {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                val timeString = remember(message.timestamp) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
                }
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
