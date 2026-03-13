package com.pocketnoc.di

import android.content.Context
import com.pocketnoc.utils.SecurityNotificationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecurityNotificationManager(
        @ApplicationContext context: Context
    ): SecurityNotificationManager {
        return SecurityNotificationManager(context)
    }
}
