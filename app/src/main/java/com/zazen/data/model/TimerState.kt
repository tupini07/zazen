package com.zazen.data.model

sealed interface TimerState {
    data object Idle : TimerState

    data class Running(
        val remainingMillis: Long,
        val totalMillis: Long,
    ) : TimerState

    data class Paused(
        val remainingMillis: Long,
        val totalMillis: Long,
    ) : TimerState

    data class Finished(
        val sessionId: Long,
        val completed: Boolean,
        val elapsedMillis: Long = 0,
    ) : TimerState
}
