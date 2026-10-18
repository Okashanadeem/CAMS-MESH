package com.cams.mesh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cams.mesh.data.local.entity.SessionEntity
import com.cams.mesh.ui.screens.*
import com.cams.mesh.ui.theme.CamsMeshTheme
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity() {

    private val app by lazy { application as CamsMeshApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CamsMeshTheme {
                val navController = rememberNavController()

                val networkStatus by app.nearbyMeshManager.networkStatus.collectAsState()
                val materials by app.materialRepository.getAllMaterialsFlow().collectAsState(initial = emptyList())
                val activeSession by app.nearbyMeshManager.activeSession.collectAsState()
                val joinedClassrooms by app.database.sessionDao().getAllSessionsFlow().collectAsState(initial = emptyList())

                var displayName by remember { mutableStateOf(app.nearbyMeshManager.localDisplayName) }

                // Local Web Server for Teacher Upload
                var isWebServerActive by remember { mutableStateOf(false) }
                var webServerIp by remember { mutableStateOf("127.0.0.1") }
                val webServerPort = 8080

                val localWebServer = remember {
                    com.cams.mesh.util.LocalWebServer(
                        context = this@MainActivity,
                        materialRepository = app.materialRepository,
                        onMaterialUploaded = { uploadedMaterial ->
                            app.nearbyMeshManager.broadcastNewMaterial(uploadedMaterial)
                            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Teacher uploaded: ${uploadedMaterial.title}", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }

                DisposableEffect(Unit) {
                    onDispose {
                        localWebServer.stop()
                    }
                }

                // Request Permissions Launcher
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { perms ->
                    val allGranted = perms.values.all { it }
                    if (!allGranted) {
                        Toast.makeText(this, "Permissions needed for nearby mesh sharing", Toast.LENGTH_SHORT).show()
                    }
                }

                LaunchedEffect(Unit) {
                    val permissionsNeeded = mutableListOf<String>()
                    // Location is always required by Nearby Connections for discovering and connecting peers
                    permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
                        permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                        permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionsNeeded.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    }
                    val missing = permissionsNeeded.filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) {
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                }

                var currentRoute by remember { mutableStateOf("classroom") }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentRoute == "classroom",
                                onClick = {
                                    currentRoute = "classroom"
                                    navController.navigate("classroom") {
                                        popUpTo("classroom") { inclusive = true }
                                    }
                                },
                                icon = { Icon(Icons.Default.Class, contentDescription = null) },
                                label = { Text("Classroom") }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "history",
                                onClick = {
                                    currentRoute = "history"
                                    navController.navigate("history")
                                },
                                icon = { Icon(Icons.Default.History, contentDescription = null) },
                                label = { Text("History") }
                            )
                            NavigationBarItem(
                                selected = currentRoute == "settings",
                                onClick = {
                                    currentRoute = "settings"
                                    navController.navigate("settings")
                                },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("Settings") }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "classroom",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        composable("classroom") {
                            ClassroomScreen(
                                activeSession = activeSession,
                                joinedClassrooms = joinedClassrooms,
                                networkStatus = networkStatus,
                                recentMaterials = materials.take(10),
                                onCreateClassroomClick = { navController.navigate("create_classroom") },
                                onJoinClassroomClick = { navController.navigate("join_classroom") },
                                onSwitchClassroomClick = { session ->
                                    app.nearbyMeshManager.stopAllNetworking()
                                    app.nearbyMeshManager.startDiscoverySession(session.roomCode)
                                    Toast.makeText(this@MainActivity, "Connecting to ${session.sessionName.ifEmpty { session.roomCode }}...", Toast.LENGTH_SHORT).show()
                                },
                                onChatClick = { navController.navigate("chat") },
                                onTeacherWebUploadClick = {
                                    if (!isWebServerActive) {
                                        val port = localWebServer.start(webServerPort)
                                        if (port > 0) {
                                            isWebServerActive = true
                                            webServerIp = localWebServer.getLocalIpAddress()
                                        }
                                    } else {
                                        webServerIp = localWebServer.getLocalIpAddress()
                                    }
                                    navController.navigate("teacher_web_portal")
                                },
                                onPasteTextClick = { navController.navigate("paste_text") },
                                onUploadFileSelected = { uri ->
                                    lifecycleScope.launch {
                                        try {
                                            val material = app.materialRepository.createFileMaterial(
                                                uri = uri,
                                                title = "",
                                                sessionId = activeSession?.sessionId,
                                                localDeviceId = app.nearbyMeshManager.localDeviceId,
                                                localDisplayName = app.nearbyMeshManager.localDisplayName
                                            )
                                            app.nearbyMeshManager.broadcastNewMaterial(material)
                                            Toast.makeText(this@MainActivity, "File shared successfully", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(this@MainActivity, "Error sharing file: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onMaterialClick = { id -> navController.navigate("detail/$id") },
                                onLeaveClassroomClick = {
                                    app.nearbyMeshManager.stopAllNetworking()
                                    Toast.makeText(this@MainActivity, "Left classroom session", Toast.LENGTH_SHORT).show()
                                },
                                onDeleteSession = { sessionToDelete ->
                                    lifecycleScope.launch {
                                        if (activeSession?.sessionId == sessionToDelete.sessionId) {
                                            app.nearbyMeshManager.stopAllNetworking()
                                        }
                                        app.database.sessionDao().deleteSession(sessionToDelete.sessionId)
                                        Toast.makeText(this@MainActivity, "Removed ${sessionToDelete.sessionName.ifEmpty { sessionToDelete.roomCode }}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        composable("create_classroom") {
                            CreateClassroomScreen(
                                onBackClick = { navController.popBackStack() },
                                onCreateConfirm = { name, approvalMode ->
                                    val code = generateRoomCode()
                                    val session = SessionEntity(
                                        sessionId = UUID.randomUUID().toString(),
                                        roomCode = code,
                                        sessionName = name,
                                        creatorDeviceId = app.nearbyMeshManager.localDeviceId,
                                        createdAt = System.currentTimeMillis(),
                                        sessionState = "ACTIVE",
                                        approvalMode = approvalMode,
                                        lastSeenAt = System.currentTimeMillis()
                                    )
                                    lifecycleScope.launch {
                                        app.database.sessionDao().insertSession(session)
                                    }
                                    app.nearbyMeshManager.startAdvertisingSession(session)
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("join_classroom") {
                            JoinClassroomScreen(
                                onBackClick = { navController.popBackStack() },
                                onJoinWithCode = { code ->
                                    app.nearbyMeshManager.startDiscoverySession(code)
                                    navController.popBackStack()
                                },
                                onScanQrClick = {
                                    navController.navigate("scan_qr")
                                }
                            )
                        }

                        composable("scan_qr") {
                            QrScanScreen(
                                onBackClick = { navController.popBackStack() },
                                onQrCodeScanned = { code ->
                                    app.nearbyMeshManager.startDiscoverySession(code)
                                    navController.navigate("classroom") {
                                        popUpTo("classroom") { inclusive = true }
                                    }
                                    Toast.makeText(this@MainActivity, "Joining room $code from QR...", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        composable("teacher_web_portal") {
                            TeacherWebPortalScreen(
                                ipAddress = webServerIp,
                                port = webServerPort,
                                isServerActive = isWebServerActive,
                                onToggleServer = { active ->
                                    if (active) {
                                        val port = localWebServer.start(webServerPort)
                                        if (port > 0) {
                                            isWebServerActive = true
                                            webServerIp = localWebServer.getLocalIpAddress()
                                        }
                                    } else {
                                        localWebServer.stop()
                                        isWebServerActive = false
                                    }
                                },
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable("chat") {
                            val session = activeSession
                            val chatMessages by if (session != null) {
                                app.database.chatDao().getMessagesForSessionFlow(session.sessionId).collectAsState(initial = emptyList())
                            } else {
                                remember { mutableStateOf(emptyList()) }
                            }

                            ClassroomChatScreen(
                                roomName = session?.sessionName ?: "",
                                roomCode = session?.roomCode ?: "",
                                messages = chatMessages,
                                onBackClick = { navController.popBackStack() },
                                onSendMessage = { text ->
                                    app.nearbyMeshManager.sendChatMessage(text)
                                }
                            )
                        }

                        composable("paste_text") {
                            PasteTextScreen(
                                onBackClick = { navController.popBackStack() },
                                onShareText = { title, content ->
                                    lifecycleScope.launch {
                                        val material = app.materialRepository.createTextMaterial(
                                            title = title,
                                            content = content,
                                            sessionId = activeSession?.sessionId,
                                            localDeviceId = app.nearbyMeshManager.localDeviceId,
                                            localDisplayName = app.nearbyMeshManager.localDisplayName
                                        )
                                        app.nearbyMeshManager.broadcastNewMaterial(material)
                                        navController.popBackStack()
                                    }
                                }
                            )
                        }

                        composable("detail/{materialId}") { backStackEntry ->
                            val materialId = backStackEntry.arguments?.getString("materialId") ?: ""
                            val material = materials.find { it.materialId == materialId }
                            MaterialDetailScreen(
                                material = material,
                                onBackClick = { navController.popBackStack() },
                                onDeleteLocalCopy = { id ->
                                    lifecycleScope.launch {
                                        app.materialRepository.deleteLocalCopy(id, app.nearbyMeshManager.localDeviceId)
                                        navController.popBackStack()
                                    }
                                }
                            )
                        }

                        composable("history") {
                            HistoryScreen(
                                materials = materials,
                                onMaterialClick = { id -> navController.navigate("detail/$id") }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                displayName = displayName,
                                onDisplayNameChange = {
                                    displayName = it
                                    app.nearbyMeshManager.localDisplayName = it
                                },
                                onClearAllData = {
                                    lifecycleScope.launch {
                                        app.nearbyMeshManager.stopAllNetworking()
                                        app.materialRepository.clearAllData()
                                        Toast.makeText(this@MainActivity, "All local data cleared successfully", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Excludes 0, O, 1, I to avoid confusion
        return (1..6).map { chars.random() }.joinToString("")
    }
}
