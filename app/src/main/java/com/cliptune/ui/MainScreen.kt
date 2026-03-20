package com.cliptune.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cliptune.R
import com.cliptune.model.ConversionState
import com.cliptune.model.DirectoryConfig
import com.cliptune.model.LibraryCategory
import com.cliptune.model.LibraryMediaItem
import com.cliptune.model.PlaybackRepeatMode
import com.cliptune.model.PlayerUiState
import com.cliptune.viewmodel.MainViewModel
import java.util.Locale

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val directoryConfig by viewModel.directoryConfig.collectAsStateWithLifecycle()
    val sourceItems by viewModel.sourceItems.collectAsStateWithLifecycle()
    val extractedItems by viewModel.extractedItems.collectAsStateWithLifecycle()
    val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val playerUiState by viewModel.playerUiState.collectAsStateWithLifecycle()
    val playerForUi by viewModel.playerForUi.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(LibraryCategory.SOURCE) }
    var isDirectoryExpanded by remember { mutableStateOf(false) }
    var seekSliderProgress by remember { mutableFloatStateOf(0f) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var customSleepMinutes by rememberSaveable { mutableStateOf("") }

    // TODO(Android9): 部分 Android 9 机型在 OpenDocumentTree 返回后闪退，需补充堆栈并增加兼容方案。
    val sourceDirLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { viewModel.onSourceDirectorySelected(context, it) }
        }
    // TODO(Android9): 部分 Android 9 机型在 OpenDocumentTree 返回后闪退，需补充堆栈并增加兼容方案。
    val outputDirLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { viewModel.onOutputDirectorySelected(context, it) }
        }

    LaunchedEffect(Unit) {
        viewModel.connectPlayer(context)
    }

    LaunchedEffect(playerUiState.positionMs, playerUiState.durationMs, isUserSeeking) {
        if (!isUserSeeking) {
            seekSliderProgress = if (playerUiState.durationMs > 0L) {
                (playerUiState.positionMs.toFloat() / playerUiState.durationMs.toFloat())
                    .coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    BackHandler(enabled = playerUiState.isFullPlayerVisible) {
        viewModel.closeFullPlayer()
    }

    val activeItems = if (currentTab == LibraryCategory.SOURCE) sourceItems else extractedItems

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                item {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                item {
                    DirectorySection(
                        directoryConfig = directoryConfig,
                        isExpanded = isDirectoryExpanded,
                        isSyncing = isSyncing,
                        onToggleExpand = { isDirectoryExpanded = !isDirectoryExpanded },
                        onPickSource = { sourceDirLauncher.launch(null) },
                        onPickOutput = { outputDirLauncher.launch(null) },
                        onRefresh = { viewModel.refreshLibraries() }
                    )
                }

                item {
                    TabRow(selectedTabIndex = if (currentTab == LibraryCategory.SOURCE) 0 else 1) {
                        Tab(
                            selected = currentTab == LibraryCategory.SOURCE,
                            onClick = { currentTab = LibraryCategory.SOURCE },
                            text = { Text(text = stringResource(R.string.tab_source_library)) }
                        )
                        Tab(
                            selected = currentTab == LibraryCategory.EXTRACTED,
                            onClick = { currentTab = LibraryCategory.EXTRACTED },
                            text = { Text(text = stringResource(R.string.tab_extracted_library)) }
                        )
                    }
                }

                if (activeItems.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(
                                if (currentTab == LibraryCategory.SOURCE) {
                                    R.string.source_list_empty
                                } else {
                                    R.string.extracted_list_empty
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    items(activeItems, key = { it.uri }) { item ->
                        MediaListItem(
                            item = item,
                            category = currentTab,
                            onPlay = { viewModel.playItem(currentTab, item.uri) },
                            onDelete = { viewModel.deleteMediaItem(item) },
                            onExtractMp3 = { viewModel.extractToMp3(item) },
                            onExtractOriginal = { viewModel.extractOriginalAudio(item) }
                        )
                    }
                }

                if (conversionState is ConversionState.Converting) {
                    item {
                        val progress = (conversionState as ConversionState.Converting).progress
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = stringResource(R.string.extracting_audio_progress))
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(text = "${(progress * 100).toInt()}%")
                        }
                    }
                }

                if (uiMessage != null) {
                    item {
                        MessageSection(
                            message = uiMessage.orEmpty(),
                            onDismiss = { viewModel.clearMessage() }
                        )
                    }
                }
            }

            MiniPlayerBar(
                modifier = Modifier.fillMaxWidth(),
                playerUiState = playerUiState,
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onNext = { viewModel.seekToNext() },
                onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                onOpenFullPlayer = { viewModel.openFullPlayer() }
            )
        }

        if (playerUiState.isFullPlayerVisible) {
            FullPlayerOverlay(
                modifier = Modifier.fillMaxSize(),
                playerUiState = playerUiState,
                playerForUi = playerForUi,
                seekSliderProgress = seekSliderProgress,
                customSleepMinutes = customSleepMinutes,
                onCustomSleepMinutesChange = { input ->
                    customSleepMinutes = input.filter { it.isDigit() }.take(4)
                },
                onSeekValueChange = {
                    isUserSeeking = true
                    seekSliderProgress = it
                },
                onSeekFinished = {
                    val duration = playerUiState.durationMs
                    if (duration > 0L) {
                        viewModel.seekTo((duration * seekSliderProgress).toLong())
                    }
                    isUserSeeking = false
                },
                onPrevious = { viewModel.seekToPrevious() },
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onNext = { viewModel.seekToNext() },
                onStop = { viewModel.stopPlayback() },
                onVolumeChange = { viewModel.setVolume(it) },
                onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                onStartSleepTimer = { minutes -> viewModel.startSleepTimer(minutes) },
                onCancelSleepTimer = { viewModel.cancelSleepTimer() },
                onClose = { viewModel.closeFullPlayer() }
            )
        }
    }
}

