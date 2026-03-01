package com.example.yt2local.di

import android.content.Context
import androidx.room.Room
import com.example.yt2local.VideoRepository
import com.example.yt2local.data.DownloadStateHolder
import com.example.yt2local.data.db.AppDatabase
import com.example.yt2local.data.db.DownloadHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideVideoRepository(@ApplicationContext context: Context): VideoRepository {
        return VideoRepository(context)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "yt2local.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideDownloadHistoryDao(db: AppDatabase): DownloadHistoryDao {
        return db.downloadHistoryDao()
    }

    @Provides
    @Singleton
    fun provideDownloadStateHolder(): DownloadStateHolder {
        return DownloadStateHolder()
    }
}
