package com.cams.mesh.data.local.dao

import androidx.room.*
import com.cams.mesh.data.local.entity.PeerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers WHERE sessionId = :sessionId AND connectionState = 'CONNECTED'")
    fun getConnectedPeersFlow(sessionId: String): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE connectionState = 'CONNECTED'")
    fun getAllConnectedPeersFlow(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getPeerById(deviceId: String): PeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePeer(peer: PeerEntity)

    @Query("UPDATE peers SET connectionState = :state, lastSeenAt = :lastSeen WHERE deviceId = :deviceId")
    suspend fun updatePeerState(deviceId: String, state: String, lastSeen: Long = System.currentTimeMillis())

    @Query("DELETE FROM peers WHERE deviceId = :deviceId")
    suspend fun deletePeer(deviceId: String)

    @Query("DELETE FROM peers")
    suspend fun clearAllPeers()
}
