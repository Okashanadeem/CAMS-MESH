package com.cams.mesh.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cams.mesh.data.local.dao.*
import com.cams.mesh.data.local.entity.*

@Database(
    entities = [
        MaterialEntity::class,
        SessionEntity::class,
        PeerEntity::class,
        TransferEntity::class,
        TombstoneEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun materialDao(): MaterialDao
    abstract fun sessionDao(): SessionDao
    abstract fun peerDao(): PeerDao
    abstract fun transferDao(): TransferDao
    abstract fun tombstoneDao(): TombstoneDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cams_mesh_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
