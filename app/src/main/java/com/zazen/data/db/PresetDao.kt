package com.zazen.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.zazen.data.model.Preset
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Insert
    suspend fun insert(preset: Preset): Long

    @Delete
    suspend fun delete(preset: Preset)

    @Query("SELECT * FROM presets ORDER BY name ASC")
    fun getAll(): Flow<List<Preset>>
}
