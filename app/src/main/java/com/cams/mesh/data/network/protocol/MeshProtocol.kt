package com.cams.mesh.data.network.protocol

data class MeshMessage(
    val protocolVersion: Int = 1,
    val type: String, // e.g. HELLO, MANIFEST_REQUEST, MANIFEST_RESPONSE, SYNC_RECORD, REQUEST_ITEM, TOMBSTONE
    val sessionId: String,
    val senderDeviceId: String,
    val senderDisplayName: String,
    val payloadJson: String? = null
)

data class ManifestItem(
    val materialId: String,
    val sha256Hash: String?,
    val createdAt: Long,
    val contentType: String,
    val title: String,
    val fileSizeBytes: Long?
)

data class ManifestPayload(
    val items: List<ManifestItem>
)

data class SyncRecordPayload(
    val materialId: String,
    val sessionId: String?,
    val title: String,
    val contentType: String,
    val mimeType: String,
    val originalFileName: String?,
    val textContent: String?,
    val fileSizeBytes: Long?,
    val sha256Hash: String?,
    val uploaderDeviceId: String,
    val uploaderDisplayName: String,
    val createdAt: Long
)

data class ItemRequestPayload(
    val materialId: String
)

data class TombstonePayload(
    val materialId: String,
    val sessionId: String?,
    val scope: String, // LOCAL, CLASSROOM
    val deletedByDeviceId: String,
    val createdAt: Long
)

data class SessionInfoPayload(
    val sessionId: String,
    val roomCode: String,
    val sessionName: String,
    val creatorDeviceId: String,
    val isVisibleToAll: Boolean = true,
    val approvalMode: Boolean = false
)

data class ChatPayload(
    val messageId: String,
    val sessionId: String,
    val text: String,
    val senderDeviceId: String,
    val senderDisplayName: String,
    val timestamp: Long
)

data class FileTransferMetadataPayload(
    val payloadId: Long,
    val materialId: String,
    val originalFileName: String,
    val sha256Hash: String,
    val fileSizeBytes: Long
)
