package com.cams.mesh.data.local.dao

import androidx.room.*
import com.cams.mesh.data.local.entity.MaterialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {
    @Query("SELECT * FROM materials WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun getAllMaterialsFlow(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE deletedAt IS NULL AND sessionId = :sessionId ORDER BY createdAt DESC")
    fun getMaterialsBySessionFlow(sessionId: String): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE deletedAt IS NULL AND sessionId IS NULL ORDER BY createdAt DESC")
    fun getExternalMaterialsFlow(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE materialId = :materialId LIMIT 1")
    suspend fun getMaterialById(materialId: String): MaterialEntity?

    @Query("SELECT * FROM materials WHERE deletedAt IS NULL AND materialId = :materialId LIMIT 1")
    fun getMaterialByIdFlow(materialId: String): Flow<MaterialEntity?>

    @Query("SELECT * FROM materials WHERE deletedAt IS NULL AND isUploadedByLocalUser = 1 ORDER BY createdAt DESC")
    fun getUploadedByMeFlow(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE deletedAt IS NULL AND isUploadedByLocalUser = 0 ORDER BY createdAt DESC")
    fun getReceivedFlow(): Flow<List<MaterialEntity>>

    @Query("""
        SELECT * FROM materials 
        WHERE deletedAt IS NULL 
        AND (
            title LIKE '%' || :query || '%' 
            OR originalFileName LIKE '%' || :query || '%' 
            OR textContent LIKE '%' || :query || '%' 
            OR uploaderDisplayName LIKE '%' || :query || '%'
        )
        ORDER BY createdAt DESC
    """)
    fun searchMaterials(query: String): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE deletedAt IS NULL")
    suspend fun getAllActiveMaterials(): List<MaterialEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterial(material: MaterialEntity)

    @Update
    suspend fun updateMaterial(material: MaterialEntity)

    @Query("UPDATE materials SET deletedAt = :timestamp, localAvailability = 'DELETED' WHERE materialId = :materialId")
    suspend fun markAsDeleted(materialId: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM materials WHERE materialId = :materialId")
    suspend fun deletePermanently(materialId: String)

    @Query("DELETE FROM materials")
    suspend fun clearAllMaterials()
}
