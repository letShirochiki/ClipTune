package com.cliptune.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items WHERE mediaKind IN (:kinds) ORDER BY displayName COLLATE NOCASE ASC")
    fun observeByKinds(kinds: List<String>): Flow<List<MediaItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MediaItemEntity)

    @Query("DELETE FROM media_items WHERE mediaKind IN (:kinds)")
    suspend fun deleteByKinds(kinds: List<String>)

    @Query("DELETE FROM media_items WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)
}
