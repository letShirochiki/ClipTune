package com.cliptune.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DirectoryConfigDao {
    @Query("SELECT * FROM directory_config WHERE id = 1")
    fun observeConfig(): Flow<DirectoryConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DirectoryConfigEntity)

    @Query("INSERT OR IGNORE INTO directory_config(id, sourceTreeUri, outputTreeUri) VALUES (1, NULL, NULL)")
    suspend fun ensureConfigRow()

    @Query("UPDATE directory_config SET sourceTreeUri = :uri WHERE id = 1")
    suspend fun updateSourceTreeUri(uri: String?)

    @Query("UPDATE directory_config SET outputTreeUri = :uri WHERE id = 1")
    suspend fun updateOutputTreeUri(uri: String?)

    @Transaction
    suspend fun updateSourceTreeUriAtomic(uri: String?) {
        ensureConfigRow()
        updateSourceTreeUri(uri)
    }

    @Transaction
    suspend fun updateOutputTreeUriAtomic(uri: String?) {
        ensureConfigRow()
        updateOutputTreeUri(uri)
    }
}
