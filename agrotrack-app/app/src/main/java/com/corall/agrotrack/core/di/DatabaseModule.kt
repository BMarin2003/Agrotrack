package com.corall.agrotrack.core.di

import android.content.Context
import androidx.room.Room
import com.corall.agrotrack.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "agrotrack.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideSensorReadingDao(db: AppDatabase) = db.sensorReadingDao()
    @Provides fun provideAlertDao(db: AppDatabase)         = db.alertDao()
}
