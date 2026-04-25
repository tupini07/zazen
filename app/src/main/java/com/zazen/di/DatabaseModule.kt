package com.zazen.di

import android.content.Context
import androidx.room.Room
import com.zazen.data.db.SessionDao
import com.zazen.data.db.ZazenDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): ZazenDatabase {
        return Room.databaseBuilder(
            context,
            ZazenDatabase::class.java,
            "zazen-db",
        ).build()
    }

    @Provides
    fun provideSessionDao(db: ZazenDatabase): SessionDao = db.sessionDao()
}
