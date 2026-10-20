package com.cams.mesh.data.repository

import android.content.Context
import android.net.Uri
import com.cams.mesh.data.local.AppDatabase
import com.cams.mesh.data.local.entity.MaterialEntity
import com.cams.mesh.data.local.entity.TombstoneEntity
import com.cams.mesh.util.FileStorageManager
import com.cams.mesh.util.HashUtil
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class MaterialRepository(
    context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val fileStorage: FileStorageManager = FileStorageManager(context)
) {
    private val materialDao = database.materialDao()
    private val tombstoneDao = database.tombstoneDao()

    fun getAllMaterialsFlow(): Flow<List<MaterialEntity>> = materialDao.getAllMaterialsFlow()

    fun getMaterialsBySessionFlow(sessionId: String): Flow<List<MaterialEntity>> = materialDao.getMaterialsBySessionFlow(sessionId)

    fun getExternalMaterialsFlow(): Flow<List<MaterialEntity>> = materialDao.getExternalMaterialsFlow()

    fun getMaterialByIdFlow(id: String): Flow<MaterialEntity?> = materialDao.getMaterialByIdFlow(id)

    fun getUploadedByMeFlow(): Flow<List<MaterialEntity>> = materialDao.getUploadedByMeFlow()

    fun getReceivedFlow(): Flow<List<MaterialEntity>> = materialDao.getReceivedFlow()

    fun searchMaterials(query: String): Flow<List<MaterialEntity>> = materialDao.searchMaterials(query)

    suspend fun getMaterialById(id: String): MaterialEntity? = materialDao.getMaterialById(id)

    suspend fun getAllActiveMaterials(): List<MaterialEntity> = materialDao.getAllActiveMaterials()

    suspend fun createTextMaterial(
        title: String,
        content: String,
        sessionId: String?,
        localDeviceId: String,
        localDisplayName: String
    ): MaterialEntity {
        val materialId = UUID.randomUUID().toString()
        val hash = HashUtil.calculateSha256(content)
        val entity = MaterialEntity(
            materialId = materialId,
            sessionId = sessionId,
            title = title,
            contentType = "TEXT",
            mimeType = "text/plain",
            originalFileName = null,
            localStoragePath = null,
            textContent = content,
            fileSizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong(),
            sha256Hash = hash,
            uploaderDeviceId = localDeviceId,
            uploaderDisplayName = localDisplayName,
            createdAt = System.currentTimeMillis(),
            receivedAt = null,
            isUploadedByLocalUser = true,
            localAvailability = "AVAILABLE",
            transferState = "COMPLETE"
        )
        materialDao.insertMaterial(entity)
        return entity
    }

    suspend fun insertMaterialDirect(entity: MaterialEntity) {
        materialDao.insertMaterial(entity)
    }

    suspend fun createFileMaterial(
        uri: Uri,
        title: String,
        sessionId: String?,
        localDeviceId: String,
        localDisplayName: String
    ): MaterialEntity {
        val materialId = UUID.randomUUID().toString()
        val (savedFile, hash) = fileStorage.saveFileFromUri(uri, materialId)
        val mimeType = fileStorage.getMimeType(savedFile.name)

        val entity = MaterialEntity(
            materialId = materialId,
            sessionId = sessionId,
            title = if (title.isBlank()) savedFile.name else title,
            contentType = "FILE",
            mimeType = mimeType,
            originalFileName = savedFile.name,
            localStoragePath = savedFile.absolutePath,
            textContent = null,
            fileSizeBytes = savedFile.length(),
            sha256Hash = hash,
            uploaderDeviceId = localDeviceId,
            uploaderDisplayName = localDisplayName,
            createdAt = System.currentTimeMillis(),
            receivedAt = null,
            isUploadedByLocalUser = true,
            localAvailability = "AVAILABLE",
            transferState = "COMPLETE"
        )
        materialDao.insertMaterial(entity)
        return entity
    }

    suspend fun receiveTextMaterial(
        materialId: String,
        title: String,
        content: String,
        sessionId: String?,
        uploaderId: String,
        uploaderName: String,
        createdAt: Long,
        hash: String?
    ) {
        // Prevent resurrecting deleted items
        if (tombstoneDao.getTombstoneForMaterial(materialId) != null) return

        val existing = materialDao.getMaterialById(materialId)
        if (existing != null) return

        val entity = MaterialEntity(
            materialId = materialId,
            sessionId = sessionId,
            title = title,
            contentType = "TEXT",
            mimeType = "text/plain",
            originalFileName = null,
            localStoragePath = null,
            textContent = content,
            fileSizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong(),
            sha256Hash = hash ?: HashUtil.calculateSha256(content),
            uploaderDeviceId = uploaderId,
            uploaderDisplayName = uploaderName,
            createdAt = createdAt,
            receivedAt = System.currentTimeMillis(),
            isUploadedByLocalUser = false,
            localAvailability = "AVAILABLE",
            transferState = "COMPLETE"
        )
        materialDao.insertMaterial(entity)
    }

    suspend fun receiveFileRecord(
        materialId: String,
        title: String,
        originalFileName: String,
        mimeType: String,
        fileSizeBytes: Long?,
        sha256Hash: String?,
        sessionId: String?,
        uploaderId: String,
        uploaderName: String,
        createdAt: Long
    ) {
        if (tombstoneDao.getTombstoneForMaterial(materialId) != null) return
        val existing = materialDao.getMaterialById(materialId)
        if (existing != null) return

        val entity = MaterialEntity(
            materialId = materialId,
            sessionId = sessionId,
            title = title,
            contentType = "FILE",
            mimeType = mimeType,
            originalFileName = originalFileName,
            localStoragePath = null,
            textContent = null,
            fileSizeBytes = fileSizeBytes,
            sha256Hash = sha256Hash,
            uploaderDeviceId = uploaderId,
            uploaderDisplayName = uploaderName,
            createdAt = createdAt,
            receivedAt = null,
            isUploadedByLocalUser = false,
            localAvailability = "UNAVAILABLE", // Available to request/download from peers
            transferState = "QUEUED"
        )
        materialDao.insertMaterial(entity)
    }

    suspend fun updateReceivedFilePath(materialId: String, filePath: String) {
        val existing = materialDao.getMaterialById(materialId) ?: return
        val updated = existing.copy(
            localStoragePath = filePath,
            localAvailability = "AVAILABLE",
            transferState = "COMPLETE",
            receivedAt = System.currentTimeMillis()
        )
        materialDao.updateMaterial(updated)
    }

    suspend fun deleteLocalCopy(materialId: String, localDeviceId: String): Boolean {
        val item = materialDao.getMaterialById(materialId) ?: return false
        if (item.localStoragePath != null) {
            fileStorage.deleteFile(item.localStoragePath)
        }
        materialDao.markAsDeleted(materialId)
        tombstoneDao.insertTombstone(
            TombstoneEntity(
                tombstoneId = UUID.randomUUID().toString(),
                materialId = materialId,
                sessionId = item.sessionId,
                deletionScope = "LOCAL",
                deletedByDeviceId = localDeviceId,
                createdAt = System.currentTimeMillis()
            )
        )
        return true
    }

    suspend fun clearAllData(): Boolean {
        fileStorage.clearAllFiles()
        materialDao.clearAllMaterials()
        database.sessionDao().clearAllSessions()
        database.peerDao().clearAllPeers()
        database.transferDao().clearAllTransfers()
        tombstoneDao.clearAllTombstones()
        return true
    }

    suspend fun getTotalStorageBytes(): Long {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            fileStorage.getTotalStorageUsageBytes()
        }
    }
}
