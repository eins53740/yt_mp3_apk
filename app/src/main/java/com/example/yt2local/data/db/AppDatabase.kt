package com.example.yt2local.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DownloadHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadHistoryDao(): DownloadHistoryDao
}
