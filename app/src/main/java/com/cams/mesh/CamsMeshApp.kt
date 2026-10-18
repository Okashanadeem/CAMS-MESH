package com.cams.mesh

import android.app.Application
import com.cams.mesh.data.local.AppDatabase
import com.cams.mesh.data.network.NearbyMeshManager
import com.cams.mesh.data.repository.MaterialRepository
import com.cams.mesh.util.FileStorageManager

class CamsMeshApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val fileStorage by lazy { FileStorageManager(this) }
    val materialRepository by lazy { MaterialRepository(this, database, fileStorage) }
    val nearbyMeshManager by lazy { NearbyMeshManager(this, materialRepository, fileStorage) }

    override fun onCreate() {
        super.onCreate()
    }
}
