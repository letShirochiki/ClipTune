package com.cliptune.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "directory_config")
data class DirectoryConfigEntity(
    @PrimaryKey val id: Int = 1,
    val sourceTreeUri: String? = null,
    val outputTreeUri: String? = null
)
