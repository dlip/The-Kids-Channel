package com.thekidschannel.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChannelProgressDao {
    @Query("SELECT * FROM channel_progress WHERE channelUri = :channelUri")
    suspend fun get(channelUri: String): ChannelProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(progress: ChannelProgressEntity)

    @Query("SELECT channelUri FROM channel_progress WHERE rootUri = :rootUri")
    suspend fun getChannelUrisForRoot(rootUri: String): List<String>

    @Query("DELETE FROM channel_progress WHERE rootUri = :rootUri")
    suspend fun deleteForRoot(rootUri: String)
}
