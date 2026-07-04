package com.zazen.data.repository

import com.zazen.data.db.PresetDao
import com.zazen.data.model.Preset
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresetRepository @Inject constructor(
    private val presetDao: PresetDao,
) {
    fun getAll(): Flow<List<Preset>> = presetDao.getAll()

    suspend fun save(preset: Preset): Long = presetDao.insert(preset)

    suspend fun delete(preset: Preset) = presetDao.delete(preset)

    /** Replaces all stored presets with [presets], used when restoring a backup. */
    suspend fun replaceAll(presets: List<Preset>) {
        presetDao.deleteAll()
        if (presets.isNotEmpty()) presetDao.insertAll(presets)
    }
}
