package com.thekidschannel.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<ChannelEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM channels")
    suspend fun nextSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(channel: ChannelEntity)

    @Query("DELETE FROM channels WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query(
        """
        UPDATE channels
        SET currentVideoUri = :videoUri,
            currentVideoIndex = :videoIndex,
            positionMs = :positionMs
        WHERE uri = :channelUri
        """,
    )
    suspend fun updateProgress(
        channelUri: String,
        videoUri: String,
        videoIndex: Int,
        positionMs: Long,
    )
}
