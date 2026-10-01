package com.thekidschannel.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channel_stats",
    indices = [Index("rootUri")],
    foreignKeys = [ForeignKey(
        entity = RootEntity::class,
        parentColumns = ["uri"],
        childColumns = ["rootUri"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ChannelStatsEntity(
    @PrimaryKey val channelUri: String,
    val rootUri: String,
    val name: String,
    val watchTimeMs: Long = 0,
)
