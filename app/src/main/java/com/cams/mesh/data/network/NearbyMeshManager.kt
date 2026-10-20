package com.cams.mesh.data.network

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.cams.mesh.data.local.AppDatabase
import com.cams.mesh.data.local.entity.ChatMessageEntity
import com.cams.mesh.data.local.entity.PeerEntity
import com.cams.mesh.data.local.entity.SessionEntity
import com.cams.mesh.data.network.protocol.*
import com.cams.mesh.data.repository.MaterialRepository
import com.cams.mesh.util.FileStorageManager
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed class NetworkStatus {
    object Idle : NetworkStatus()
    data class Advertising(val roomCode: String) : NetworkStatus()
    data class Discovering(val query: String?) : NetworkStatus()
    data class Connected(val peerCount: Int, val roomCode: String) : NetworkStatus()
    data class Error(val message: String) : NetworkStatus()
}

data class DiscoveredRoom(
    val endpointId: String,
    val roomCode: String,
    val roomName: String,
    val hostDisplayName: String
)

class NearbyMeshManager(
    private val context: Context,
    private val materialRepository: MaterialRepository,
    private val fileStorage: FileStorageManager = FileStorageManager(context)
) {
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val gson = Gson()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val peerDao = AppDatabase.getInstance(context).peerDao()
    private val sessionDao = AppDatabase.getInstance(context).sessionDao()
    private val chatDao = AppDatabase.getInstance(context).chatDao()

    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Idle)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus

    private val _nearbyPublicRooms = MutableStateFlow<List<DiscoveredRoom>>(emptyList())
    val nearbyPublicRooms: StateFlow<List<DiscoveredRoom>> = _nearbyPublicRooms

    // Active connections: endpointId -> Peer info
    private val connectedEndpoints = mutableMapOf<String, PeerInfo>()
    // Tracking incoming file payloads: payloadId -> IncomingTransferInfo
    private val incomingFilePayloads = mutableMapOf<Long, IncomingTransferInfo>()
    // Tracking incoming file metadata: payloadId -> FileTransferMetadataPayload
    private val pendingFileMetadata = mutableMapOf<Long, FileTransferMetadataPayload>()

    private val _activeSession = MutableStateFlow<SessionEntity?>(null)
    val activeSession: StateFlow<SessionEntity?> = _activeSession
    val currentSession: SessionEntity? get() = _activeSession.value

    private val prefs = context.getSharedPreferences("cams_mesh_prefs", Context.MODE_PRIVATE)

    var localDeviceId: String
        get() = prefs.getString("local_device_id", null) ?: run {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString("local_device_id", newId).apply()
            newId
        }
        set(value) {
            prefs.edit().putString("local_device_id", value).apply()
        }

    var localDisplayName: String
        get() = prefs.getString("local_display_name", "Student") ?: "Student"
        set(value) {
            prefs.edit().putString("local_display_name", value).apply()
        }

    data class PeerInfo(
        val endpointId: String,
        var deviceId: String = "",
        var displayName: String = ""
    )

    data class IncomingTransferInfo(
        val materialId: String,
        val originalFileName: String,
        val expectedHash: String,
        val tempFile: File
    )

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val messageJson = String(bytes, Charsets.UTF_8)
                    try {
                        val message = gson.fromJson(messageJson, MeshMessage::class.java)
                        handleIncomingMeshMessage(endpointId, message)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                Payload.Type.FILE -> {
                    val meta = pendingFileMetadata.remove(payload.id)
                    val file = payload.asFile()?.asJavaFile()
                    if (file != null) {
                        val materialId = meta?.materialId ?: "incoming_${payload.id}"
                        val fileName = meta?.originalFileName ?: "file_${payload.id}"
                        val expectedHash = meta?.sha256Hash ?: ""
                        incomingFilePayloads[payload.id] = IncomingTransferInfo(
                            materialId = materialId,
                            originalFileName = fileName,
                            expectedHash = expectedHash,
                            tempFile = file
                        )
                    }
                }
                Payload.Type.STREAM -> {
                    // For streaming transfers
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val transferInfo = incomingFilePayloads.remove(update.payloadId)
                if (transferInfo != null) {
                    coroutineScope.launch {
                        try {
                            val finalFile = if (transferInfo.expectedHash.isNotEmpty()) {
                                fileStorage.finalizeReceivedFile(
                                    transferInfo.tempFile,
                                    transferInfo.materialId,
                                    transferInfo.originalFileName,
                                    transferInfo.expectedHash
                                )
                            } else {
                                val safeName = fileStorage.sanitizeFilename(transferInfo.originalFileName)
                                val target = File(context.filesDir, "materials/${transferInfo.materialId}_$safeName")
                                target.parentFile?.mkdirs()
                                transferInfo.tempFile.copyTo(target, overwrite = true)
                                transferInfo.tempFile.delete()
                                target
                            }
                            materialRepository.updateReceivedFilePath(
                                transferInfo.materialId,
                                finalFile.absolutePath
                            )
                            // Multi-hop relay: Advertise newly verified file to other connected peers!
                            broadcastManifest()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            // Automatically accept connection for offline classroom mesh (or verify room code)
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            connectedEndpoints[endpointId] = PeerInfo(
                endpointId = endpointId,
                displayName = connectionInfo.endpointName
            )
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                val peer = connectedEndpoints[endpointId] ?: return
                coroutineScope.launch {
                    val peerEntity = PeerEntity(
                        deviceId = peer.deviceId.ifEmpty { endpointId },
                        displayName = peer.displayName,
                        sessionId = currentSession?.sessionId,
                        lastSeenAt = System.currentTimeMillis(),
                        connectionState = "CONNECTED",
                        lastSuccessfulSyncAt = null
                    )
                    peerDao.insertOrUpdatePeer(peerEntity)
                    _networkStatus.value = NetworkStatus.Connected(
                        peerCount = connectedEndpoints.size,
                        roomCode = currentSession?.roomCode ?: ""
                    )
                    // Send HELLO & request manifest
                    sendHello(endpointId)
                    requestManifest(endpointId)
                }
            } else {
                connectedEndpoints.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            val peer = connectedEndpoints.remove(endpointId)
            if (peer != null) {
                coroutineScope.launch {
                    peerDao.updatePeerState(peer.deviceId.ifEmpty { endpointId }, "DISCONNECTED")
                }
            }
            if (connectedEndpoints.isEmpty()) {
                val code = currentSession?.roomCode
                if (code != null) {
                    _networkStatus.value = NetworkStatus.Advertising(code)
                } else {
                    _networkStatus.value = NetworkStatus.Idle
                }
            } else {
                _networkStatus.value = NetworkStatus.Connected(
                    peerCount = connectedEndpoints.size,
                    roomCode = currentSession?.roomCode ?: ""
                )
            }
        }
    }

    companion object {
        const val PUBLIC_SERVICE_ID = "com.cams.mesh.public"
        private const val PUBLIC_PREFIX = "CAMS_PUB"
    }

    fun startAdvertisingSession(session: SessionEntity) {
        // Stop any active advertising/discovery/endpoints before advertising a new room
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()

        _activeSession.value = session
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER) // PRD: P2P Cluster mesh topology
            .build()

        // If public/visible to all, advertise on public service ID with room metadata in endpoint name
        // Format: CAMS_PUB:<roomCode>:<roomName>:<displayName>
        // Otherwise, advertise purely on private roomCode service ID
        val serviceId: String
        val advertisingName: String

        if (session.isVisibleToAll) {
            serviceId = PUBLIC_SERVICE_ID
            val safeName = session.sessionName.ifEmpty { "Room" }.replace(":", " ")
            val safeHost = localDisplayName.replace(":", " ")
            advertisingName = "$PUBLIC_PREFIX:${session.roomCode}:$safeName:$safeHost"
        } else {
            serviceId = "com.cams.mesh.${session.roomCode}"
            advertisingName = localDisplayName
        }

        connectionsClient.startAdvertising(
            advertisingName,
            serviceId,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            _networkStatus.value = NetworkStatus.Advertising(session.roomCode)
        }.addOnFailureListener { e ->
            val msg = if (e.message?.contains("8034") == true || e.message?.contains("MISSING_PERMISSION") == true) {
                "Location or Nearby Devices permission missing. Please grant permissions."
            } else if (e.message?.contains("8001") == true || e.message?.contains("STATUS_ALREADY_ADVERTISING") == true) {
                "Already advertising room ${session.roomCode}"
            } else {
                e.message ?: "Advertising failed"
            }
            if (e.message?.contains("8001") == true || e.message?.contains("STATUS_ALREADY_ADVERTISING") == true) {
                _networkStatus.value = NetworkStatus.Advertising(session.roomCode)
            } else {
                _networkStatus.value = NetworkStatus.Error(msg)
            }
        }
    }

    fun startPublicRoomDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                val endpointName = info.endpointName
                if (endpointName.startsWith("$PUBLIC_PREFIX:")) {
                    val parts = endpointName.split(":")
                    if (parts.size >= 4) {
                        val roomCode = parts[1]
                        val roomName = parts[2]
                        val hostName = parts[3]
                        val room = DiscoveredRoom(
                            endpointId = endpointId,
                            roomCode = roomCode,
                            roomName = roomName,
                            hostDisplayName = hostName
                        )
                        _nearbyPublicRooms.value = _nearbyPublicRooms.value
                            .filter { it.endpointId != endpointId && it.roomCode != roomCode } + room
                    }
                }
            }

            override fun onEndpointLost(endpointId: String) {
                _nearbyPublicRooms.value = _nearbyPublicRooms.value.filter { it.endpointId != endpointId }
            }
        }

        connectionsClient.startDiscovery(
            PUBLIC_SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnFailureListener { e ->
            // If discovery fails, clear list
            _nearbyPublicRooms.value = emptyList()
        }
    }

    fun stopPublicRoomDiscovery() {
        connectionsClient.stopDiscovery()
        _nearbyPublicRooms.value = emptyList()
    }

    fun startDiscoverySession(roomCode: String, customSessionName: String? = null) {
        // First ensure any public scanning stops
        connectionsClient.stopDiscovery()

        coroutineScope.launch {
            val existing = sessionDao.getSessionByRoomCode(roomCode)
            val session = existing ?: SessionEntity(
                sessionId = UUID.randomUUID().toString(),
                roomCode = roomCode,
                sessionName = customSessionName ?: "Room $roomCode",
                creatorDeviceId = "remote",
                createdAt = System.currentTimeMillis(),
                sessionState = "ACTIVE",
                isVisibleToAll = true,
                approvalMode = false,
                lastSeenAt = System.currentTimeMillis()
            )
            sessionDao.insertSession(session)
            _activeSession.value = session
        }

        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                connectionsClient.requestConnection(
                    localDisplayName,
                    endpointId,
                    connectionLifecycleCallback
                )
            }

            override fun onEndpointLost(endpointId: String) {}
        }

        // Simultaneously discover on both private room channel and public channel
        connectionsClient.startDiscovery(
            "com.cams.mesh.$roomCode",
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            _networkStatus.value = NetworkStatus.Discovering(roomCode)
        }.addOnFailureListener {
            connectionsClient.startDiscovery(
                PUBLIC_SERVICE_ID,
                object : EndpointDiscoveryCallback() {
                    override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                        if (info.endpointName.contains(roomCode)) {
                            connectionsClient.requestConnection(
                                localDisplayName,
                                endpointId,
                                connectionLifecycleCallback
                            )
                        }
                    }

                    override fun onEndpointLost(endpointId: String) {}
                },
                options
            ).addOnSuccessListener {
                _networkStatus.value = NetworkStatus.Discovering(roomCode)
            }.addOnFailureListener { err ->
                val msg = if (err.message?.contains("8034") == true || err.message?.contains("MISSING_PERMISSION") == true) {
                    "Location or Nearby Devices permission missing. Please grant permissions."
                } else {
                    err.message ?: "Discovery failed"
                }
                _networkStatus.value = NetworkStatus.Error(msg)
            }
        }

        // Also advertise alongside discovery so if two peers switch into the same room simultaneously,
        // they can find each other symmetrically!
        val advOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()
        connectionsClient.startAdvertising(
            "$PUBLIC_PREFIX:$roomCode:${customSessionName ?: "Room"}:$localDisplayName",
            PUBLIC_SERVICE_ID,
            connectionLifecycleCallback,
            advOptions
        )
    }

    fun connectToDiscoveredRoom(room: DiscoveredRoom) {
        connectionsClient.stopDiscovery()
        coroutineScope.launch {
            val existing = sessionDao.getSessionByRoomCode(room.roomCode)
            val session = existing ?: SessionEntity(
                sessionId = UUID.randomUUID().toString(),
                roomCode = room.roomCode,
                sessionName = room.roomName.ifEmpty { "Room ${room.roomCode}" },
                creatorDeviceId = "remote",
                createdAt = System.currentTimeMillis(),
                sessionState = "ACTIVE",
                isVisibleToAll = true,
                approvalMode = false,
                lastSeenAt = System.currentTimeMillis()
            )
            sessionDao.insertSession(session)
            _activeSession.value = session
        }

        _networkStatus.value = NetworkStatus.Discovering(room.roomCode)
        connectionsClient.requestConnection(
            localDisplayName,
            room.endpointId,
            connectionLifecycleCallback
        ).addOnFailureListener { e ->
            // If direct endpoint connection failed (e.g. lost/stale), fall back to discovering that room code
            startDiscoverySession(room.roomCode, room.roomName)
        }
    }

    fun stopAllNetworking() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        incomingFilePayloads.clear()
        _nearbyPublicRooms.value = emptyList()
        _activeSession.value = null
        _networkStatus.value = NetworkStatus.Idle
    }

    private fun handleIncomingMeshMessage(fromEndpointId: String, message: MeshMessage) {
        val peer = connectedEndpoints[fromEndpointId]
        if (peer != null) {
            peer.deviceId = message.senderDeviceId
            peer.displayName = message.senderDisplayName
        }

        coroutineScope.launch {
            when (message.type) {
                "HELLO" -> {
                    // Send active session info if we have one
                    val session = currentSession
                    if (session != null) {
                        val sessionInfo = SessionInfoPayload(
                            sessionId = session.sessionId,
                            roomCode = session.roomCode,
                            sessionName = session.sessionName,
                            creatorDeviceId = session.creatorDeviceId,
                            isVisibleToAll = session.isVisibleToAll,
                            approvalMode = session.approvalMode
                        )
                        val msg = MeshMessage(
                            type = "SESSION_INFO",
                            sessionId = session.sessionId,
                            senderDeviceId = localDeviceId,
                            senderDisplayName = localDisplayName,
                            payloadJson = gson.toJson(sessionInfo)
                        )
                        sendBytes(fromEndpointId, gson.toJson(msg))
                    }
                    // Reply with our manifest to initiate bidirectional sync
                    sendManifest(fromEndpointId)
                }
                "SESSION_INFO" -> {
                    val info = gson.fromJson(message.payloadJson, SessionInfoPayload::class.java)
                    if (info != null) {
                        // If we had a temporary or placeholder session for this roomCode, remove it so we don't have duplicate sessions
                        val current = _activeSession.value
                        if (current != null && current.roomCode == info.roomCode && current.sessionId != info.sessionId) {
                            sessionDao.deleteSession(current.sessionId)
                        }

                        val existing = sessionDao.getSessionById(info.sessionId)
                        val updated = SessionEntity(
                            sessionId = info.sessionId,
                            roomCode = info.roomCode,
                            sessionName = info.sessionName,
                            creatorDeviceId = info.creatorDeviceId,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                            sessionState = "ACTIVE",
                            isVisibleToAll = info.isVisibleToAll,
                            approvalMode = info.approvalMode,
                            lastSeenAt = System.currentTimeMillis()
                        )
                        sessionDao.insertSession(updated)
                        _activeSession.value = updated
                    }
                }
                "FILE_METADATA" -> {
                    val meta = gson.fromJson(message.payloadJson, FileTransferMetadataPayload::class.java)
                    if (meta != null) {
                        // Store pending metadata mapped by materialId or payloadId
                        pendingFileMetadata[meta.payloadId] = meta
                    }
                }
                "CHAT" -> {
                    val chat = gson.fromJson(message.payloadJson, ChatPayload::class.java)
                    if (chat != null) {
                        val entity = ChatMessageEntity(
                            messageId = chat.messageId,
                            sessionId = chat.sessionId,
                            senderDeviceId = chat.senderDeviceId,
                            senderDisplayName = chat.senderDisplayName,
                            text = chat.text,
                            timestamp = chat.timestamp,
                            isFromMe = chat.senderDeviceId == localDeviceId
                        )
                        chatDao.insertMessage(entity)
                        // Relay chat to other connected peers in mesh
                        broadcastChatMessage(chat, excludeEndpointId = fromEndpointId)
                    }
                }
                "MANIFEST_REQUEST" -> {
                    sendManifest(fromEndpointId)
                }
                "MANIFEST_RESPONSE" -> {
                    val manifest = gson.fromJson(message.payloadJson, ManifestPayload::class.java)
                    compareManifestAndRequestMissing(fromEndpointId, manifest)
                }
                "SYNC_RECORD" -> {
                    val record = gson.fromJson(message.payloadJson, SyncRecordPayload::class.java)
                    if (record.contentType == "TEXT" && record.textContent != null) {
                        materialRepository.receiveTextMaterial(
                            materialId = record.materialId,
                            title = record.title,
                            content = record.textContent,
                            sessionId = message.sessionId,
                            uploaderId = record.uploaderDeviceId,
                            uploaderName = record.uploaderDisplayName,
                            createdAt = record.createdAt,
                            hash = record.sha256Hash
                        )
                        // Post notification in room chat that text was shared
                        val uploaderName = if (record.uploaderDeviceId == "external_web" || record.uploaderDeviceId == "teacher_web") {
                            "External User"
                        } else {
                            record.uploaderDisplayName
                        }
                        val chatEntity = ChatMessageEntity(
                            messageId = UUID.randomUUID().toString(),
                            sessionId = message.sessionId,
                            senderDeviceId = record.uploaderDeviceId,
                            senderDisplayName = uploaderName,
                            text = "$uploaderName has shared a text note please view it in files",
                            timestamp = record.createdAt,
                            isFromMe = false
                        )
                        chatDao.insertMessage(chatEntity)
                        // Relay text material to other peers who don't have it!
                        broadcastSyncRecord(record, excludeEndpointId = fromEndpointId)
                    } else if (record.contentType == "FILE") {
                        materialRepository.receiveFileRecord(
                            materialId = record.materialId,
                            title = record.title,
                            originalFileName = record.originalFileName ?: "file",
                            mimeType = record.mimeType,
                            fileSizeBytes = record.fileSizeBytes,
                            sha256Hash = record.sha256Hash,
                            sessionId = message.sessionId,
                            uploaderId = record.uploaderDeviceId,
                            uploaderName = record.uploaderDisplayName,
                            createdAt = record.createdAt
                        )
                        // Post notification in room chat that file was uploaded and can be viewed in files
                        val uploaderName = if (record.uploaderDeviceId == "external_web" || record.uploaderDeviceId == "teacher_web") {
                            "External User"
                        } else {
                            record.uploaderDisplayName
                        }
                        val chatEntity = ChatMessageEntity(
                            messageId = UUID.randomUUID().toString(),
                            sessionId = message.sessionId,
                            senderDeviceId = record.uploaderDeviceId,
                            senderDisplayName = uploaderName,
                            text = "$uploaderName has uploaded a file please view it in files",
                            timestamp = record.createdAt,
                            isFromMe = false
                        )
                        chatDao.insertMessage(chatEntity)
                        // Auto-request the actual file content from the peer who sent the record
                        sendFilePayloadRequest(fromEndpointId, record.materialId)
                    }
                }
                "REQUEST_ITEM" -> {
                    val req = gson.fromJson(message.payloadJson, ItemRequestPayload::class.java)
                    sendFilePayloadToPeer(fromEndpointId, req.materialId)
                }
                "TOMBSTONE" -> {
                    val tombstone = gson.fromJson(message.payloadJson, TombstonePayload::class.java)
                    materialRepository.deleteLocalCopy(tombstone.materialId, tombstone.deletedByDeviceId)
                }
            }
        }
    }

    fun sendChatMessage(text: String) {
        val session = currentSession ?: return
        val chatPayload = ChatPayload(
            messageId = UUID.randomUUID().toString(),
            sessionId = session.sessionId,
            text = text,
            senderDeviceId = localDeviceId,
            senderDisplayName = localDisplayName,
            timestamp = System.currentTimeMillis()
        )
        coroutineScope.launch {
            val entity = ChatMessageEntity(
                messageId = chatPayload.messageId,
                sessionId = chatPayload.sessionId,
                senderDeviceId = chatPayload.senderDeviceId,
                senderDisplayName = chatPayload.senderDisplayName,
                text = chatPayload.text,
                timestamp = chatPayload.timestamp,
                isFromMe = true
            )
            chatDao.insertMessage(entity)
            broadcastChatMessage(chatPayload)
        }
    }

    private suspend fun broadcastChatMessage(chat: ChatPayload, excludeEndpointId: String? = null) {
        val msg = MeshMessage(
            type = "CHAT",
            sessionId = chat.sessionId,
            senderDeviceId = chat.senderDeviceId,
            senderDisplayName = chat.senderDisplayName,
            payloadJson = gson.toJson(chat)
        )
        val json = gson.toJson(msg)
        connectedEndpoints.keys.filter { it != excludeEndpointId }.forEach { endpointId ->
            sendBytes(endpointId, json)
        }
    }

    private suspend fun sendHello(endpointId: String) {
        val session = currentSession
        if (session != null) {
            val sessionInfo = SessionInfoPayload(
                sessionId = session.sessionId,
                roomCode = session.roomCode,
                sessionName = session.sessionName,
                creatorDeviceId = session.creatorDeviceId,
                isVisibleToAll = session.isVisibleToAll,
                approvalMode = session.approvalMode
            )
            val infoMsg = MeshMessage(
                type = "SESSION_INFO",
                sessionId = session.sessionId,
                senderDeviceId = localDeviceId,
                senderDisplayName = localDisplayName,
                payloadJson = gson.toJson(sessionInfo)
            )
            sendBytes(endpointId, gson.toJson(infoMsg))
        }

        val msg = MeshMessage(
            type = "HELLO",
            sessionId = currentSession?.sessionId ?: "",
            senderDeviceId = localDeviceId,
            senderDisplayName = localDisplayName
        )
        sendBytes(endpointId, gson.toJson(msg))
    }

    private suspend fun requestManifest(endpointId: String) {
        val msg = MeshMessage(
            type = "MANIFEST_REQUEST",
            sessionId = currentSession?.sessionId ?: "",
            senderDeviceId = localDeviceId,
            senderDisplayName = localDisplayName
        )
        sendBytes(endpointId, gson.toJson(msg))
    }

    private suspend fun sendManifest(endpointId: String) {
        val materials = materialRepository.getAllActiveMaterials()
        val manifestItems = materials.map {
            ManifestItem(
                materialId = it.materialId,
                sha256Hash = it.sha256Hash,
                createdAt = it.createdAt,
                contentType = it.contentType,
                title = it.title,
                fileSizeBytes = it.fileSizeBytes
            )
        }
        val payload = ManifestPayload(manifestItems)
        val msg = MeshMessage(
            type = "MANIFEST_RESPONSE",
            sessionId = currentSession?.sessionId ?: "",
            senderDeviceId = localDeviceId,
            senderDisplayName = localDisplayName,
            payloadJson = gson.toJson(payload)
        )
        sendBytes(endpointId, gson.toJson(msg))
    }

    fun broadcastManifest() {
        coroutineScope.launch {
            connectedEndpoints.keys.forEach { endpointId ->
                sendManifest(endpointId)
            }
        }
    }

    fun sendFilePayloadRequest(endpointId: String? = null, materialId: String) {
        val reqMsg = MeshMessage(
            type = "REQUEST_ITEM",
            sessionId = currentSession?.sessionId ?: "",
            senderDeviceId = localDeviceId,
            senderDisplayName = localDisplayName,
            payloadJson = gson.toJson(ItemRequestPayload(materialId))
        )
        val json = gson.toJson(reqMsg)
        if (endpointId != null) {
            sendBytes(endpointId, json)
        } else {
            connectedEndpoints.keys.forEach { peerEndpointId ->
                sendBytes(peerEndpointId, json)
            }
        }
    }

    private suspend fun compareManifestAndRequestMissing(fromEndpointId: String, remoteManifest: ManifestPayload) {
        val localMaterials = materialRepository.getAllActiveMaterials()
        val localIds = localMaterials.map { it.materialId }.toSet()

        remoteManifest.items.forEach { remoteItem ->
            if (!localIds.contains(remoteItem.materialId)) {
                // We are missing this item! Request its metadata or send an item request
                val reqMsg = MeshMessage(
                    type = "REQUEST_ITEM",
                    sessionId = currentSession?.sessionId ?: "",
                    senderDeviceId = localDeviceId,
                    senderDisplayName = localDisplayName,
                    payloadJson = gson.toJson(ItemRequestPayload(remoteItem.materialId))
                )
                sendBytes(fromEndpointId, gson.toJson(reqMsg))
            }
        }
    }

    fun broadcastNewMaterial(material: com.cams.mesh.data.local.entity.MaterialEntity) {
        coroutineScope.launch {
            val record = SyncRecordPayload(
                materialId = material.materialId,
                sessionId = material.sessionId,
                title = material.title,
                contentType = material.contentType,
                mimeType = material.mimeType,
                originalFileName = material.originalFileName,
                textContent = material.textContent,
                fileSizeBytes = material.fileSizeBytes,
                sha256Hash = material.sha256Hash,
                uploaderDeviceId = material.uploaderDeviceId,
                uploaderDisplayName = material.uploaderDisplayName,
                createdAt = material.createdAt
            )
            broadcastSyncRecord(record)
        }
    }

    private suspend fun broadcastSyncRecord(record: SyncRecordPayload, excludeEndpointId: String? = null) {
        val msg = MeshMessage(
            type = "SYNC_RECORD",
            sessionId = currentSession?.sessionId ?: "",
            senderDeviceId = localDeviceId,
            senderDisplayName = localDisplayName,
            payloadJson = gson.toJson(record)
        )
        val json = gson.toJson(msg)
        connectedEndpoints.keys.filter { it != excludeEndpointId }.forEach { endpointId ->
            sendBytes(endpointId, json)
        }
    }

    private suspend fun sendFilePayloadToPeer(endpointId: String, materialId: String) {
        val material = materialRepository.getMaterialById(materialId) ?: return
        if (material.contentType == "TEXT") {
            // Text material can be transmitted directly as a SyncRecord
            val record = SyncRecordPayload(
                materialId = material.materialId,
                sessionId = material.sessionId,
                title = material.title,
                contentType = "TEXT",
                mimeType = "text/plain",
                originalFileName = null,
                textContent = material.textContent,
                fileSizeBytes = material.fileSizeBytes,
                sha256Hash = material.sha256Hash,
                uploaderDeviceId = material.uploaderDeviceId,
                uploaderDisplayName = material.uploaderDisplayName,
                createdAt = material.createdAt
            )
            val msg = MeshMessage(
                type = "SYNC_RECORD",
                sessionId = currentSession?.sessionId ?: "",
                senderDeviceId = localDeviceId,
                senderDisplayName = localDisplayName,
                payloadJson = gson.toJson(record)
            )
            sendBytes(endpointId, gson.toJson(msg))
        } else if (material.contentType == "FILE" && material.localStoragePath != null) {
            val file = File(material.localStoragePath)
            if (file.exists()) {
                val filePayload = Payload.fromFile(file)
                val meta = FileTransferMetadataPayload(
                    payloadId = filePayload.id,
                    materialId = material.materialId,
                    originalFileName = material.originalFileName ?: file.name,
                    sha256Hash = material.sha256Hash ?: "",
                    fileSizeBytes = material.fileSizeBytes ?: file.length()
                )
                val metaMsg = MeshMessage(
                    type = "FILE_METADATA",
                    sessionId = currentSession?.sessionId ?: "",
                    senderDeviceId = localDeviceId,
                    senderDisplayName = localDisplayName,
                    payloadJson = gson.toJson(meta)
                )
                sendBytes(endpointId, gson.toJson(metaMsg))
                // Send the file payload
                connectionsClient.sendPayload(endpointId, filePayload)
            }
        }
    }

    private fun sendBytes(endpointId: String, data: String) {
        val payload = Payload.fromBytes(data.toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(endpointId, payload)
    }
}