@Composable
private fun MiniPlayerBar(
    modifier: Modifier = Modifier,
    playerUiState: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onOpenFullPlayer: () -> Unit
) {
    Card(
        modifier = modifier.clickable(
            enabled = playerUiState.connected || playerUiState.currentTitle.isNotBlank(),
            onClick = onOpenFullPlayer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (playerUiState.currentTitle.isNotBlank()) {
                    playerUiState.currentTitle
                } else {
                    stringResource(R.string.no_playing_file)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onTogglePlayPause,
                    enabled = playerUiState.connected,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (playerUiState.isPlaying) {
                            stringResource(R.string.pause_playback)
                        } else {
                            stringResource(R.string.start_playback)
                        }
                    )
                }
                OutlinedButton(
                    onClick = onNext,
                    enabled = playerUiState.canSkipNext,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.next_track))
                }
                OutlinedButton(
                    onClick = onCycleRepeatMode,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = repeatModeLabel(playerUiState.repeatMode))
                }
            }

            val timerText = if (playerUiState.isSleepTimerRunning) {
                stringResource(
                    R.string.sleep_timer_running,
                    formatCountdown(playerUiState.sleepTimerRemainingMs)
                )
            } else {
                stringResource(R.string.sleep_timer_not_set)
            }
            Text(
                text = timerText,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.open_full_player_hint),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun FullPlayerOverlay(
    modifier: Modifier = Modifier,
    playerUiState: PlayerUiState,
    playerForUi: Player?,
    seekSliderProgress: Float,
    customSleepMinutes: String,
    onCustomSleepMinutesChange: (String) -> Unit,
    onSeekValueChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onCycleRepeatMode: () -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onClose: () -> Unit
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.full_player_title),
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedButton(onClick = onClose) {
                    Text(text = stringResource(R.string.close_full_player))
                }
            }

            VideoSection(
                playerUiState = playerUiState,
                playerForUi = playerForUi,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = if (playerUiState.currentTitle.isNotBlank()) {
                    playerUiState.currentTitle
                } else {
                    stringResource(R.string.no_playing_file)
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.repeat_mode_line,
                        repeatModeLabel(playerUiState.repeatMode)
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = onCycleRepeatMode) {
                    Text(text = stringResource(R.string.switch_repeat_mode))
                }
            }

            Slider(
                value = seekSliderProgress,
                onValueChange = onSeekValueChange,
                onValueChangeFinished = onSeekFinished,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(
                    R.string.player_time_line,
                    formatDuration(playerUiState.positionMs),
                    formatDuration(playerUiState.durationMs)
                ),
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPrevious,
                    modifier = Modifier.weight(1f),
                    enabled = playerUiState.canSkipPrevious
                ) {
                    Text(text = stringResource(R.string.previous_track))
                }
                Button(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.weight(1f),
                    enabled = playerUiState.connected
                ) {
                    Text(
                        text = if (playerUiState.isPlaying) {
                            stringResource(R.string.pause_playback)
                        } else {
                            stringResource(R.string.start_playback)
                        }
                    )
                }
                OutlinedButton(
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                    enabled = playerUiState.canSkipNext
                ) {
                    Text(text = stringResource(R.string.next_track))
                }
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                    enabled = playerUiState.connected
                ) {
                    Text(text = stringResource(R.string.stop_playback))
                }
            }

            Divider()

            Text(
                text = stringResource(R.string.player_volume_label),
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = playerUiState.volume.coerceIn(0f, 1f),
                onValueChange = onVolumeChange,
                modifier = Modifier.fillMaxWidth()
            )

            Divider()

            Text(
                text = stringResource(R.string.sleep_timer_title),
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onStartSleepTimer(15) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.sleep_timer_15m))
                }
                OutlinedButton(
                    onClick = { onStartSleepTimer(30) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.sleep_timer_30m))
                }
                OutlinedButton(
                    onClick = { onStartSleepTimer(60) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.sleep_timer_60m))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customSleepMinutes,
                    onValueChange = onCustomSleepMinutesChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.sleep_timer_custom_minutes)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(
                    onClick = {
                        onStartSleepTimer(customSleepMinutes.toIntOrNull() ?: 0)
                    }
                ) {
                    Text(text = stringResource(R.string.sleep_timer_start))
                }
            }

            val timerText = if (playerUiState.isSleepTimerRunning) {
                stringResource(
                    R.string.sleep_timer_running,
                    formatCountdown(playerUiState.sleepTimerRemainingMs)
                )
            } else {
                stringResource(R.string.sleep_timer_not_set)
            }
            Text(text = timerText, style = MaterialTheme.typography.bodySmall)

            OutlinedButton(
                onClick = onCancelSleepTimer,
                enabled = playerUiState.isSleepTimerRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.sleep_timer_cancel))
            }
        }
    }
}

