package com.zazen.ui.screens.setup

import com.zazen.data.model.IntervalBell
import com.zazen.data.model.Preset
import com.zazen.data.model.Sound
import com.zazen.data.model.TimerConfig
import com.zazen.data.repository.PreferencesRepository
import com.zazen.data.repository.PresetRepository
import com.zazen.service.SoundPlayer
import com.zazen.service.TimerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val timerManager: TimerManager,
    private val prefs: PreferencesRepository,
    private val presetRepository: PresetRepository,
    private val soundPlayer: SoundPlayer,
) : ViewModel() {

    private val _durationMinutes = MutableStateFlow(prefs.durationMinutes)
    val durationMinutes: StateFlow<Int> = _durationMinutes.asStateFlow()

    private val _vibrateOnly = MutableStateFlow(prefs.vibrateOnly)
    val vibrateOnly: StateFlow<Boolean> = _vibrateOnly.asStateFlow()

    private val _bellVolume = MutableStateFlow(prefs.bellVolume)
    val bellVolume: StateFlow<Float> = _bellVolume.asStateFlow()

    private val _endSound = MutableStateFlow(
        Sound.entries.find { it.name == prefs.endSoundName } ?: Sound.DEFAULT
    )
    val endSound: StateFlow<Sound> = _endSound.asStateFlow()

    private val _dndEnabled = MutableStateFlow(prefs.dndEnabled)
    val dndEnabled: StateFlow<Boolean> = _dndEnabled.asStateFlow()

    private val _bells = MutableStateFlow<List<IntervalBell>>(emptyList())
    val bells: StateFlow<List<IntervalBell>> = _bells.asStateFlow()

    val presets = presetRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val themeMode: StateFlow<String> = prefs.themeModeFlow

    init {
        // Preload all sounds for instant preview
        soundPlayer.preloadAll()
    }

    fun setDuration(minutes: Int) {
        _durationMinutes.value = minutes.coerceIn(1, 240)
        prefs.durationMinutes = _durationMinutes.value
    }

    fun incrementDuration() = setDuration(_durationMinutes.value + 5)
    fun decrementDuration() = setDuration(_durationMinutes.value - 5)

    fun toggleVibrateOnly() {
        _vibrateOnly.value = !_vibrateOnly.value
        prefs.vibrateOnly = _vibrateOnly.value
    }

    fun setBellVolume(volume: Float) {
        _bellVolume.value = volume.coerceIn(0f, 1f)
        prefs.bellVolume = _bellVolume.value
    }

    fun setEndSound(sound: Sound) {
        _endSound.value = sound
        prefs.endSoundName = sound.name
    }

    fun toggleDnd() {
        _dndEnabled.value = !_dndEnabled.value
        prefs.dndEnabled = _dndEnabled.value
    }

    fun addBell(triggerAtMinutes: Int, sound: Sound) {
        _bells.value = (_bells.value + IntervalBell(
            triggerAtMillis = triggerAtMinutes * 60_000L,
            soundResId = sound.resId,
        )).sortedBy { it.triggerAtMillis }
    }

    fun removeBell(index: Int) {
        _bells.value = _bells.value.toMutableList().apply { removeAt(index) }
    }

    fun previewSound(sound: Sound) {
        soundPlayer.play(sound.resId, _bellVolume.value)
    }

    // --- Presets ---

    fun loadPreset(preset: Preset) {
        setDuration(preset.durationMinutes)
        _vibrateOnly.value = preset.vibrateOnly
        prefs.vibrateOnly = preset.vibrateOnly
        setBellVolume(preset.bellVolume)
        setEndSound(Sound.entries.find { it.name == preset.endSoundName } ?: Sound.DEFAULT)
        _dndEnabled.value = preset.dndEnabled
        prefs.dndEnabled = preset.dndEnabled
        // Convert preset bells to interval bells
        _bells.value = preset.bells.mapNotNull { pb ->
            val sound = Sound.entries.find { it.name == pb.soundName } ?: return@mapNotNull null
            IntervalBell(triggerAtMillis = pb.triggerAtMillis, soundResId = sound.resId)
        }
    }

    fun savePreset(name: String) {
        viewModelScope.launch {
            presetRepository.save(
                Preset.fromSetupState(
                    name = name,
                    durationMinutes = _durationMinutes.value,
                    vibrateOnly = _vibrateOnly.value,
                    bellVolume = _bellVolume.value,
                    endSound = _endSound.value,
                    dndEnabled = _dndEnabled.value,
                    intervalBells = _bells.value,
                )
            )
        }
    }

    fun deletePreset(preset: Preset) {
        viewModelScope.launch {
            presetRepository.delete(preset)
        }
    }

    fun setThemeMode(mode: String) {
        prefs.themeMode = mode
    }

    fun startTimer() {
        timerManager.start(
            TimerConfig(
                durationMillis = _durationMinutes.value * 60_000L,
                bells = _bells.value,
                vibrateOnly = _vibrateOnly.value,
                bellVolume = _bellVolume.value,
                endSoundResId = _endSound.value.resId,
                dndEnabled = _dndEnabled.value,
            )
        )
    }
}
