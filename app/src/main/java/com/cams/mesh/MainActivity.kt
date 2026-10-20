package com.cams.mesh

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cams.mesh.data.local.entity.SessionEntity
import com.cams.mesh.ui.screens.*
import com.cams.mesh.ui.theme.CamsMeshTheme
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity() {

    private val app by lazy { application as CamsMeshApp }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        // Location permissions required for Nearby Connections
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return permissions
    }

    private fun hasAllPermissions(): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getMissingPermissions(): List<String> {
        return getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CamsMeshTheme {
                val navController = rememberNavController()

                val networkStatus by app.nearbyMeshManager.networkStatus.collectAsState()
                val nearbyPublicRooms by app.nearbyMeshManager.nearbyPublicRooms.collectAsState()
                val materials by app.materialRepository.getAllMaterialsFlow().collectAsState(initial = emptyList())
                val externalMaterials by app.materialRepository.getExternalMaterialsFlow().collectAsState(initial = emptyList())
                val activeSession by app.nearbyMeshManager.activeSession.collectAsState()
                val joinedClassrooms by app.database.sessionDao().getAllSessionsFlow().collectAsState(initial = emptyList())

                var displayName by remember { mutableStateOf(app.nearbyMeshManager.localDisplayName) }
                var showChangeNameDialog by remember {
                    mutableStateOf(displayName.trim().equals("Student", ignoreCase = true))
                }

                // Local Web Server for External User Upload
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
                                Toast.makeText(this@MainActivity, "External user uploaded: ${uploadedMaterial.title}", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }

                DisposableEffect(Unit) {
                    onDispose {
                        localWebServer.stop()
                    }
                }

                // Permission State & Rationales
                var showPermissionDialog by remember { mutableStateOf(false) }
                var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { perms ->
                    val allGranted = perms.values.all { it }
                    if (allGranted) {
                        pendingAction?.invoke()
                        pendingAction = null
                    } else {
                        Toast.makeText(this@MainActivity, "Permissions are required to connect with nearby peers", Toast.LENGTH_LONG).show()
                        showPermissionDialog = true
                    }
                }

                fun executeWithPermissionCheck(action: () -> Unit) {
                    val missing = getMissingPermissions()
                    if (missing.isEmpty()) {
                        action()
                    } else {
                        pendingAction = action
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                }

                LaunchedEffect(Unit) {
                    val missing = getMissingPermissions()
                    if (missing.isNotEmpty()) {
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                }

                if (showPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = { showPermissionDialog = false },
                        icon = { Icon(Icons.Default.LocationOff, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        title = { Text("Permissions Required") },
                        text = {
                            Text("CAMS-MESH uses Location and Bluetooth / Nearby Devices to discover and share study materials without internet. Please enable them in App Settings.")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showPermissionDialog = false
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", packageName, null)
                                    }
                                    startActivity(intent)
                                }
                            ) {
                                Text("Open Settings")
                            }
                        },
                        dismissButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        showPermissionDialog = false
                                        com.cams.mesh.ui.components.ErrorReporter.reportIssue(
                                            this@MainActivity,
                                            "Permission Error: Missing permissions for Nearby Mesh: ${getMissingPermissions().joinToString(", ")}"
                                        )
                                    }
                                ) {
                                    Text("Report Issue", color = MaterialTheme.colorScheme.error)
                                }
                                OutlinedButton(onClick = { showPermissionDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    )
                }

                if (showChangeNameDialog) {
                    AlertDialog(
                        onDismissRequest = { showChangeNameDialog = false },
                        icon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        title = { Text("Update Your Name") },
                        text = {
                            Text("Your display name is currently set to 'Student'. Please update your name so other users in the room can recognize your messages and uploads.")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showChangeNameDialog = false
                                    currentRoute = "settings"
                                    navController.navigate("settings?focus=true")
                                }
                            ) {
                                Text("Open Settings")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showChangeNameDialog = false }) {
                                Text("Later")
                            }
                        }
                    )
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
                                onCreateClassroomClick = {
                                    executeWithPermissionCheck {
                                        navController.navigate("create_classroom")
                                    }
                                },
                                onJoinClassroomClick = {
                                    executeWithPermissionCheck {
                                        navController.navigate("join_classroom")
                                    }
                                },
                                onSwitchClassroomClick = { session ->
                                    executeWithPermissionCheck {
                                        app.nearbyMeshManager.stopAllNetworking()
                                        app.nearbyMeshManager.startDiscoverySession(session.roomCode, session.sessionName)
                                        Toast.makeText(this@MainActivity, "Connecting to ${session.sessionName.ifEmpty { session.roomCode }}...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onOpenClassroomClick = { session ->
                                    executeWithPermissionCheck {
                                        if (activeSession?.sessionId != session.sessionId) {
                                            app.nearbyMeshManager.stopAllNetworking()
                                            app.nearbyMeshManager.startDiscoverySession(session.roomCode, session.sessionName)
                                        }
                                        navController.navigate("chat")
                                    }
                                },
                                onExternalWebUploadClick = {
                                    if (!isWebServerActive) {
                                        val port = localWebServer.start(webServerPort)
                                        if (port > 0) {
                                            isWebServerActive = true
                                            webServerIp = localWebServer.getLocalIpAddress()
                                        }
                                    } else {
                                        webServerIp = localWebServer.getLocalIpAddress()
                                    }
                                    navController.navigate("external_web_portal")
                                },
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
                                onCreateConfirm = { name, isVisibleToAll ->
                                    val code = generateRoomCode()
                                    val session = SessionEntity(
                                        sessionId = UUID.randomUUID().toString(),
                                        roomCode = code,
                                        sessionName = name,
                                        creatorDeviceId = app.nearbyMeshManager.localDeviceId,
                                        createdAt = System.currentTimeMillis(),
                                        sessionState = "ACTIVE",
                                        isVisibleToAll = isVisibleToAll,
                                        approvalMode = false,
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
                            DisposableEffect(Unit) {
                                app.nearbyMeshManager.startPublicRoomDiscovery()
                                onDispose {
                                    app.nearbyMeshManager.stopPublicRoomDiscovery()
                                }
                            }

                            JoinClassroomScreen(
                                discoveredRooms = nearbyPublicRooms,
                                onBackClick = { navController.popBackStack() },
                                onJoinWithCode = { code ->
                                    app.nearbyMeshManager.startDiscoverySession(code)
                                    navController.popBackStack()
                                },
                                onJoinDiscoveredRoom = { room ->
                                    app.nearbyMeshManager.connectToDiscoveredRoom(room)
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
                                    executeWithPermissionCheck {
                                        app.nearbyMeshManager.startDiscoverySession(code)
                                        navController.navigate("classroom") {
                                            popUpTo("classroom") { inclusive = true }
                                        }
                                        Toast.makeText(this@MainActivity, "Joining room $code from QR...", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        composable("external_web_portal") {
                            TeacherWebPortalScreen(
                                ipAddress = webServerIp,
                                port = webServerPort,
                                isServerActive = isWebServerActive,
                                externalMaterials = externalMaterials,
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
                                onMaterialClick = { id -> navController.navigate("detail/$id") },
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable("chat") {
                            val session = activeSession
                            if (session == null) {
                                LaunchedEffect(Unit) {
                                    Toast.makeText(this@MainActivity, "Please join or switch to an active classroom to chat", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                }
                            } else {
                                val chatMessages by app.database.chatDao().getMessagesForSessionFlow(session.sessionId).collectAsState(initial = emptyList())

                                ClassroomChatScreen(
                                    roomName = session.sessionName,
                                    roomCode = session.roomCode,
                                    messages = chatMessages,
                                    onBackClick = { navController.popBackStack() },
                                    onSendMessage = { text ->
                                        app.nearbyMeshManager.sendChatMessage(text)
                                    },
                                    onUploadFileClick = { uri ->
                                        lifecycleScope.launch {
                                            try {
                                                val material = app.materialRepository.createFileMaterial(
                                                    uri = uri,
                                                    title = "",
                                                    sessionId = session.sessionId,
                                                    localDeviceId = app.nearbyMeshManager.localDeviceId,
                                                    localDisplayName = app.nearbyMeshManager.localDisplayName
                                                )
                                                app.nearbyMeshManager.broadcastNewMaterial(material)
                                                // Send notification in chat
                                                val myName = app.nearbyMeshManager.localDisplayName
                                                app.nearbyMeshManager.sendChatMessage("$myName has uploaded a file please view it in files")
                                                Toast.makeText(this@MainActivity, "File shared to room", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(this@MainActivity, "Error sharing file", Toast.LENGTH_SHORT).show()
                                                com.cams.mesh.ui.components.ErrorReporter.reportIssue(
                                                    this@MainActivity,
                                                    "File Sharing Error in room ${session.roomCode}: ${e.message}"
                                                )
                                            }
                                        }
                                    },
                                    onPasteTextClick = { navController.navigate("paste_text") },
                                    onViewRoomFilesClick = { navController.navigate("room_files/${session.sessionId}") }
                                )
                            }
                        }

                        composable("room_files/{sessionId}") { backStackEntry ->
                            val targetSessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                            val session = joinedClassrooms.find { it.sessionId == targetSessionId } ?: activeSession
                            val roomMaterials by app.materialRepository.getMaterialsBySessionFlow(targetSessionId).collectAsState(initial = emptyList())

                            RoomFilesScreen(
                                roomName = session?.sessionName ?: "Classroom",
                                roomCode = session?.roomCode ?: "",
                                materials = roomMaterials,
                                onMaterialClick = { id -> navController.navigate("detail/$id") },
                                onBackClick = { navController.popBackStack() }
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
                                sessions = joinedClassrooms,
                                onMaterialClick = { id -> navController.navigate("detail/$id") }
                            )
                        }

                        composable(
                            route = "settings?focus={focus}",
                            arguments = listOf(
                                navArgument("focus") {
                                    type = NavType.BoolType
                                    defaultValue = false
                                }
                            )
                        ) { backStackEntry ->
                            val shouldFocus = backStackEntry.arguments?.getBoolean("focus") ?: false
                            SettingsScreen(
                                displayName = displayName,
                                autoFocusName = shouldFocus,
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