@Composable
private fun VideoSection(
    playerUiState: PlayerUiState,
    playerForUi: Player?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (playerUiState.canShowVideo && playerForUi != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        player = playerForUi
                    }
                },
                update = { view ->
                    view.player = playerForUi
                }
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_media_play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = stringResource(R.string.video_not_available),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DirectorySection(
    directoryConfig: DirectoryConfig,
    isExpanded: Boolean,
    isSyncing: Boolean,
    onToggleExpand: () -> Unit,
    onPickSource: () -> Unit,
    onPickOutput: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.directory_panel_title))
                OutlinedButton(onClick = onToggleExpand) {
                    Text(
                        text = if (isExpanded) {
                            stringResource(R.string.collapse_directory)
                        } else {
                            stringResource(R.string.expand_directory)
                        }
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.source_directory_summary,
                    directoryConfig.sourceTreeUri ?: stringResource(R.string.directory_not_selected)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(
                    R.string.output_directory_summary,
                    directoryConfig.outputTreeUri ?: stringResource(R.string.directory_not_selected)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )

            if (isExpanded) {
                Button(onClick = onPickSource, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.select_source_directory))
                }
                Button(onClick = onPickOutput, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.select_output_directory))
                }
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    val text = if (isSyncing) {
                        stringResource(R.string.refreshing_library)
                    } else {
                        stringResource(R.string.refresh_library)
                    }
                    Text(text = text)
                }
            }
        }
    }
}

@Composable
private fun MediaListItem(
    item: LibraryMediaItem,
    category: LibraryCategory,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onExtractMp3: () -> Unit,
    onExtractOriginal: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = item.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(
                    R.string.file_meta_line,
                    formatFileSize(item.sizeBytes),
                    formatDuration(item.durationMs)
                ),
                style = MaterialTheme.typography.bodySmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPlay, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.play_file))
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.delete_file))
                }
            }

            if (category == LibraryCategory.SOURCE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExtractMp3, modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.extract_to_mp3))
                    }
                    Button(onClick = onExtractOriginal, modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.extract_original_audio))
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageSection(
    message: String,
    onDismiss: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = message, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dismiss_message))
            }
        }
    }
}

@Composable
private fun repeatModeLabel(mode: PlaybackRepeatMode): String {
    return when (mode) {
        PlaybackRepeatMode.OFF -> stringResource(R.string.repeat_mode_off)
        PlaybackRepeatMode.ONE -> stringResource(R.string.repeat_mode_one)
        PlaybackRepeatMode.ALL -> stringResource(R.string.repeat_mode_all)
    }
}

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroup = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(
        Locale.getDefault(),
        "%.2f %s",
        sizeBytes / Math.pow(1024.0, digitGroup.toDouble()),
        units[digitGroup.coerceAtMost(units.lastIndex)]
    )
}

private fun formatDuration(durationMs: Long): String {
    val safeMs = durationMs.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private fun formatCountdown(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
