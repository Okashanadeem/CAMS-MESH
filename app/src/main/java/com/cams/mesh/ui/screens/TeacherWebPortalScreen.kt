package com.cams.mesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
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
import com.cams.mesh.ui.components.QrCodeView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherWebPortalScreen(
    ipAddress: String,
    port: Int,
    isServerActive: Boolean,
    onToggleServer: (Boolean) -> Unit,
    onBackClick: () -> Unit
) {
    val portalUrl = remember(ipAddress, port) {
        "http://$ipAddress:$port"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Teacher Web Upload") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                            text = "Teacher Upload Portal",
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

            if (isServerActive) {
                Text(
                    text = "Ask teacher to scan this QR with their camera / browser or visit the link:",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                QrCodeView(
                    content = portalUrl,
                    modifier = Modifier.size(200.dp)
                )

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
                                text = "If using mobile hotspot, teacher can also try: http://192.168.43.1:8080",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "How it works for teachers (Zero App Installation):",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "1. Teacher turns on Wi-Fi and connects to this device's hotspot (or the same classroom Wi-Fi).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "2. Teacher scans the QR code or opens Chrome / Safari to the URL above.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "3. Teacher uploads slides, documents, PDFs, or assignments directly from their phone or laptop.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "4. Your CAMS-MESH app instantly seeds the files to every student across the classroom mesh!",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
