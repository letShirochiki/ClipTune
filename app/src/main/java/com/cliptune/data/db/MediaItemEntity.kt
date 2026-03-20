package com.cliptune.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val mediaKind: String,
    val lastScannedAt: Long,
    val sourceTag: String
)
