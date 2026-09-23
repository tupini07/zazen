package com.zazen.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zazen.data.model.Preset
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Insert
    suspend fun insert(preset: Preset): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(presets: List<Preset>)

    @Update
    suspend fun update(preset: Preset): Int

    @Delete
    suspend fun delete(preset: Preset)

    @Query("DELETE FROM presets")
    suspend fun deleteAll()

    @Query("SELECT * FROM presets ORDER BY name ASC")
    fun getAll(): Flow<List<Preset>>
}
