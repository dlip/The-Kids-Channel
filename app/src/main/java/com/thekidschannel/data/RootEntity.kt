package com.thekidschannel.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "roots")
data class RootEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val sortOrder: Int,
)
