package com.cliptune.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.cliptune.data.db.MediaItemDao
import com.cliptune.data.db.MediaItemEntity
import com.cliptune.model.DirectoryDisplay
import com.cliptune.model.LibraryMediaItem
import com.cliptune.model.LibraryScanState
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

    /**
     * 扫描原始视频目录。
     *
     * 失败不再静默返回 0：授权失效、目录被删、没有读权限都会返回 [LibraryScanState.Failed]，
     * 这样界面才能区分“目录里确实没有视频”和“根本没读到目录”。
     */
    suspend fun scanSourceLibrary(sourceTreeUri: String?): LibraryScanState {
        val directoryName = DirectoryDisplay.nameOf(sourceTreeUri)
        if (sourceTreeUri.isNullOrBlank()) {
            mediaItemDao.deleteByKinds(SOURCE_KINDS)
            return LibraryScanState.NoDirectory
        }

        return try {
            val root = openTree(sourceTreeUri, "原始")
            val files = collectFiles(root)
            val now = System.currentTimeMillis()
            val entities = files.filter { isSourceVideo(it) }.mapNotNull { file ->
                file.toEntity(
                    mediaKind = MediaKind.SOURCE_VIDEO,
                    lastScannedAt = now,
                    sourceTag = SOURCE_TAG_SCAN
                )
            }

            mediaItemDao.deleteByKinds(SOURCE_KINDS)
            if (entities.isNotEmpty()) {
                mediaItemDao.upsertAll(entities)
            }
            LibraryScanState.Ready(directoryName, entities.size, files.size)
        } catch (e: DirectoryUnreadableException) {
            LibraryScanState.Failed(directoryName, e.message.orEmpty(), e.permissionLost)
        } catch (e: SecurityException) {
            LibraryScanState.Failed(directoryName, "没有该目录的访问权限，请重新选择目录", true)
        } catch (e: Exception) {
            LibraryScanState.Failed(directoryName, e.message ?: "扫描原始目录失败", false)
        }
    }

    /** 扫描提取输出目录，语义与 [scanSourceLibrary] 一致。 */
    suspend fun scanOutputLibrary(outputTreeUri: String?): LibraryScanState {
        val directoryName = DirectoryDisplay.nameOf(outputTreeUri)
        if (outputTreeUri.isNullOrBlank()) {
            mediaItemDao.deleteByKinds(OUTPUT_KINDS)
            return LibraryScanState.NoDirectory
        }

        return try {
            val root = openTree(outputTreeUri, "输出")
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

            mediaItemDao.deleteByKinds(OUTPUT_KINDS)
            if (entities.isNotEmpty()) {
                mediaItemDao.upsertAll(entities)
            }
            LibraryScanState.Ready(directoryName, entities.size, files.size)
        } catch (e: DirectoryUnreadableException) {
            LibraryScanState.Failed(directoryName, e.message.orEmpty(), e.permissionLost)
        } catch (e: SecurityException) {
            LibraryScanState.Failed(directoryName, "没有该目录的访问权限，请重新选择目录", true)
        } catch (e: Exception) {
            LibraryScanState.Failed(directoryName, e.message ?: "扫描输出目录失败", false)
        }
    }

    /**
     * 打开已授权目录。SAF 授权失效时 [DocumentFile.fromTreeUri] 不会抛异常也不会返回 null，
     * 只是 exists()/canRead() 变成 false，这里显式检查，避免被当成“空目录”。
     */
    private fun openTree(treeUri: String, label: String): DocumentFile {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: throw DirectoryUnreadableException("无法解析${label}目录的授权", true)
        val readable = runCatching { root.exists() && root.canRead() }
            .getOrElse { throw DirectoryUnreadableException("${label}目录没有读取权限", true) }
        if (!readable) {
            throw DirectoryUnreadableException(
                "${label}目录不存在或授权已失效，请重新选择目录",
                true
            )
        }
        return root
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
            if (!node.isDirectory) {
                if (node.isFile) output += node
                return
            }
            // 单个子目录读不到不应该中断整次扫描（例如子目录授权异常）
            val children = runCatching { node.listFiles() }.getOrDefault(emptyArray())
            children.forEach { child -> runCatching { walk(child) } }
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

        private val SOURCE_KINDS = listOf(MediaKind.SOURCE_VIDEO.name)
        private val OUTPUT_KINDS = listOf(
            MediaKind.EXTRACTED_MP3.name,
            MediaKind.EXTRACTED_ORIGINAL.name
        )

        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mov", "mkv", "avi", "webm", "3gp", "m4v", "ts"
        )

        private val AUDIO_EXTENSIONS = setOf(
            "aac", "m4a", "wav", "flac", "ogg", "opus", "amr", "mpga", "mp2"
        )
    }
}

/** 目录本身打不开（授权失效、被删除、无权限），与“目录能开但没内容”区分开 */
private class DirectoryUnreadableException(
    message: String,
    val permissionLost: Boolean
) : IllegalStateException(message)
