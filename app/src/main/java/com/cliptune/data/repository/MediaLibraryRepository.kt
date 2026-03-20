package com.cliptune.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.cliptune.data.db.MediaItemDao
import com.cliptune.data.db.MediaItemEntity
import com.cliptune.model.LibraryMediaItem
import com.cliptune.model.MediaKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

class MediaLibraryRepository(
    private val context: Context,
    private val mediaItemDao: MediaItemDao
) {
    fun observeSourceItems(): Flow<List<LibraryMediaItem>> {
        return mediaItemDao.observeByKinds(listOf(MediaKind.SOURCE_VIDEO.name))
            .map { entities -> entities.map { it.toModel() } }
    }

    fun observeExtractedItems(): Flow<List<LibraryMediaItem>> {
        return mediaItemDao.observeByKinds(
            listOf(MediaKind.EXTRACTED_MP3.name, MediaKind.EXTRACTED_ORIGINAL.name)
        ).map { entities -> entities.map { it.toModel() } }
    }

    suspend fun refreshSourceLibrary(sourceTreeUri: String?): Result<Int> = runCatching {
        val sourceKinds = listOf(MediaKind.SOURCE_VIDEO.name)
        if (sourceTreeUri.isNullOrBlank()) {
            mediaItemDao.deleteByKinds(sourceKinds)
            return@runCatching 0
        }

        val root = DocumentFile.fromTreeUri(context, Uri.parse(sourceTreeUri))
            ?: throw IllegalStateException("无法访问原始目录")
        val files = collectFiles(root).filter { isSourceVideo(it) }
        val now = System.currentTimeMillis()
        val entities = files.mapNotNull { file ->
            file.toEntity(
                mediaKind = MediaKind.SOURCE_VIDEO,
                lastScannedAt = now,
                sourceTag = SOURCE_TAG_SCAN
            )
        }

        mediaItemDao.deleteByKinds(sourceKinds)
        if (entities.isNotEmpty()) {
            mediaItemDao.upsertAll(entities)
        }
        entities.size
    }

    suspend fun refreshOutputLibrary(outputTreeUri: String?): Result<Int> = runCatching {
        val outputKinds = listOf(MediaKind.EXTRACTED_MP3.name, MediaKind.EXTRACTED_ORIGINAL.name)
        if (outputTreeUri.isNullOrBlank()) {
            mediaItemDao.deleteByKinds(outputKinds)
            return@runCatching 0
        }

        val root = DocumentFile.fromTreeUri(context, Uri.parse(outputTreeUri))
            ?: throw IllegalStateException("无法访问输出目录")
        val files = collectFiles(root)
        val now = System.currentTimeMillis()
        val entities = files.mapNotNull { file ->
            val kind = classifyOutputKind(file) ?: return@mapNotNull null
            file.toEntity(
                mediaKind = kind,
                lastScannedAt = now,
                sourceTag = SOURCE_TAG_SCAN
            )
        }

        mediaItemDao.deleteByKinds(outputKinds)
        if (entities.isNotEmpty()) {
            mediaItemDao.upsertAll(entities)
        }
        entities.size
    }

    suspend fun upsertExtractedRecord(
        uri: Uri,
        displayName: String,
        sizeBytes: Long,
        durationMs: Long,
        mediaKind: MediaKind
    ) {
        mediaItemDao.upsert(
            MediaItemEntity(
                uri = uri.toString(),
                displayName = displayName,
                sizeBytes = sizeBytes,
                durationMs = durationMs,
                mediaKind = mediaKind.name,
                lastScannedAt = System.currentTimeMillis(),
                sourceTag = SOURCE_TAG_EXTRACT
            )
        )
    }

    suspend fun deleteMediaItem(item: LibraryMediaItem): Result<Unit> = runCatching {
        val uri = Uri.parse(item.uri)
        val deleted = DocumentFile.fromSingleUri(context, uri)?.delete()
            ?: (context.contentResolver.delete(uri, null, null) > 0)

        if (!deleted) {
            throw IllegalStateException("删除失败，文件可能不可访问或缺少目录授权。")
        }
        mediaItemDao.deleteByUri(item.uri)
    }

    private fun collectFiles(root: DocumentFile): List<DocumentFile> {
        val output = mutableListOf<DocumentFile>()

        fun walk(node: DocumentFile) {
            if (!node.exists()) return
            if (node.isFile) {
                output += node
                return
            }
            if (node.isDirectory) {
                node.listFiles().forEach { child -> walk(child) }
            }
        }

        walk(root)
        return output
    }

    private fun isSourceVideo(file: DocumentFile): Boolean {
        val type = file.type.orEmpty()
        if (type.startsWith("video/")) return true
        val ext = file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in VIDEO_EXTENSIONS
    }

    private fun classifyOutputKind(file: DocumentFile): MediaKind? {
        val ext = file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        val type = file.type.orEmpty()
        if (ext == "mp3") return MediaKind.EXTRACTED_MP3
        if (type.startsWith("audio/") || ext in AUDIO_EXTENSIONS) {
            return MediaKind.EXTRACTED_ORIGINAL
        }
        return null
    }

    private fun DocumentFile.toEntity(
        mediaKind: MediaKind,
        lastScannedAt: Long,
        sourceTag: String
    ): MediaItemEntity? {
        val uri = uri.toString()
        val displayName = name ?: return null
        val sizeBytes = length().coerceAtLeast(0L)
        val durationMs = queryDuration(uri = uri).coerceAtLeast(0L)

        return MediaItemEntity(
            uri = uri,
            displayName = displayName,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            mediaKind = mediaKind.name,
            lastScannedAt = lastScannedAt,
            sourceTag = sourceTag
        )
    }

    private fun queryDuration(uri: String): Long {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.parse(uri))
            val value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            value?.toLongOrNull() ?: 0L
        }.getOrDefault(0L)
    }

    private fun MediaItemEntity.toModel(): LibraryMediaItem {
        return LibraryMediaItem(
            uri = uri,
            displayName = displayName,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            mediaKind = MediaKind.valueOf(mediaKind),
            lastScannedAt = lastScannedAt
        )
    }

    companion object {
        private const val SOURCE_TAG_SCAN = "scan"
        private const val SOURCE_TAG_EXTRACT = "extract"

        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mov", "mkv", "avi", "webm", "3gp", "m4v", "ts"
        )

        private val AUDIO_EXTENSIONS = setOf(
            "aac", "m4a", "wav", "flac", "ogg", "opus", "amr", "mpga", "mp2"
        )
    }
}
