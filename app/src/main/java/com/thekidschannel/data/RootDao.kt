package com.thekidschannel.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RootDao {
    @Query("SELECT * FROM roots ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<RootEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM roots")
    suspend fun nextSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(root: RootEntity)

    @Query("DELETE FROM roots WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("UPDATE roots SET enabled = :enabled WHERE uri = :uri")
    suspend fun setEnabled(uri: String, enabled: Boolean)

    @Query("UPDATE roots SET watchTimeMs = watchTimeMs + :elapsedMs WHERE uri = :uri")
    suspend fun addWatchTime(uri: String, elapsedMs: Long)
}
