package com.pocketnoc.di

import android.content.Context
import androidx.room.Room
import com.pocketnoc.data.local.AppDatabase
import com.pocketnoc.data.local.dao.ServerDao
import com.pocketnoc.data.local.dao.AlertDao
import com.pocketnoc.data.local.AlertThresholdRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "pocket_noc_db"
        ).fallbackToDestructiveMigration()
         .build()
    }

    @Provides
    fun provideServerDao(database: AppDatabase): ServerDao {
        return database.serverDao()
    }

    @Provides
    fun provideAlertDao(database: AppDatabase): AlertDao {
        return database.alertDao()
    }

    @Provides
    @Singleton
    fun provideAlertThresholdRepository(@ApplicationContext context: Context): AlertThresholdRepository {
        return AlertThresholdRepository(context)
    }
}
