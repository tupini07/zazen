package com.zazen.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zazen.data.model.MeditationSession
import com.zazen.data.model.Preset

@Database(
    entities = [MeditationSession::class, Preset::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(BellListConverter::class)
abstract class ZazenDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun presetDao(): PresetDao
}
