package com.example.yt2local.di

import android.content.Context
import com.example.yt2local.VideoRepository
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
}
