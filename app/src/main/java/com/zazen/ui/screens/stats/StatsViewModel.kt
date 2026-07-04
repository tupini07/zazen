package com.zazen.ui.screens.stats

import android.net.Uri
import com.zazen.data.repository.BackupRepository
import com.zazen.data.repository.SessionRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zazen.data.model.MeditationSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    val sessions = sessionRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalMillis = sessionRepository.getTotalMeditatedMillis()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val sessionCount = sessionRepository.getSessionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    fun deleteSession(session: MeditationSession) {
        viewModelScope.launch { sessionRepository.deleteSession(session.id) }
    }

    fun updateNotes(sessionId: Long, notes: String) {
        viewModelScope.launch { sessionRepository.updateNotes(sessionId, notes) }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                backupRepository.exportToUri(uri)
                _messages.emit("Backup saved")
            } catch (e: Exception) {
                _messages.emit("Backup failed: ${e.message}")
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val result = backupRepository.importFromUri(uri)
                _messages.emit(
                    "Restored ${result.presetCount} preset(s) and ${result.sessionCount} session(s)",
                )
            } catch (e: Exception) {
                _messages.emit("Restore failed: ${e.message}")
            }
        }
    }
}
