package com.cams.mesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val sessionId: String,
    val roomCode: String,
    val sessionName: String,
    val creatorDeviceId: String,
    val createdAt: Long,
    val sessionState: String, // "ACTIVE", "DISCONNECTED", "ENDED"
    val isVisibleToAll: Boolean = true,
    val approvalMode: Boolean = false,
    val lastSeenAt: Long
)
