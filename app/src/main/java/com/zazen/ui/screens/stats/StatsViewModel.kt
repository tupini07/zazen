package com.zazen.ui.screens.stats

import com.zazen.data.repository.SessionRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zazen.data.model.MeditationSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val sessions = sessionRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalMillis = sessionRepository.getTotalMeditatedMillis()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val sessionCount = sessionRepository.getSessionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun deleteSession(session: MeditationSession) {
        viewModelScope.launch { sessionRepository.deleteSession(session.id) }
    }

    fun updateNotes(sessionId: Long, notes: String) {
        viewModelScope.launch { sessionRepository.updateNotes(sessionId, notes) }
    }
}
