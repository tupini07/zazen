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

    /** Duration in total seconds. Only meaningful when [openEnded] is false. */
    private val _durationSeconds = MutableStateFlow(prefs.durationSeconds)
    val durationSeconds: StateFlow<Int> = _durationSeconds.asStateFlow()

    /**
     * Open-ended sits are kept as a separate flag rather than a 0 duration so
     * that toggling back to Timed restores the user's last chosen duration.
     */
    private val _openEnded = MutableStateFlow(prefs.openEnded)
    val openEnded: StateFlow<Boolean> = _openEnded.asStateFlow()

    private val _repeatEverySeconds = MutableStateFlow(prefs.repeatEverySeconds)
    val repeatEverySeconds: StateFlow<Int> = _repeatEverySeconds.asStateFlow()

    private val _repeatSound = MutableStateFlow(
        Sound.entries.find { it.name == prefs.repeatSoundName } ?: Sound.DEFAULT
    )
    val repeatSound: StateFlow<Sound> = _repeatSound.asStateFlow()

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

    private val _screenAlwaysOn = MutableStateFlow(prefs.screenAlwaysOn)
    val screenAlwaysOn: StateFlow<Boolean> = _screenAlwaysOn.asStateFlow()

    private val _bells = MutableStateFlow<List<IntervalBell>>(emptyList())
    val bells: StateFlow<List<IntervalBell>> = _bells.asStateFlow()

    val presets = presetRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val themeMode: StateFlow<String> = prefs.themeModeFlow

    init {
        soundPlayer.preloadAll()
        // Restore last-used interval bells
        _bells.value = prefs.loadIntervalBells().mapNotNull { (millis, soundName) ->
            val sound = Sound.entries.find { it.name == soundName } ?: return@mapNotNull null
            IntervalBell(triggerAtMillis = millis, soundResId = sound.resId)
        }
    }

    private fun persistBells() {
        prefs.saveIntervalBells(_bells.value.map { bell ->
            val sound = Sound.entries.find { it.resId == bell.soundResId } ?: Sound.DEFAULT
            bell.triggerAtMillis to sound.name
        })
    }

    fun setDurationSeconds(seconds: Int) {
        _durationSeconds.value = seconds.coerceIn(10, 240 * 60)
        prefs.durationSeconds = _durationSeconds.value
    }

    fun setOpenEnded(open: Boolean) {
        _openEnded.value = open
        prefs.openEnded = open
    }

    /** 0 disables repeating bells. */
    fun setRepeatEverySeconds(seconds: Int) {
        _repeatEverySeconds.value = seconds.coerceIn(0, 240 * 60)
        prefs.repeatEverySeconds = _repeatEverySeconds.value
    }

    fun setRepeatSound(sound: Sound) {
        _repeatSound.value = sound
        prefs.repeatSoundName = sound.name
    }

    fun incrementDuration() {
        val curMin = _durationSeconds.value / 60
        // Snap to next 5-min boundary
        val nextMin = if (curMin < 5) 5 else (curMin / 5 + 1) * 5
        setDurationSeconds(nextMin * 60)
    }

    fun decrementDuration() {
        val curMin = _durationSeconds.value / 60
        // Snap down; below 5 min → 1 min
        val targetMin = if (curMin <= 5) 1 else ((curMin - 1) / 5) * 5
        setDurationSeconds(targetMin * 60)
    }

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

    fun toggleScreenAlwaysOn() {
        _screenAlwaysOn.value = !_screenAlwaysOn.value
        prefs.screenAlwaysOn = _screenAlwaysOn.value
    }

    fun addBell(triggerAtMinutes: Int, sound: Sound) {
        _bells.value = (_bells.value + IntervalBell(
            triggerAtMillis = triggerAtMinutes * 60_000L,
            soundResId = sound.resId,
        )).sortedBy { it.triggerAtMillis }
        persistBells()
    }

    fun removeBell(index: Int) {
        _bells.value = _bells.value.toMutableList().apply { removeAt(index) }
        persistBells()
    }

    fun updateBell(index: Int, triggerAtMinutes: Int, sound: Sound) {
        _bells.value = _bells.value.toMutableList().apply {
            this[index] = IntervalBell(
                triggerAtMillis = triggerAtMinutes * 60_000L,
                soundResId = sound.resId,
            )
        }.sortedBy { it.triggerAtMillis }
        persistBells()
    }

    fun previewSound(sound: Sound) {
        soundPlayer.play(sound.resId, _bellVolume.value)
    }

    // --- Presets ---

    fun loadPreset(preset: Preset) {
        setOpenEnded(preset.isOpenEnded)
        if (!preset.isOpenEnded) setDurationSeconds(preset.durationSeconds)
        _vibrateOnly.value = preset.vibrateOnly
        prefs.vibrateOnly = preset.vibrateOnly
        setBellVolume(preset.bellVolume)
        setEndSound(Sound.entries.find { it.name == preset.endSoundName } ?: Sound.DEFAULT)
        setRepeatEverySeconds(preset.repeatEverySeconds)
        setRepeatSound(Sound.entries.find { it.name == preset.repeatSoundName } ?: Sound.DEFAULT)
        _dndEnabled.value = preset.dndEnabled
        prefs.dndEnabled = preset.dndEnabled
        // Convert preset bells to interval bells
        _bells.value = preset.bells.mapNotNull { pb ->
            val sound = Sound.entries.find { it.name == pb.soundName } ?: return@mapNotNull null
            IntervalBell(triggerAtMillis = pb.triggerAtMillis, soundResId = sound.resId)
        }
        persistBells()
    }

    fun savePreset(name: String) {
        viewModelScope.launch {
            presetRepository.save(
                Preset.fromSetupState(
                    name = name,
                    durationSeconds = if (_openEnded.value) 0 else _durationSeconds.value,
                    vibrateOnly = _vibrateOnly.value,
                    bellVolume = _bellVolume.value,
                    endSound = _endSound.value,
                    dndEnabled = _dndEnabled.value,
                    intervalBells = _bells.value,
                    repeatEverySeconds = _repeatEverySeconds.value,
                    repeatSound = _repeatSound.value,
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
                durationMillis = if (_openEnded.value) 0L else _durationSeconds.value * 1_000L,
                bells = _bells.value,
                vibrateOnly = _vibrateOnly.value,
                bellVolume = _bellVolume.value,
                endSoundResId = _endSound.value.resId,
                dndEnabled = _dndEnabled.value,
                repeatEveryMillis = _repeatEverySeconds.value * 1_000L,
                repeatSoundResId = _repeatSound.value.resId,
            )
        )
    }
}
