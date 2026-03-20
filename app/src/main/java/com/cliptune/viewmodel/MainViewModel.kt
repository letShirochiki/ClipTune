package com.cliptune.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.Statistics
import com.cliptune.data.db.ClipTuneDatabase
import com.cliptune.data.repository.DirectoryRepository
import com.cliptune.data.repository.MediaLibraryRepository
import com.cliptune.model.ConversionState
import com.cliptune.model.DirectoryConfig
import com.cliptune.model.LibraryCategory
import com.cliptune.model.LibraryMediaItem
import com.cliptune.model.MediaKind
import com.cliptune.model.PlaybackRepeatMode
import com.cliptune.model.PlayerUiState
import com.cliptune.service.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val database = ClipTuneDatabase.getInstance(appContext)
    private val directoryRepository = DirectoryRepository(database.directoryConfigDao())
    private val mediaLibraryRepository = MediaLibraryRepository(appContext, database.mediaItemDao())
    private val syncMutex = Mutex()

    private val _directoryConfig = MutableStateFlow(DirectoryConfig())
    val directoryConfig: StateFlow<DirectoryConfig> = _directoryConfig.asStateFlow()

    private val _sourceItems = MutableStateFlow<List<LibraryMediaItem>>(emptyList())
    val sourceItems: StateFlow<List<LibraryMediaItem>> = _sourceItems.asStateFlow()

    private val _extractedItems = MutableStateFlow<List<LibraryMediaItem>>(emptyList())
    val extractedItems: StateFlow<List<LibraryMediaItem>> = _extractedItems.asStateFlow()

    private val _conversionState = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val conversionState: StateFlow<ConversionState> = _conversionState.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val _playerUiState = MutableStateFlow(PlayerUiState())
    val playerUiState: StateFlow<PlayerUiState> = _playerUiState.asStateFlow()

    private val _playerForUi = MutableStateFlow<Player?>(null)
    val playerForUi: StateFlow<Player?> = _playerForUi.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? =
        null
    private var positionUpdateJob: Job? = null
    private var sleepTimerJob: Job? = null

    private var activeCategory: LibraryCategory? = null
    private var repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.ALL
    private var sleepTimerEndAtMs: Long? = null
    private var isFullPlayerVisible: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishPlayerState()
        }
    }

    init {
        observeDirectoryConfig()
        observeMediaLists()
    }

    fun connectPlayer(context: Context) {
        if (controller != null || controllerFuture != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { mediaController ->
                        controller = mediaController
                        _playerForUi.value = mediaController
                        mediaController.repeatMode = repeatMode.toPlayerRepeatMode()
                        mediaController.addListener(playerListener)
                        startPositionPolling()
                        publishPlayerState()
                    }
                    .onFailure {
                        _uiMessage.value = "播放器连接失败：${it.message}"
                        _playerForUi.value = null
                        publishPlayerState()
                    }
                controllerFuture = null
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun onSourceDirectorySelected(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val sourceUri = uri.toString()
            runCatching {
                // TODO(Android9): 在 Android 9 设备上，OpenDocumentTree 选择目录后偶发闪退，需补充真实堆栈后做兼容修复。
                takePersistablePermission(context, uri)
                directoryRepository.updateSourceTreeUri(sourceUri)
                _directoryConfig.value = _directoryConfig.value.copy(sourceTreeUri = sourceUri)
                refreshLibrariesWith(sourceUri, _directoryConfig.value.outputTreeUri)
            }.onFailure {
                _uiMessage.value = it.message ?: "更新原始目录失败"
            }
        }
    }

    fun onOutputDirectorySelected(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val outputUri = uri.toString()
            runCatching {
                // TODO(Android9): 在 Android 9 设备上，OpenDocumentTree 选择目录后偶发闪退，需补充真实堆栈后做兼容修复。
                takePersistablePermission(context, uri)
                directoryRepository.updateOutputTreeUri(outputUri)
                _directoryConfig.value = _directoryConfig.value.copy(outputTreeUri = outputUri)
                refreshLibrariesWith(_directoryConfig.value.sourceTreeUri, outputUri)
            }.onFailure {
                _uiMessage.value = it.message ?: "更新输出目录失败"
            }
        }
    }

    fun refreshLibraries() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshLibrariesWith(
                sourceTreeUri = _directoryConfig.value.sourceTreeUri,
                outputTreeUri = _directoryConfig.value.outputTreeUri
            )
        }
    }

    fun extractToMp3(item: LibraryMediaItem) {
        startExtraction(item, ExtractionTarget.MP3)
    }

    fun extractOriginalAudio(item: LibraryMediaItem) {
        startExtraction(item, ExtractionTarget.ORIGINAL)
    }

    fun deleteMediaItem(item: LibraryMediaItem) {
        viewModelScope.launch(Dispatchers.IO) {
            mediaLibraryRepository.deleteMediaItem(item)
                .onSuccess {
                    when (item.mediaKind) {
                        MediaKind.SOURCE_VIDEO -> refreshSourceLibrary(_directoryConfig.value.sourceTreeUri)
                        MediaKind.EXTRACTED_MP3, MediaKind.EXTRACTED_ORIGINAL -> {
                            refreshOutputLibrary(_directoryConfig.value.outputTreeUri)
                        }
                    }
                    _uiMessage.value = "已删除：${item.displayName}"
                }
                .onFailure {
                    _uiMessage.value = it.message ?: "删除失败"
                }
        }
    }

    fun playItem(category: LibraryCategory, startItemUri: String) {
        val mediaController = controller ?: run {
            _uiMessage.value = "播放器尚未就绪"
            return
        }

        val queue = when (category) {
            LibraryCategory.SOURCE -> _sourceItems.value
            LibraryCategory.EXTRACTED -> _extractedItems.value
        }
        if (queue.isEmpty()) {
            _uiMessage.value = "当前列表为空，无法播放"
            return
        }

        val startIndex = queue.indexOfFirst { it.uri == startItemUri }
        if (startIndex < 0) {
            _uiMessage.value = "未找到要播放的文件"
            return
        }

        val mediaItems = queue.map { item ->
            MediaItem.Builder()
                .setUri(item.uri)
                .setMediaId(item.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.displayName)
                        .build()
                )
                .build()
        }

        activeCategory = category
        mediaController.repeatMode = repeatMode.toPlayerRepeatMode()
        mediaController.setMediaItems(mediaItems, startIndex, 0L)
        mediaController.prepare()
        mediaController.play()
        publishPlayerState()
    }

    fun togglePlayPause() {
        val mediaController = controller ?: return
        if (mediaController.isPlaying) {
            mediaController.pause()
        } else {
            mediaController.play()
        }
        publishPlayerState()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        publishPlayerState()
    }

    fun seekToNext() {
        controller?.seekToNextMediaItem()
        publishPlayerState()
    }

    fun seekToPrevious() {
        controller?.seekToPreviousMediaItem()
        publishPlayerState()
    }

    fun setVolume(volume: Float) {
        controller?.volume = volume.coerceIn(0f, 1f)
        publishPlayerState()
    }

    fun stopPlayback() {
        controller?.stop()
        publishPlayerState()
    }

    fun setRepeatMode(targetMode: PlaybackRepeatMode) {
        repeatMode = targetMode
        controller?.repeatMode = targetMode.toPlayerRepeatMode()
        publishPlayerState()
    }

    fun cycleRepeatMode() {
        val nextMode = when (repeatMode) {
            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.OFF
            PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ALL
        }
        setRepeatMode(nextMode)
    }

    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            _uiMessage.value = "定时关闭时长必须大于 0 分钟"
            return
        }

        val durationMs = minutes * 60_000L
        sleepTimerEndAtMs = System.currentTimeMillis() + durationMs

        sleepTimerJob?.cancel()
        sleepTimerJob = viewModelScope.launch {
            while (true) {
                val remainingMs = currentSleepTimerRemainingMs()
                if (remainingMs <= 0L) {
                    sleepTimerEndAtMs = null
                    controller?.pause()
                    _uiMessage.value = "定时关闭已触发，播放已暂停"
                    publishPlayerState()
                    break
                }
                publishPlayerState()
                delay(500L)
            }
        }

        _uiMessage.value = "已设置定时关闭：$minutes 分钟"
        publishPlayerState()
    }

    fun cancelSleepTimer() {
        sleepTimerEndAtMs = null
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiMessage.value = "已取消定时关闭"
        publishPlayerState()
    }

    fun openFullPlayer() {
        isFullPlayerVisible = true
        publishPlayerState()
    }

    fun closeFullPlayer() {
        isFullPlayerVisible = false
        publishPlayerState()
    }

    fun clearMessage() {
        _uiMessage.value = null
    }

    private fun observeDirectoryConfig() {
        viewModelScope.launch {
            directoryRepository.observeConfig().collectLatest { config ->
                _directoryConfig.value = config
                refreshLibrariesWith(config.sourceTreeUri, config.outputTreeUri)
            }
        }
    }

    private fun observeMediaLists() {
        viewModelScope.launch {
            mediaLibraryRepository.observeSourceItems().collectLatest {
                _sourceItems.value = it
            }
        }
        viewModelScope.launch {
            mediaLibraryRepository.observeExtractedItems().collectLatest {
                _extractedItems.value = it
            }
        }
    }

    private fun startExtraction(item: LibraryMediaItem, target: ExtractionTarget) {
        viewModelScope.launch(Dispatchers.IO) {
            val outputTreeUri = _directoryConfig.value.outputTreeUri
            if (outputTreeUri.isNullOrBlank()) {
                _conversionState.value = ConversionState.Error("请先选择输出目录")
                return@launch
            }

            val outputRoot = DocumentFile.fromTreeUri(appContext, Uri.parse(outputTreeUri))
            if (outputRoot == null || !outputRoot.canWrite()) {
                _conversionState.value = ConversionState.Error("输出目录不可写，请重新选择目录")
                return@launch
            }

            _conversionState.value = ConversionState.Converting(0f)
            val inputFile = File(appContext.cacheDir, "extract_input_${System.nanoTime()}.tmp")
            val outputExt = if (target == ExtractionTarget.MP3) "mp3" else "m4a"
            val outputFile =
                File(appContext.cacheDir, "extract_output_${System.nanoTime()}.$outputExt")

            try {
                copyUriToFile(appContext, Uri.parse(item.uri), inputFile)
                val command = buildCommand(target, inputFile, outputFile)

                executeFfmpeg(
                    command = command,
                    totalDurationMs = item.durationMs.takeIf { it > 0L }
                ).getOrThrow()

                val baseName = item.displayName.substringBeforeLast('.')
                val targetMime = if (target == ExtractionTarget.MP3) "audio/mpeg" else "audio/mp4"
                val destination = createUniqueOutputFile(
                    directory = outputRoot,
                    baseName = baseName,
                    extension = outputExt,
                    mimeType = targetMime
                ) ?: throw IllegalStateException("无法在输出目录创建文件")

                copyFileToUri(appContext, outputFile, destination.uri)

                val finalSize = destination.length().coerceAtLeast(0L)
                val finalDuration = queryDuration(destination.uri) ?: 0L
                val mediaKind =
                    if (target == ExtractionTarget.MP3) {
                        MediaKind.EXTRACTED_MP3
                    } else {
                        MediaKind.EXTRACTED_ORIGINAL
                    }
                mediaLibraryRepository.upsertExtractedRecord(
                    uri = destination.uri,
                    displayName = destination.name ?: "${baseName}.$outputExt",
                    sizeBytes = finalSize,
                    durationMs = finalDuration,
                    mediaKind = mediaKind
                )

                refreshOutputLibrary(_directoryConfig.value.outputTreeUri)
                _conversionState.value = ConversionState.Success(destination.uri)
                _uiMessage.value = "提取完成：${destination.name}"
            } catch (e: Throwable) {
                val message = e.message ?: "音轨提取失败"
                _conversionState.value = ConversionState.Error(message)
                _uiMessage.value = message
            } finally {
                inputFile.delete()
                outputFile.delete()
            }
        }
    }

    private fun buildCommand(target: ExtractionTarget, inputFile: File, outputFile: File): String {
        return if (target == ExtractionTarget.MP3) {
            buildString {
                append("-y ")
                append("-i ${inputFile.absolutePath} ")
                append("-vn ")
                append("-acodec libmp3lame ")
                append("-ar 44100 ")
                append("-b:a 128k ")
                append(outputFile.absolutePath)
            }
        } else {
            buildString {
                append("-y ")
                append("-i ${inputFile.absolutePath} ")
                append("-vn ")
                append("-acodec copy ")
                append(outputFile.absolutePath)
            }
        }
    }

    private suspend fun executeFfmpeg(
        command: String,
        totalDurationMs: Long?
    ): Result<Unit> = suspendCoroutine { continuation ->
        FFmpegKit.executeAsync(
            command,
            { session ->
                val returnCode = session.returnCode
                if (returnCode.isValueSuccess) {
                    continuation.resume(Result.success(Unit))
                } else {
                    continuation.resume(
                        Result.failure(
                            IllegalStateException("FFmpeg 执行失败，错误码：$returnCode")
                        )
                    )
                }
            },
            { _ -> },
            { statistics: Statistics ->
                if (totalDurationMs != null && totalDurationMs > 0) {
                    val progress =
                        (statistics.time.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    _conversionState.value = ConversionState.Converting(progress)
                }
            }
        )
    }

    private fun createUniqueOutputFile(
        directory: DocumentFile,
        baseName: String,
        extension: String,
        mimeType: String
    ): DocumentFile? {
        var index = 0
        while (index < 500) {
            val candidateName = if (index == 0) {
                "$baseName.$extension"
            } else {
                "$baseName ($index).$extension"
            }
            if (directory.findFile(candidateName) == null) {
                return directory.createFile(mimeType, candidateName)
            }
            index++
        }
        return null
    }

    private suspend fun refreshLibrariesWith(sourceTreeUri: String?, outputTreeUri: String?) {
        syncMutex.withLock {
            _isSyncing.value = true
            try {
                refreshSourceLibrary(sourceTreeUri)
                refreshOutputLibrary(outputTreeUri)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun refreshSourceLibrary(sourceTreeUri: String?) {
        mediaLibraryRepository.refreshSourceLibrary(sourceTreeUri)
            .onFailure {
                _uiMessage.value = it.message ?: "扫描原始目录失败"
            }
    }

    private suspend fun refreshOutputLibrary(outputTreeUri: String?) {
        mediaLibraryRepository.refreshOutputLibrary(outputTreeUri)
            .onFailure {
                _uiMessage.value = it.message ?: "扫描输出目录失败"
            }
    }

    private fun startPositionPolling() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (true) {
                publishPlayerState()
                delay(500L)
            }
        }
    }

    private fun publishPlayerState() {
        val mediaController = controller
        val sleepRemainingMs = currentSleepTimerRemainingMs()
        val isTimerRunning = sleepTimerEndAtMs != null && sleepRemainingMs > 0L

        if (mediaController == null) {
            _playerUiState.value = PlayerUiState(
                connected = false,
                repeatMode = repeatMode,
                sleepTimerRemainingMs = sleepRemainingMs,
                isSleepTimerRunning = isTimerRunning,
                isFullPlayerVisible = isFullPlayerVisible
            )
            return
        }

        repeatMode = mediaController.repeatMode.toRepeatMode()

        val duration = mediaController.duration.let {
            if (it == C.TIME_UNSET || it < 0L) 0L else it
        }
        val position = mediaController.currentPosition.coerceAtLeast(0L)
        val title = mediaController.mediaMetadata.title?.toString()
            ?: mediaController.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: ""
        val canShowVideo = activeCategory == LibraryCategory.SOURCE &&
            mediaController.currentMediaItem != null

        _playerUiState.value = PlayerUiState(
            connected = true,
            currentTitle = title,
            isPlaying = mediaController.isPlaying,
            positionMs = if (duration > 0) position.coerceAtMost(duration) else position,
            durationMs = duration,
            volume = mediaController.volume.coerceIn(0f, 1f),
            canSkipPrevious = mediaController.hasPreviousMediaItem(),
            canSkipNext = mediaController.hasNextMediaItem(),
            activeCategory = activeCategory,
            repeatMode = repeatMode,
            sleepTimerRemainingMs = sleepRemainingMs,
            isSleepTimerRunning = isTimerRunning,
            canShowVideo = canShowVideo,
            isFullPlayerVisible = isFullPlayerVisible
        )
    }

    private fun currentSleepTimerRemainingMs(): Long {
        val endAt = sleepTimerEndAtMs ?: return 0L
        return (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun takePersistablePermission(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }.onFailure {
            _uiMessage.value = "目录授权持久化失败：${it.message}"
        }
    }

    private fun queryDuration(uri: Uri): Long? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(appContext, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            duration?.toLongOrNull()
        }.getOrNull()
    }

    @Throws(IOException::class)
    private fun copyUriToFile(context: Context, sourceUri: Uri, destination: File) {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("无法打开输入文件流")
    }

    @Throws(IOException::class)
    private fun copyFileToUri(context: Context, sourceFile: File, targetUri: Uri) {
        context.contentResolver.openOutputStream(targetUri, "w")?.use { output ->
            FileInputStream(sourceFile).use { input ->
                input.copyTo(output)
            }
        } ?: throw IOException("无法打开输出文件流")
    }

    override fun onCleared() {
        positionUpdateJob?.cancel()
        sleepTimerJob?.cancel()

        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        _playerForUi.value = null

        controllerFuture?.let {
            MediaController.releaseFuture(it)
            controllerFuture = null
        }
        super.onCleared()
    }

    private enum class ExtractionTarget {
        MP3,
        ORIGINAL
    }

    private fun PlaybackRepeatMode.toPlayerRepeatMode(): Int {
        return when (this) {
            PlaybackRepeatMode.OFF -> Player.REPEAT_MODE_OFF
            PlaybackRepeatMode.ONE -> Player.REPEAT_MODE_ONE
            PlaybackRepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }

    private fun Int.toRepeatMode(): PlaybackRepeatMode {
        return when (this) {
            Player.REPEAT_MODE_OFF -> PlaybackRepeatMode.OFF
            Player.REPEAT_MODE_ONE -> PlaybackRepeatMode.ONE
            Player.REPEAT_MODE_ALL -> PlaybackRepeatMode.ALL
            else -> PlaybackRepeatMode.ALL
        }
    }
}
