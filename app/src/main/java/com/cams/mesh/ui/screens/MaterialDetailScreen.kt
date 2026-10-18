package com.cams.mesh.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.cams.mesh.data.local.entity.MaterialEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialDetailScreen(
    material: MaterialEntity?,
    onBackClick: () -> Unit,
    onDeleteLocalCopy: (String) -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(material?.title ?: "Material Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (material != null) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Local Copy")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (material == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("Material not found or deleted.")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Type: ${material.contentType}", fontWeight = FontWeight.SemiBold)
                        Text("Uploader: ${material.uploaderDisplayName}")
                        val dateFormatted = remember(material.createdAt) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(material.createdAt))
                        }
                        Text("Date: $dateFormatted")
                        if (material.fileSizeBytes != null) {
                            val kb = material.fileSizeBytes / 1024
                            Text("Size: $kb KB")
                        }
                        if (material.sha256Hash != null) {
                            Text("SHA-256: ${material.sha256Hash.take(16)}...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (material.contentType == "TEXT" && material.textContent != null) {
                    Text("Text Content:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = material.textContent,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(material.title, material.textContent)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Text")
                    }
                } else if (material.contentType == "FILE") {
                    Text("File Attachment:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Filename: ${material.originalFileName ?: "unknown"}")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (material.localStoragePath != null) {
                                    val sourceFile = File(material.localStoragePath)
                                    if (sourceFile.exists()) {
                                        try {
                                            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                                                android.os.Environment.DIRECTORY_DOWNLOADS
                                            )
                                            if (!downloadsDir.exists()) {
                                                downloadsDir.mkdirs()
                                            }
                                            val destName = material.originalFileName ?: sourceFile.name
                                            var targetFile = File(downloadsDir, destName)
                                            var count = 1
                                            val dotIndex = destName.lastIndexOf('.')
                                            val base = if (dotIndex > 0) destName.substring(0, dotIndex) else destName
                                            val ext = if (dotIndex > 0) destName.substring(dotIndex) else ""

                                            while (targetFile.exists()) {
                                                targetFile = File(downloadsDir, "${base}_$count$ext")
                                                count++
                                            }

                                            sourceFile.copyTo(targetFile, overwrite = true)
                                            Toast.makeText(context, "Saved to Downloads: ${targetFile.name}", Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "File does not exist on disk", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = material.localStoragePath != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download")
                        }

                        OutlinedButton(
                            onClick = {
                                if (material.localStoragePath != null) {
                                    val file = File(material.localStoragePath)
                                    if (file.exists()) {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = material.mimeType
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share or open file"))
                                    } else {
                                        Toast.makeText(context, "File does not exist on disk", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = material.localStoragePath != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog && material != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Local Copy?") },
            text = { Text("This will remove this material from your local device. Copies on other participants' devices will not be affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteLocalCopy(material.materialId)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
