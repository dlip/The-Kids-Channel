package com.thekidschannel.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelStatsDao {
    @Query("SELECT * FROM channel_stats ORDER BY name, channelUri")
    fun observeAll(): Flow<List<ChannelStatsEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(channel: ChannelStatsEntity)

    @Query("UPDATE channel_stats SET name = :name WHERE channelUri = :channelUri")
    suspend fun updateName(channelUri: String, name: String)

    @Query("SELECT EXISTS(SELECT 1 FROM roots WHERE uri = :rootUri)")
    suspend fun rootExists(rootUri: String): Boolean

    @Transaction
    suspend fun rememberChannel(channel: ChannelStatsEntity) {
        if (!rootExists(channel.rootUri)) return
        insert(channel)
        updateName(channel.channelUri, channel.name)
    }

    @Query("UPDATE roots SET watchTimeMs = watchTimeMs + :elapsedMs WHERE uri = :rootUri")
    suspend fun addRootWatchTime(rootUri: String, elapsedMs: Long): Int

    @Query("UPDATE channel_stats SET watchTimeMs = watchTimeMs + :elapsedMs WHERE channelUri = :channelUri")
    suspend fun addChannelWatchTime(channelUri: String, elapsedMs: Long)

    @Transaction
    suspend fun recordWatchTime(channel: ChannelStatsEntity, elapsedMs: Long) {
        if (elapsedMs <= 0 || addRootWatchTime(channel.rootUri, elapsedMs) == 0) return
        insert(channel)
        updateName(channel.channelUri, channel.name)
        addChannelWatchTime(channel.channelUri, elapsedMs)
    }
}
