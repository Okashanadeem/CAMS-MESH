package com.cams.mesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey
    val deviceId: String,
    val displayName: String,
    val sessionId: String?,
    val lastSeenAt: Long,
    val connectionState: String, // "DISCOVERED", "CONNECTING", "CONNECTED", "DISCONNECTED"
    val lastSuccessfulSyncAt: Long?
)
