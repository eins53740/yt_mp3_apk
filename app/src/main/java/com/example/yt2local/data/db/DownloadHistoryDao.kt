package com.example.yt2local.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadHistoryDao {
    @Query("SELECT * FROM download_history ORDER BY timestamp DESC LIMIT 10")
    fun getRecent(): Flow<List<DownloadHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadHistoryEntity)

    @Query("DELETE FROM download_history WHERE id NOT IN (SELECT id FROM download_history ORDER BY timestamp DESC LIMIT 10)")
    suspend fun pruneOld()
}
