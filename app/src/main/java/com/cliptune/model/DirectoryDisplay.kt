package com.cliptune.model

import android.net.Uri
import android.provider.DocumentsContract

/**
 * 把 SAF 的 tree uri 转成人能看懂的目录名。
 *
 * 例如：
 * content://com.android.externalstorage.documents/tree/primary%3AMovies      -> Movies
 * content://com.android.externalstorage.documents/tree/primary%3AMovies%2FSub -> Movies/Sub
 * content://com.android.externalstorage.documents/tree/primary%3A             -> 内部存储根目录
 */
object DirectoryDisplay {

    private const val ROOT_DIRECTORY_NAME = "内部存储根目录"

    fun nameOf(treeUri: String?): String? {
        if (treeUri.isNullOrBlank()) return null

        val uri = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return null
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: uri.lastPathSegment
            ?: return null

        val decoded = Uri.decode(documentId)
        val parts = decoded.split(':', '/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null

        return when {
            parts.size == 1 && parts[0].equals("primary", ignoreCase = true) -> ROOT_DIRECTORY_NAME
            parts.size >= 2 -> parts.drop(1).joinToString("/")
            else -> parts[0]
        }
    }
}
