package com.cliptune.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cliptune.R
import com.cliptune.model.ConversionState
import com.cliptune.model.DirectoryConfig
import com.cliptune.model.DirectoryDisplay
import com.cliptune.model.LibraryCategory
import com.cliptune.model.LibraryMediaItem
import com.cliptune.model.LibraryScanState
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
    val sourceScanState by viewModel.sourceScanState.collectAsStateWithLifecycle()
    val outputScanState by viewModel.outputScanState.collectAsStateWithLifecycle()
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
    var isSleepTimerDialogVisible by remember { mutableStateOf(false) }

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

    BackHandler(enabled = isSleepTimerDialogVisible) {
        isSleepTimerDialogVisible = false
    }
    // 全屏时返回键先退出全屏
    BackHandler(enabled = playerUiState.isFullPlayerVisible && !isSleepTimerDialogVisible) {
        viewModel.closeFullPlayer()
    }

    val activeItems = if (currentTab == LibraryCategory.SOURCE) sourceItems else extractedItems
    val activeScanState = if (currentTab == LibraryCategory.SOURCE) sourceScanState else outputScanState

    val onSeekFinished = {
        val duration = playerUiState.durationMs
        if (duration > 0L) {
            viewModel.seekTo((duration * seekSliderProgress).toLong())
        }
        isUserSeeking = false
    }

    if (playerUiState.isFullPlayerVisible) {
        FullscreenPlayer(
            modifier = modifier.fillMaxSize(),
            playerUiState = playerUiState,
            playerForUi = playerForUi,
            seekSliderProgress = seekSliderProgress,
            onSeekValueChange = {
                isUserSeeking = true
                seekSliderProgress = it
            },
            onSeekFinished = onSeekFinished,
            onPrevious = { viewModel.seekToPrevious() },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onNext = { viewModel.seekToNext() },
            onExit = { viewModel.closeFullPlayer() }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppHeader(
            directoryConfig = directoryConfig,
            sourceScanState = sourceScanState,
            outputScanState = outputScanState,
            isExpanded = isDirectoryExpanded,
            isSyncing = isSyncing,
            onToggleDirectory = { isDirectoryExpanded = !isDirectoryExpanded },
            onRefresh = { viewModel.refreshLibraries() }
        )

        if (isDirectoryExpanded) {
            DirectoryPanel(
                directoryConfig = directoryConfig,
                sourceScanState = sourceScanState,
                outputScanState = outputScanState,
                onPickSource = { sourceDirLauncher.launch(null) },
                onPickOutput = { outputDirLauncher.launch(null) }
            )
        }

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

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 4.dp)
        ) {
            if (activeItems.isEmpty()) {
                item {
                    Text(
                        text = libraryEmptyMessage(
                            category = currentTab,
                            scanState = activeScanState
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                items(activeItems, key = { it.uri }) { item ->
                    val isActive = playerUiState.currentMediaUri == item.uri

                    if (isActive) {
                        // 正在播放的条目就地变成播放器，控制按钮都收在这里
                        ActiveMediaCard(
                            playerUiState = playerUiState,
                            playerForUi = playerForUi,
                            seekSliderProgress = seekSliderProgress,
                            onSeekValueChange = {
                                isUserSeeking = true
                                seekSliderProgress = it
                            },
                            onSeekFinished = onSeekFinished,
                            onPrevious = { viewModel.seekToPrevious() },
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onNext = { viewModel.seekToNext() },
                            onStop = { viewModel.stopPlayback() },
                            onVolumeChange = { viewModel.setVolume(it) },
                            onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                            onOpenSleepTimer = { isSleepTimerDialogVisible = true },
                            onToggleFullscreen = { viewModel.openFullPlayer() }
                        )
                    } else {
                        MediaListItem(
                            item = item,
                            category = currentTab,
                            onPlay = { viewModel.playItem(currentTab, item.uri) },
                            onDelete = { viewModel.deleteMediaItem(item) },
                            onExtractMp3 = { viewModel.extractToMp3(item) }
                        )
                    }
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
    }

    if (isSleepTimerDialogVisible) {
        SleepTimerDialog(
            playerUiState = playerUiState,
            customMinutes = customSleepMinutes,
            onCustomMinutesChange = { input ->
                customSleepMinutes = input.filter { it.isDigit() }.take(4)
            },
            onStartTimer = { minutes ->
                viewModel.startSleepTimer(minutes)
                isSleepTimerDialogVisible = false
            },
            onCancelTimer = { viewModel.cancelSleepTimer() },
            onDismiss = { isSleepTimerDialogVisible = false }
        )
    }
}

/** 顶部标题 + 目录状态 + 目录/刷新两个图标操作 */
@Composable
private fun AppHeader(
    directoryConfig: DirectoryConfig,
    sourceScanState: LibraryScanState,
    outputScanState: LibraryScanState,
    isExpanded: Boolean,
    isSyncing: Boolean,
    onToggleDirectory: () -> Unit,
    onRefresh: () -> Unit
) {
    val notSelected = stringResource(R.string.directory_not_selected)
    val sourceName = DirectoryDisplay.nameOf(directoryConfig.sourceTreeUri) ?: notSelected
    val outputName = DirectoryDisplay.nameOf(directoryConfig.outputTreeUri) ?: notSelected
    val summary = stringResource(
        R.string.header_directory_line,
        sourceName + scanSuffix(sourceScanState),
        outputName + scanSuffix(outputScanState)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isSyncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        CompactIconButton(
            icon = Icons.Filled.FolderOpen,
            contentDescription = stringResource(R.string.expand_directory_panel),
            onClick = onToggleDirectory,
            tint = if (isExpanded) {
                MaterialTheme.colorScheme.primary
            } else {
                LocalContentColor.current
            }
        )
        CompactIconButton(
            icon = Icons.Filled.Refresh,
            contentDescription = stringResource(R.string.refresh_library),
            onClick = onRefresh
        )
    }
}

@Composable
private fun scanSuffix(scanState: LibraryScanState): String {
    return when (scanState) {
        is LibraryScanState.Ready -> if (scanState.itemCount > 0) {
            stringResource(R.string.scan_count_suffix, scanState.itemCount)
        } else {
            ""
        }

        is LibraryScanState.Failed -> stringResource(R.string.scan_unreadable_suffix)
        LibraryScanState.NoDirectory -> ""
    }
}

/** 展开后的目录设置：每行一个目录 + 一个“换目录”图标，底部保留安卓 11 的选目录说明 */
@Composable
private fun DirectoryPanel(
    directoryConfig: DirectoryConfig,
    sourceScanState: LibraryScanState,
    outputScanState: LibraryScanState,
    onPickSource: () -> Unit,
    onPickOutput: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DirectoryRow(
                icon = Icons.Filled.VideoLibrary,
                label = stringResource(R.string.source_directory_label),
                treeUri = directoryConfig.sourceTreeUri,
                scanState = sourceScanState,
                actionDescription = stringResource(R.string.select_source_directory),
                onAction = onPickSource
            )
            DirectoryRow(
                icon = Icons.Filled.LibraryMusic,
                label = stringResource(R.string.output_directory_label),
                treeUri = directoryConfig.outputTreeUri,
                scanState = outputScanState,
                actionDescription = stringResource(R.string.select_output_directory),
                onAction = onPickOutput
            )
            Text(
                text = stringResource(R.string.directory_picker_scope_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DirectoryRow(
    icon: ImageVector,
    label: String,
    treeUri: String?,
    scanState: LibraryScanState,
    actionDescription: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = DirectoryDisplay.nameOf(treeUri)
                    ?: stringResource(R.string.directory_not_selected),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (scanState is LibraryScanState.Failed) {
                Text(
                    text = stringResource(R.string.scan_status_failed, scanState.message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        CompactIconButton(
            icon = Icons.Filled.FolderOpen,
            contentDescription = actionDescription,
            onClick = onAction,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 空闲列表项：左边文件名与信息，右边图标操作。
 * 从 MP3 和“原音轨”两个按钮收敛成一个：两者听起来几乎没区别，留一个更清楚。
 */
@Composable
private fun MediaListItem(
    item: LibraryMediaItem,
    category: LibraryCategory,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onExtractMp3: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.file_meta_line,
                        formatFileSize(item.sizeBytes),
                        formatDuration(item.durationMs)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (category == LibraryCategory.SOURCE) {
                CompactIconButton(
                    icon = Icons.Filled.MusicNote,
                    contentDescription = stringResource(R.string.extract_to_mp3),
                    onClick = onExtractMp3
                )
            }
            CompactIconButton(
                icon = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.play_file),
                onClick = onPlay,
                tint = MaterialTheme.colorScheme.primary
            )
            CompactIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.delete_file),
                onClick = onDelete
            )
        }
    }
}

/**
 * 正在播放的列表项：就地展开成播放器。
 * 视频画面 + 进度 + 一排控制图标（上一首/播放/下一首/停止/循环/定时/全屏）。
 */
@Composable
private fun ActiveMediaCard(
    playerUiState: PlayerUiState,
    playerForUi: Player?,
    seekSliderProgress: Float,
    onSeekValueChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onCycleRepeatMode: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (playerUiState.canShowVideo) {
                VideoSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    playerUiState = playerUiState,
                    playerForUi = playerForUi,
                    onTogglePlayPause = onTogglePlayPause
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.audio_only_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = playerUiState.currentTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Slider(
                value = seekSliderProgress,
                onValueChange = onSeekValueChange,
                onValueChangeFinished = onSeekFinished,
                enabled = playerUiState.connected,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(playerUiState.positionMs),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = formatDuration(playerUiState.durationMs),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactIconButton(
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.previous_track),
                    onClick = onPrevious,
                    enabled = playerUiState.canSkipPrevious,
                    buttonSize = 42.dp,
                    iconSize = 22.dp
                )
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    enabled = playerUiState.connected,
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = if (playerUiState.isPlaying) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = if (playerUiState.isPlaying) {
                            stringResource(R.string.pause_playback)
                        } else {
                            stringResource(R.string.start_playback)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
                CompactIconButton(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.next_track),
                    onClick = onNext,
                    enabled = playerUiState.canSkipNext,
                    buttonSize = 42.dp,
                    iconSize = 22.dp
                )
                CompactIconButton(
                    icon = Icons.Filled.Stop,
                    contentDescription = stringResource(R.string.stop_playback),
                    onClick = onStop,
                    enabled = playerUiState.connected,
                    buttonSize = 42.dp
                )
                CompactIconButton(
                    icon = repeatModeIcon(playerUiState.repeatMode),
                    contentDescription = stringResource(R.string.switch_repeat_mode),
                    onClick = onCycleRepeatMode,
                    buttonSize = 42.dp,
                    tint = if (playerUiState.repeatMode == PlaybackRepeatMode.OFF) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                CompactIconButton(
                    icon = Icons.Filled.Timer,
                    contentDescription = stringResource(R.string.sleep_timer_title),
                    onClick = onOpenSleepTimer,
                    buttonSize = 42.dp,
                    tint = if (playerUiState.isSleepTimerRunning) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        LocalContentColor.current
                    }
                )
                CompactIconButton(
                    icon = Icons.Filled.Fullscreen,
                    contentDescription = stringResource(R.string.play_fullscreen),
                    onClick = onToggleFullscreen,
                    buttonSize = 42.dp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (playerUiState.volume <= 0.01f) {
                        Icons.Filled.VolumeOff
                    } else {
                        Icons.Filled.VolumeUp
                    },
                    contentDescription = stringResource(R.string.player_volume_label),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = playerUiState.volume.coerceIn(0f, 1f),
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1f)
                )
            }

            if (playerUiState.isSleepTimerRunning) {
                Text(
                    text = stringResource(
                        R.string.sleep_timer_running,
                        formatCountdown(playerUiState.sleepTimerRemainingMs)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 原生全屏：隐藏状态栏/导航栏并横屏，退出时全部还原 */
@Composable
private fun FullscreenPlayer(
    modifier: Modifier = Modifier,
    playerUiState: PlayerUiState,
    playerForUi: Player?,
    seekSliderProgress: Float,
    onSeekValueChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit
) {
    FullscreenWindowEffect(landscape = playerUiState.canShowVideo)

    Box(modifier = modifier.background(Color.Black)) {
        VideoSurface(
            modifier = Modifier.fillMaxSize(),
            playerUiState = playerUiState,
            playerForUi = playerForUi,
            onTogglePlayPause = onTogglePlayPause
        )

        CompactIconButton(
            icon = Icons.Filled.FullscreenExit,
            contentDescription = stringResource(R.string.exit_fullscreen),
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            tint = Color.White,
            buttonSize = 44.dp,
            iconSize = 24.dp
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Slider(
                value = seekSliderProgress,
                onValueChange = onSeekValueChange,
                onValueChangeFinished = onSeekFinished,
                enabled = playerUiState.connected,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(playerUiState.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
                Text(
                    text = formatDuration(playerUiState.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactIconButton(
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.previous_track),
                    onClick = onPrevious,
                    enabled = playerUiState.canSkipPrevious,
                    tint = Color.White,
                    buttonSize = 44.dp,
                    iconSize = 22.dp
                )
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    enabled = playerUiState.connected,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (playerUiState.isPlaying) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = if (playerUiState.isPlaying) {
                            stringResource(R.string.pause_playback)
                        } else {
                            stringResource(R.string.start_playback)
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }
                CompactIconButton(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.next_track),
                    onClick = onNext,
                    enabled = playerUiState.canSkipNext,
                    tint = Color.White,
                    buttonSize = 44.dp,
                    iconSize = 22.dp
                )
            }
        }
    }
}

/**
 * 用安卓原生 API 进全屏：隐藏系统栏 + 横屏。
 * 离开组合时把系统栏和方向还原，避免影响其他页面。
 */
@Composable
private fun FullscreenWindowEffect(landscape: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current

    DisposableEffect(landscape) {
        val activity = context.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }

        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        if (landscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (landscape) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** 内置视频画面：点画面即播放/暂停，暂停时叠一个大播放键 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(
    modifier: Modifier = Modifier,
    playerUiState: PlayerUiState,
    playerForUi: Player?,
    onTogglePlayPause: () -> Unit
) {
    Box(
        modifier = modifier
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
                    }
                },
                update = { view ->
                    view.player = playerForUi
                    // 只在播放时保持屏幕常亮，暂停后交还给系统
                    view.keepScreenOn = playerForUi.isPlaying
                },
                onRelease = { view ->
                    view.player = null
                }
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LibraryMusic,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = stringResource(
                        if (playerUiState.canShowVideo) {
                            R.string.video_not_available
                        } else {
                            R.string.audio_only_hint
                        }
                    ),
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // 覆盖一层点击区，保证 PlayerView 不会吃掉点击事件
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onTogglePlayPause)
        )

        if (playerUiState.canShowVideo && !playerUiState.isPlaying) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.start_playback),
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerDialog(
    playerUiState: PlayerUiState,
    customMinutes: String,
    onCustomMinutesChange: (String) -> Unit,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.sleep_timer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(15, 30, 60).forEach { minutes ->
                        FilterChip(
                            selected = false,
                            onClick = { onStartTimer(minutes) },
                            label = { Text(text = "$minutes 分钟") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = onCustomMinutesChange,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.sleep_timer_custom_minutes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    FilledIconButton(
                        onClick = { onStartTimer(customMinutes.toIntOrNull() ?: 0) },
                        enabled = (customMinutes.toIntOrNull() ?: 0) > 0
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.sleep_timer_start),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = if (playerUiState.isSleepTimerRunning) {
                        stringResource(
                            R.string.sleep_timer_running,
                            formatCountdown(playerUiState.sleepTimerRemainingMs)
                        )
                    } else {
                        stringResource(R.string.sleep_timer_not_set)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dismiss_message))
            }
        },
        dismissButton = {
            if (playerUiState.isSleepTimerRunning) {
                TextButton(onClick = onCancelTimer) {
                    Text(text = stringResource(R.string.sleep_timer_cancel))
                }
            }
        }
    )
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
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall
            )
            CompactIconButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.dismiss_message),
                onClick = onDismiss
            )
        }
    }
}

/**
 * 统一的紧凑图标按钮。
 * Material3 的 IconButton 有 48dp 最小触控区，列表里几个并排会挤掉文件名，
 * 这里用 38dp 的圆形点击区换取密度，图标语义通过 contentDescription 保留。
 */
@Composable
private fun CompactIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 38.dp,
    iconSize: Dp = 20.dp
) {
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (enabled) tint else tint.copy(alpha = 0.38f)
        )
    }
}

private fun repeatModeIcon(mode: PlaybackRepeatMode): ImageVector {
    return when (mode) {
        PlaybackRepeatMode.ONE -> Icons.Filled.RepeatOne
        PlaybackRepeatMode.OFF, PlaybackRepeatMode.ALL -> Icons.Filled.Repeat
    }
}

/**
 * 空列表提示。以前不管什么原因都显示“请先授权”，
 * 用户授权了空目录后会以为没读到，这里按扫描结果分别说明。
 */
@Composable
private fun libraryEmptyMessage(
    category: LibraryCategory,
    scanState: LibraryScanState
): String {
    val unnamed = stringResource(R.string.directory_unnamed)
    return when (scanState) {
        LibraryScanState.NoDirectory -> stringResource(
            if (category == LibraryCategory.SOURCE) {
                R.string.source_scan_no_directory
            } else {
                R.string.output_scan_no_directory
            }
        )

        is LibraryScanState.Failed -> stringResource(R.string.scan_failed_hint)

        is LibraryScanState.Ready -> {
            val name = scanState.directoryName ?: unnamed
            when {
                scanState.fileCount == 0 -> stringResource(R.string.scan_empty_folder, name)
                scanState.itemCount == 0 && category == LibraryCategory.SOURCE ->
                    stringResource(R.string.scan_no_video, name, scanState.fileCount)
                scanState.itemCount == 0 ->
                    stringResource(R.string.scan_no_audio, name, scanState.fileCount)
                else -> stringResource(
                    if (category == LibraryCategory.SOURCE) {
                        R.string.source_list_empty
                    } else {
                        R.string.extracted_list_empty
                    }
                )
            }
        }
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
