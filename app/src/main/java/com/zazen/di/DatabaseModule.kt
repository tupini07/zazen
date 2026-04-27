package com.zazen.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zazen.data.db.PresetDao
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

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS presets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    durationMinutes INTEGER NOT NULL,
                    vibrateOnly INTEGER NOT NULL DEFAULT 0,
                    bellVolume REAL NOT NULL DEFAULT 1.0,
                    endSoundName TEXT NOT NULL DEFAULT 'BELL',
                    dndEnabled INTEGER NOT NULL DEFAULT 0,
                    bells TEXT NOT NULL DEFAULT '[]'
                )""",
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Recreate presets table: replace durationMinutes with durationSeconds
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS presets_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    durationSeconds INTEGER NOT NULL,
                    vibrateOnly INTEGER NOT NULL DEFAULT 0,
                    bellVolume REAL NOT NULL DEFAULT 1.0,
                    endSoundName TEXT NOT NULL DEFAULT 'BELL',
                    dndEnabled INTEGER NOT NULL DEFAULT 0,
                    bells TEXT NOT NULL DEFAULT '[]'
                )"""
            )
            db.execSQL(
                """INSERT INTO presets_new (id, name, durationSeconds, vibrateOnly, bellVolume, endSoundName, dndEnabled, bells)
                   SELECT id, name, durationMinutes * 60, vibrateOnly, bellVolume, endSoundName, dndEnabled, bells
                   FROM presets"""
            )
            db.execSQL("DROP TABLE presets")
            db.execSQL("ALTER TABLE presets_new RENAME TO presets")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ZazenDatabase {
        return Room.databaseBuilder(
            context,
            ZazenDatabase::class.java,
            "zazen-db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }

    @Provides
    fun provideSessionDao(db: ZazenDatabase): SessionDao = db.sessionDao()

    @Provides
    fun providePresetDao(db: ZazenDatabase): PresetDao = db.presetDao()
}
