package com.cams.mesh.data.local.dao

import androidx.room.*
import com.cams.mesh.data.local.entity.TombstoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TombstoneDao {
    @Query("SELECT * FROM tombstones WHERE materialId = :materialId LIMIT 1")
    suspend fun getTombstoneForMaterial(materialId: String): TombstoneEntity?

    @Query("SELECT * FROM tombstones")
    suspend fun getAllTombstones(): List<TombstoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: TombstoneEntity)

    @Query("DELETE FROM tombstones")
    suspend fun clearAllTombstones()
}
