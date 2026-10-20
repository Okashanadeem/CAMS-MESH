package com.cams.mesh.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.ui.text.style.TextOverflow
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
    onRequestDownload: ((String) -> Unit)? = null,
    onDeleteLocalCopy: (String) -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = material?.title?.ifBlank { material.originalFileName ?: "Material Details" } ?: "Material Details",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
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
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val uploader = when {
                            material.uploaderDeviceId == "external_web" || material.uploaderDeviceId == "teacher_web" -> "External User"
                            material.uploaderDisplayName.contains("Teacher", ignoreCase = true) -> "External User"
                            material.uploaderDisplayName.contains("Student", ignoreCase = true) -> "User"
                            material.uploaderDisplayName.isNotBlank() -> material.uploaderDisplayName
                            else -> "User"
                        }
                        Text("Type: ${material.contentType}", fontWeight = FontWeight.SemiBold)
                        Text("Shared By: $uploader")
                        val dateFormatted = remember(material.createdAt) {
                            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(material.createdAt))
                        }
                        Text("Date: $dateFormatted")
                        if (material.fileSizeBytes != null) {
                            val kb = material.fileSizeBytes / 1024
                            val mb = kb / 1024f
                            val sizeStr = if (mb >= 1.0f) String.format(Locale.getDefault(), "%.1f MB", mb) else "$kb KB"
                            Text("Size: $sizeStr")
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
                                if (material.localStoragePath == null) {
                                    onRequestDownload?.invoke(material.materialId)
                                    Toast.makeText(context, "Requesting file from peers...", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (material.localStoragePath != null) {
                                    val sourceFile = File(material.localStoragePath)
                                    if (sourceFile.exists()) {
                                        try {
                                            val isImage = material.mimeType.startsWith("image/") ||
                                                    listOf(".jpg", ".jpeg", ".png", ".webp", ".gif").any {
                                                        sourceFile.name.endsWith(it, ignoreCase = true)
                                                    }

                                            val destName = material.originalFileName ?: sourceFile.name

                                            if (isImage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                // Save to MediaStore Images so it instantly appears in Android Gallery
                                                val contentValues = ContentValues().apply {
                                                    put(MediaStore.Images.Media.DISPLAY_NAME, destName)
                                                    put(MediaStore.Images.Media.MIME_TYPE, material.mimeType.ifBlank { "image/jpeg" })
                                                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CAMS-MESH")
                                                    put(MediaStore.Images.Media.IS_PENDING, 1)
                                                }
                                                val resolver = context.contentResolver
                                                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                                                if (imageUri != null) {
                                                    resolver.openOutputStream(imageUri)?.use { output ->
                                                        sourceFile.inputStream().use { input ->
                                                            input.copyTo(output)
                                                        }
                                                    }
                                                    contentValues.clear()
                                                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                                                    resolver.update(imageUri, contentValues, null, null)

                                                    Toast.makeText(context, "Saved to Gallery & Pictures", Toast.LENGTH_SHORT).show()

                                                    // Open image immediately in Gallery / Viewer
                                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(imageUri, material.mimeType.ifBlank { "image/jpeg" })
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(viewIntent)
                                                    return@Button
                                                }
                                            }

                                            // Fallback or non-image files (PDFs, docs, etc.) -> Save to Downloads/Pictures
                                            val targetDir = if (isImage) {
                                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                            } else {
                                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                            }
                                            if (!targetDir.exists()) {
                                                targetDir.mkdirs()
                                            }

                                            var targetFile = File(targetDir, destName)
                                            var count = 1
                                            val dotIndex = destName.lastIndexOf('.')
                                            val base = if (dotIndex > 0) destName.substring(0, dotIndex) else destName
                                            val ext = if (dotIndex > 0) destName.substring(dotIndex) else ""

                                            while (targetFile.exists()) {
                                                targetFile = File(targetDir, "${base}_$count$ext")
                                                count++
                                            }

                                            sourceFile.copyTo(targetFile, overwrite = true)

                                            // Trigger Android Media Scanner so Gallery and file managers immediately discover it
                                            MediaScannerConnection.scanFile(
                                                context,
                                                arrayOf(targetFile.absolutePath),
                                                arrayOf(material.mimeType)
                                            ) { _, _ -> }

                                            val toastMsg = if (isImage) "Saved to Gallery" else "Saved to Downloads"
                                            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()

                                            // Immediately open the downloaded file like WhatsApp
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                targetFile
                                            )
                                            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, material.mimeType)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            try {
                                                context.startActivity(openIntent)
                                            } catch (e: Exception) {
                                                val chooser = Intent.createChooser(openIntent, "Open with")
                                                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(chooser)
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                                            com.cams.mesh.ui.components.ErrorReporter.reportIssue(
                                                context,
                                                "Download / Save Error for file '${material.title}': ${e.message}"
                                            )
                                        }
                                    } else {
                                        Toast.makeText(context, "File is not downloaded locally yet", Toast.LENGTH_SHORT).show()
                                        com.cams.mesh.ui.components.ErrorReporter.reportIssue(
                                            context,
                                            "File Missing on Disk for '${material.title}' (ID: ${material.materialId})"
                                        )
                                    }
                                }
                            },
                            enabled = true,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (material.localStoragePath != null) "Save / Open" else "Download")
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
                                        context.startActivity(Intent.createChooser(shareIntent, "Share file"))
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
