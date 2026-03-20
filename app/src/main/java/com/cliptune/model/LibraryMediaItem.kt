package com.cliptune.model

data class LibraryMediaItem(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val mediaKind: MediaKind,
    val lastScannedAt: Long
)
