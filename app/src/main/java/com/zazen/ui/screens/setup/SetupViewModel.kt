package com.zazen.ui.screens.setup

import com.zazen.data.model.IntervalBell
import com.zazen.data.model.Sound
import com.zazen.data.model.TimerConfig
import com.zazen.service.TimerManager
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val timerManager: TimerManager,
) : ViewModel() {

    private val _durationMinutes = MutableStateFlow(25)
    val durationMinutes: StateFlow<Int> = _durationMinutes.asStateFlow()

    private val _vibrateOnly = MutableStateFlow(false)
    val vibrateOnly: StateFlow<Boolean> = _vibrateOnly.asStateFlow()

    private val _bells = MutableStateFlow<List<IntervalBell>>(emptyList())
    val bells: StateFlow<List<IntervalBell>> = _bells.asStateFlow()

    fun setDuration(minutes: Int) {
        _durationMinutes.value = minutes.coerceIn(1, 240)
    }

    fun incrementDuration() = setDuration(_durationMinutes.value + 5)
    fun decrementDuration() = setDuration(_durationMinutes.value - 5)

    fun toggleVibrateOnly() {
        _vibrateOnly.value = !_vibrateOnly.value
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

    fun startTimer() {
        timerManager.start(
            TimerConfig(
                durationMillis = _durationMinutes.value * 60_000L,
                bells = _bells.value,
                vibrateOnly = _vibrateOnly.value,
            )
        )
    }
}
