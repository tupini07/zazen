package com.zazen.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zazen.data.model.MeditationSession

@Database(entities = [MeditationSession::class], version = 1, exportSchema = false)
abstract class ZazenDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}
