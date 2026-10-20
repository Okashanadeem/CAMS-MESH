package com.cams.mesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cams.mesh.data.local.entity.MaterialEntity
import com.cams.mesh.ui.components.MaterialCard
import com.cams.mesh.ui.components.QrCodeView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherWebPortalScreen(
    ipAddress: String,
    port: Int,
    isServerActive: Boolean,
    externalMaterials: List<MaterialEntity>,
    onToggleServer: (Boolean) -> Unit,
    onMaterialClick: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val portalUrl = remember(ipAddress, port) {
        "http://$ipAddress:$port"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("External User Web Portal") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "External User Portal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (isServerActive) "Portal Active" else "Portal Stopped",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Switch(
                            checked = isServerActive,
                            onCheckedChange = { onToggleServer(it) }
                        )
                    }
                }
            }

            if (isServerActive) {
                item {
                    Text(
                        text = "Ask external user to scan this QR with their camera / browser or visit the link:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }

                item {
                    QrCodeView(
                        content = portalUrl,
                        modifier = Modifier.size(200.dp)
                    )
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = portalUrl,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            if (!portalUrl.contains("192.168.43.1") && !portalUrl.contains("127.0.0.1")) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "If using mobile hotspot, external user can also try: http://192.168.43.1:8080",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "How it works (Zero App Installation):",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "1. External user turns on Wi-Fi and connects to this device's hotspot (or shared Wi-Fi).",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "2. External user scans the QR code or opens Chrome / Safari to the URL above.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "3. Uploaded slides, docs, pictures, or notes appear right below under External User Content.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // External Uploaded Content List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "External User Uploaded Content (${externalMaterials.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (externalMaterials.isEmpty()) {
                item {
                    Text(
                        text = "No content uploaded by external users yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(externalMaterials, key = { it.materialId }) { material ->
                    MaterialCard(
                        material = material,
                        onClick = { onMaterialClick(material.materialId) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
