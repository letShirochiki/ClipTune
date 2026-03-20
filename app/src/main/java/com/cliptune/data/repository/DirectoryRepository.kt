package com.cliptune.data.repository

import com.cliptune.data.db.DirectoryConfigDao
import com.cliptune.model.DirectoryConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DirectoryRepository(
    private val dao: DirectoryConfigDao
) {
    fun observeConfig(): Flow<DirectoryConfig> {
        return dao.observeConfig().map { entity ->
            DirectoryConfig(
                sourceTreeUri = entity?.sourceTreeUri,
                outputTreeUri = entity?.outputTreeUri
            )
        }
    }

    suspend fun updateSourceTreeUri(uri: String?) {
        dao.updateSourceTreeUriAtomic(uri)
    }

    suspend fun updateOutputTreeUri(uri: String?) {
        dao.updateOutputTreeUriAtomic(uri)
    }
}
