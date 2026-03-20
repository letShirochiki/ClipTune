package com.cliptune.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DirectoryConfigEntity::class, MediaItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ClipTuneDatabase : RoomDatabase() {
    abstract fun directoryConfigDao(): DirectoryConfigDao
    abstract fun mediaItemDao(): MediaItemDao

    companion object {
        @Volatile
        private var INSTANCE: ClipTuneDatabase? = null

        fun getInstance(context: Context): ClipTuneDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClipTuneDatabase::class.java,
                    "cliptune.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
