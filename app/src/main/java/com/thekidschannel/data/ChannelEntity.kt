package com.thekidschannel.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val sortOrder: Int,
    val currentVideoUri: String? = null,
    val currentVideoIndex: Int = 0,
    val positionMs: Long = 0,
)
