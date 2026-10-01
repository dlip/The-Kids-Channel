package com.thekidschannel.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "roots")
data class RootEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val watchTimeMs: Long = 0,
)
