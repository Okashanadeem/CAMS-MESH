package com.cams.mesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "materials")
data class MaterialEntity(
    @PrimaryKey
    val materialId: String,
    val sessionId: String?,
    val title: String,
    val contentType: String, // "TEXT" or "FILE"
    val mimeType: String,
    val originalFileName: String?,
    val localStoragePath: String?,
    val textContent: String?,
    val fileSizeBytes: Long?,
    val sha256Hash: String?,
    val uploaderDeviceId: String,
    val uploaderDisplayName: String,
    val createdAt: Long,
    val receivedAt: Long?,
    val isUploadedByLocalUser: Boolean,
    val localAvailability: String, // "AVAILABLE", "PARTIAL", "UNAVAILABLE", "DELETED"
    val transferState: String, // "QUEUED", "TRANSFERRING", "COMPLETE", "FAILED", "UNKNOWN"
    val deletedAt: Long? = null
)
