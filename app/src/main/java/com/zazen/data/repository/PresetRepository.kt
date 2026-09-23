package com.zazen.data.repository

import com.zazen.data.db.PresetDao
import com.zazen.data.model.Preset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresetRepository @Inject constructor(
    private val presetDao: PresetDao,
) {
    private val _selectedPreset = MutableStateFlow<Preset?>(null)
    val selectedPreset: StateFlow<Preset?> = _selectedPreset.asStateFlow()

    fun getAll(): Flow<List<Preset>> = presetDao.getAll()

    fun select(preset: Preset) {
        _selectedPreset.value = preset
    }

    suspend fun save(preset: Preset): Long {
        val id = presetDao.insert(preset)
        _selectedPreset.value = preset.copy(id = id)
        return id
    }

    suspend fun update(preset: Preset): Int {
        val rows = presetDao.update(preset)
        if (rows == 1) _selectedPreset.value = preset
        else if (_selectedPreset.value?.id == preset.id) _selectedPreset.value = null
        return rows
    }

    suspend fun delete(preset: Preset) {
        presetDao.delete(preset)
        if (_selectedPreset.value?.id == preset.id) _selectedPreset.value = null
    }

    /** Replaces all stored presets with [presets], used when restoring a backup. */
    suspend fun replaceAll(presets: List<Preset>) {
        _selectedPreset.value = null
        presetDao.deleteAll()
        if (presets.isNotEmpty()) presetDao.insertAll(presets)
    }
}
