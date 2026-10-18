package com.cams.mesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val messageId: String,
    val sessionId: String,
    val senderDeviceId: String,
    val senderDisplayName: String,
    val text: String,
    val timestamp: Long,
    val isFromMe: Boolean
)
