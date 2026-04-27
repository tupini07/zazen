package com.zazen.ui.screens.timer

import androidx.lifecycle.ViewModel
import com.zazen.data.model.TimerConfig
import com.zazen.data.model.TimerState
import com.zazen.service.TimerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val timerManager: TimerManager,
) : ViewModel() {

    val state: StateFlow<TimerState> = timerManager.state
    val config: TimerConfig? get() = timerManager.config

    fun pause() = timerManager.pause()
    fun resume() = timerManager.resume()
    fun stop() = timerManager.stop()
    fun dismiss() = timerManager.reset()
}
