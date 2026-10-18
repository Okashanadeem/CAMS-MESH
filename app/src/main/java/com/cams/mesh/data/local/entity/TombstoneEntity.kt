package com.cams.mesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tombstones")
data class TombstoneEntity(
    @PrimaryKey
    val tombstoneId: String,
    val materialId: String,
    val sessionId: String?,
    val deletionScope: String, // "LOCAL", "CLASSROOM"
    val deletedByDeviceId: String,
    val createdAt: Long
)
