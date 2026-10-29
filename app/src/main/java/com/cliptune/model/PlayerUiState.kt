package com.cliptune.model

data class PlayerUiState(
    val connected: Boolean = false,
    val currentTitle: String = "",
    /** 当前播放条目的 content uri，用来在列表里标出正在播放的那一条 */
    val currentMediaUri: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    val activeCategory: LibraryCategory? = null,
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.ALL,
    val sleepTimerRemainingMs: Long = 0L,
    val isSleepTimerRunning: Boolean = false,
    val canShowVideo: Boolean = false,
    val isFullPlayerVisible: Boolean = false
)
