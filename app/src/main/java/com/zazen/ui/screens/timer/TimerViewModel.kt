package com.zazen.ui.screens.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zazen.data.model.TimerConfig
import com.zazen.data.model.TimerState
import com.zazen.data.repository.SessionRepository
import com.zazen.service.TimerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val timerManager: TimerManager,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val state: StateFlow<TimerState> = timerManager.state
    val config: TimerConfig? get() = timerManager.config

    fun pause() = timerManager.pause()
    fun resume() = timerManager.resume()
    fun stop() = timerManager.stop()
    fun dismiss() = timerManager.reset()

    fun discardAndDismiss(sessionId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                if (sessionId > 0) {
                    sessionRepository.deleteSession(sessionId)
                }
            } catch (_: Exception) { /* best-effort delete */ }
            dismiss()
            onDone()
        }
    }

    fun saveNotesAndDismiss(sessionId: Long, notes: String, onDone: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            try {
                val trimmed = notes.trim()
                if (trimmed.isNotEmpty() && sessionId > 0) {
                    sessionRepository.updateNotes(sessionId, trimmed)
                }
                dismiss()
                onDone()
            } catch (_: Exception) {
                onError()
            }
        }
    }
}
