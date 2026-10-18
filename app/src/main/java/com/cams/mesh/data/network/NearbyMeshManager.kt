package com.cams.mesh.data.network

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.cams.mesh.data.local.AppDatabase
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

    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Idle)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus

    // Active connections: endpointId -> Peer info
    private val connectedEndpoints = mutableMapOf<String, PeerInfo>()
    // Tracking incoming file payloads: payloadId -> (MaterialId, TempFile)
    private val incomingFilePayloads = mutableMapOf<Long, IncomingTransferInfo>()

    private val _activeSession = MutableStateFlow<SessionEntity?>(null)
    val activeSession: StateFlow<SessionEntity?> = _activeSession

    var localDeviceId: String = UUID.randomUUID().toString()
    var localDisplayName: String = "Student"

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
                    // Handled upon completion or stream read
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
                            val finalFile = fileStorage.finalizeReceivedFile(
                                transferInfo.tempFile,
                                transferInfo.materialId,
                                transferInfo.originalFileName,
                                transferInfo.expectedHash
                            )
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
                        sessionId = activeSession?.sessionId,
                        lastSeenAt = System.currentTimeMillis(),
                        connectionState = "CONNECTED",
                        lastSuccessfulSyncAt = null
                    )
                    peerDao.insertOrUpdatePeer(peerEntity)
                    _networkStatus.value = NetworkStatus.Connected(
                        peerCount = connectedEndpoints.size,
                        roomCode = activeSession?.roomCode ?: ""
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
                val code = activeSession?.roomCode
                if (code != null) {
                    _networkStatus.value = NetworkStatus.Advertising(code)
                } else {
                    _networkStatus.value = NetworkStatus.Idle
                }
            } else {
                _networkStatus.value = NetworkStatus.Connected(
                    peerCount = connectedEndpoints.size,
                    roomCode = activeSession?.roomCode ?: ""
                )
            }
        }
    }

    fun startAdvertisingSession(session: SessionEntity) {
        _activeSession.value = session
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER) // PRD: P2P Cluster mesh topology
            .build()

        val serviceId = "com.cams.mesh.${session.roomCode}"
        connectionsClient.startAdvertising(
            localDisplayName,
            serviceId,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            _networkStatus.value = NetworkStatus.Advertising(session.roomCode)
        }.addOnFailureListener { e ->
            _networkStatus.value = NetworkStatus.Error(e.message ?: "Advertising failed")
        }
    }

    fun startDiscoverySession(roomCode: String) {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        val serviceId = "com.cams.mesh.$roomCode"
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

        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            _networkStatus.value = NetworkStatus.Discovering(roomCode)
        }.addOnFailureListener { e ->
            _networkStatus.value = NetworkStatus.Error(e.message ?: "Discovery failed")
        }
    }

    fun stopAllNetworking() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        incomingFilePayloads.clear()
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
                    // Reply with our manifest to initiate bidirectional sync
                    sendManifest(fromEndpointId)
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

    private suspend fun sendHello(endpointId: String) {
        val msg = MeshMessage(
            type = "HELLO",
            sessionId = activeSession?.sessionId ?: "",
            senderDeviceId = localDeviceId,
            senderDisplayName = localDisplayName
        )
        sendBytes(endpointId, gson.toJson(msg))
    }

    private suspend fun requestManifest(endpointId: String) {
        val msg = MeshMessage(
            type = "MANIFEST_REQUEST",
            sessionId = activeSession?.sessionId ?: "",
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
            sessionId = activeSession?.sessionId ?: "",
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

    private suspend fun compareManifestAndRequestMissing(fromEndpointId: String, remoteManifest: ManifestPayload) {
        val localMaterials = materialRepository.getAllActiveMaterials()
        val localIds = localMaterials.map { it.materialId }.toSet()

        remoteManifest.items.forEach { remoteItem ->
            if (!localIds.contains(remoteItem.materialId)) {
                // We are missing this item! Request its metadata or send an item request
                val reqMsg = MeshMessage(
                    type = "REQUEST_ITEM",
                    sessionId = activeSession?.sessionId ?: "",
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
            sessionId = activeSession?.sessionId ?: "",
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
                sessionId = activeSession?.sessionId ?: "",
                senderDeviceId = localDeviceId,
                senderDisplayName = localDisplayName,
                payloadJson = gson.toJson(record)
            )
            sendBytes(endpointId, gson.toJson(msg))
        } else if (material.contentType == "FILE" && material.localStoragePath != null) {
            val file = File(material.localStoragePath)
            if (file.exists()) {
                val filePayload = Payload.fromFile(file)
                // Register tracking
                connectionsClient.sendPayload(endpointId, filePayload)
            }
        }
    }

    private fun sendBytes(endpointId: String, data: String) {
        val payload = Payload.fromBytes(data.toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(endpointId, payload)
    }
}
