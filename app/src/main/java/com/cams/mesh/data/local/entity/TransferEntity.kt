package com.cams.mesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey
    val transferId: String,
    val materialId: String,
    val peerDeviceId: String,
    val direction: String, // "INCOMING" or "OUTGOING"
    val bytesTransferred: Long,
    val totalBytes: Long,
    val transferState: String, // "QUEUED", "RUNNING", "COMPLETED", "FAILED"
    val retryCount: Int,
    val lastErrorCode: String?,
    val updatedAt: Long
)
