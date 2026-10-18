package com.cams.mesh.data.local.dao

import androidx.room.*
import com.cams.mesh.data.local.entity.TransferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers WHERE materialId = :materialId")
    fun getTransfersForMaterialFlow(materialId: String): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE transferState = 'RUNNING'")
    fun getActiveTransfersFlow(): Flow<List<TransferEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTransfer(transfer: TransferEntity)

    @Query("DELETE FROM transfers WHERE transferId = :transferId")
    suspend fun deleteTransfer(transferId: String)

    @Query("DELETE FROM transfers")
    suspend fun clearAllTransfers()
}
