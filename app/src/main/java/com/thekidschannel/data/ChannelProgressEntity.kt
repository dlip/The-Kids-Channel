package com.thekidschannel.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channel_progress",
    indices = [Index("rootUri")],
)
data class ChannelProgressEntity(
    @PrimaryKey val channelUri: String,
    val rootUri: String,
    val currentVideoUri: String?,
    val currentVideoIndex: Int,
    val positionMs: Long,
)
